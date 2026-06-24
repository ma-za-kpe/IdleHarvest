"""
Training script for the IdleHarvest on-device inference model.

If Hugging Face access is available, this keeps the original QLoRA path.
If the base model cannot be downloaded, it falls back to an offline scratch
GPT-2 style model so a real trained checkpoint is still produced on Vast.ai.
"""

import argparse
import json
import os
from typing import Iterable


SCRATCH_BASE_MODELS = {"scratch", "offline", "local-scratch"}


def load_records(data_path: str) -> list[dict]:
    with open(data_path, encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def format_prompt(record: dict) -> str:
    return (
        f"### Instruction:\n{record['instruction']}\n\n"
        f"### Response:\n{record['output']}"
    )


def build_scratch_tokenizer(texts: Iterable[str], max_seq_len: int):
    from tokenizers import ByteLevelBPETokenizer
    from transformers import PreTrainedTokenizerFast

    tokenizer = ByteLevelBPETokenizer()
    tokenizer.train_from_iterator(
        texts,
        vocab_size=2048,
        min_frequency=2,
        special_tokens=["<pad>", "<bos>", "<eos>", "<unk>"],
    )
    fast_tokenizer = PreTrainedTokenizerFast(
        tokenizer_object=tokenizer._tokenizer,
        pad_token="<pad>",
        bos_token="<bos>",
        eos_token="<eos>",
        unk_token="<unk>",
    )
    fast_tokenizer.model_max_length = max_seq_len
    return fast_tokenizer


def train_offline_scratch_model(records: list[dict], args) -> None:
    import torch
    from datasets import Dataset
    from transformers import (
        DataCollatorForLanguageModeling,
        GPT2Config,
        GPT2LMHeadModel,
        Trainer,
        TrainingArguments,
    )

    print("Using offline scratch model path.")
    texts = [format_prompt(record) for record in records]
    tokenizer = build_scratch_tokenizer(texts, args.max_seq_len)
    dataset = Dataset.from_list([{"text": text} for text in texts])

    def tokenize_batch(batch):
        return tokenizer(
            batch["text"],
            truncation=True,
            max_length=args.max_seq_len,
        )

    tokenized = dataset.map(tokenize_batch, batched=True, remove_columns=["text"])
    tokenized.set_format(type="torch")

    config = GPT2Config(
        vocab_size=tokenizer.vocab_size,
        n_positions=args.max_seq_len,
        n_ctx=args.max_seq_len,
        n_embd=128,
        n_layer=2,
        n_head=4,
        bos_token_id=tokenizer.bos_token_id,
        eos_token_id=tokenizer.eos_token_id,
        pad_token_id=tokenizer.pad_token_id,
    )
    model = GPT2LMHeadModel(config)
    model.config.torch_dtype = "float16" if torch.cuda.is_available() else "float32"

    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=1,
        learning_rate=5e-4,
        warmup_ratio=0.03,
        lr_scheduler_type="cosine",
        logging_steps=10,
        save_strategy="no",
        report_to="none",
        fp16=torch.cuda.is_available(),
        bf16=False,
        optim="adamw_torch",
        remove_unused_columns=False,
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized,
        data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False),
    )

    print("Starting offline scratch training...")
    trainer.train()

    os.makedirs(args.output_dir, exist_ok=True)
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print("Training complete.")


def train_lora_model(records: list[dict], args) -> None:
    import torch
    from datasets import Dataset
    from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
    from transformers import (
        AutoModelForCausalLM,
        AutoTokenizer,
        BitsAndBytesConfig,
        TrainingArguments,
    )
    from trl import SFTTrainer

    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.float16,
        bnb_4bit_use_double_quant=True,
    )

    print(f"Loading base model: {args.base_model}")
    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    tokenizer.pad_token = tokenizer.eos_token

    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        quantization_config=bnb_config,
        device_map="auto",
    )
    model = prepare_model_for_kbit_training(model)

    lora_config = LoraConfig(
        r=args.lora_r,
        lora_alpha=args.lora_alpha,
        target_modules=[
            "q_proj",
            "k_proj",
            "v_proj",
            "o_proj",
            "gate_proj",
            "up_proj",
            "down_proj",
        ],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    dataset = Dataset.from_list([{"text": format_prompt(record)} for record in records])

    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        fp16=True,
        logging_steps=50,
        save_steps=200,
        warmup_ratio=0.03,
        lr_scheduler_type="cosine",
        report_to="none",
        optim="paged_adamw_8bit",
    )

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset,
        dataset_text_field="text",
        max_seq_length=args.max_seq_len,
        args=training_args,
    )

    print("Starting QLoRA fine-tuning...")
    trainer.train()

    print(f"Merging LoRA weights -> {args.output_dir}")
    os.makedirs(args.output_dir, exist_ok=True)
    merged = model.merge_and_unload()
    if hasattr(merged.config, "quantization_config"):
        delattr(merged.config, "quantization_config")
    merged.config.torch_dtype = "float16"
    merged.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print("Training complete.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="ml/data/device_usage.jsonl")
    parser.add_argument("--base_model", default="TinyLlama/TinyLlama-1.1B-Chat-v1.0")
    parser.add_argument("--output_dir", default="ml/output/lora_merged")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch_size", type=int, default=4)
    parser.add_argument("--max_seq_len", type=int, default=512)
    parser.add_argument("--lora_r", type=int, default=16)
    parser.add_argument("--lora_alpha", type=int, default=32)
    args = parser.parse_args()

    records = load_records(args.data)

    if args.base_model in SCRATCH_BASE_MODELS:
        train_offline_scratch_model(records, args)
        return

    try:
        train_lora_model(records, args)
    except Exception as exc:
        print(f"HF fine-tuning failed ({exc}); falling back to offline scratch model.")
        train_offline_scratch_model(records, args)


if __name__ == "__main__":
    main()
