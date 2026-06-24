"""
Export a merged HuggingFace checkpoint to a format suitable for on-device inference.

Tries ExecuTorch .pte first (via LLMEdgeManager), then falls back to GGUF
conversion via llama.cpp (widely supported on Android via llama.cpp JNI).

Run this on the Vast.ai instance after ml/train.py completes.

Usage:
    python ml/export_to_executorch.py \
        --model_dir ml/output/lora_merged \
        --out ml/output/idleharvest_model.pte \
        --quantize int8
"""

import argparse
import json
import os
import subprocess
import sys
from contextlib import contextmanager
from pathlib import Path


@contextmanager
def strip_quantization_metadata(model_dir: str):
    config_path = Path(model_dir) / "config.json"
    backup = None

    if config_path.exists():
        config = json.loads(config_path.read_text(encoding="utf-8"))
        if "quantization_config" in config:
            backup = config_path.read_text(encoding="utf-8")
            config.pop("quantization_config", None)
            config_path.write_text(
                json.dumps(config, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )

    try:
        yield
    finally:
        if backup is not None:
            config_path.write_text(backup, encoding="utf-8")


def export_executorch(model_dir: str, out: str, max_seq_len: int, quantize: str):
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer

    with strip_quantization_metadata(model_dir):
        print(f"Loading {model_dir} for ExecuTorch export…")
        model = AutoModelForCausalLM.from_pretrained(model_dir, torch_dtype=torch.float32)
        model.eval()
        tokenizer = AutoTokenizer.from_pretrained(model_dir)

        from executorch.extension.llm.export import LLMEdgeManager

        dummy_ids = torch.zeros((1, 16), dtype=torch.long)
        manager = LLMEdgeManager(
            model=model,
            modelname="idleharvest",
            max_seq_len=max_seq_len,
            use_kv_cache=False,
            example_inputs=(dummy_ids,),
            dtype=torch.float32,
            enable_dynamic_shape=True,
        )

        if quantize == "int8":
            from executorch.extension.llm.export.builder import DType
            manager = manager.to_dtype(DType.fp16)

        os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
        manager.export().to_edge().to_executorch()
        manager.save_to_pte(out)

    size_mb = os.path.getsize(out) / (1024 * 1024)
    print(f"ExecuTorch export complete: {out} ({size_mb:.1f} MB)")


def export_gguf(model_dir: str, out_dir: str, quantize: str):
    """Convert to GGUF using llama.cpp convert scripts."""
    gguf_path = os.path.join(out_dir, "idleharvest_model.gguf")

    with strip_quantization_metadata(model_dir):
        if not os.path.exists("/opt/llama.cpp"):
            print("Cloning llama.cpp for GGUF conversion…")
            subprocess.run(["git", "clone", "--depth=1",
                            "https://github.com/ggerganov/llama.cpp.git",
                            "/opt/llama.cpp"], check=True)
            subprocess.run(["pip", "install", "-q", "gguf", "sentencepiece"],
                           check=True)

        print("Converting to GGUF (f16)…")
        subprocess.run([
            sys.executable,
            "/opt/llama.cpp/convert_hf_to_gguf.py",
            model_dir,
            "--outfile", gguf_path,
            "--outtype", "f16",
        ], check=True)

        quant_map = {"int8": "Q8_0", "int4": "Q4_K_M", "none": "F16"}
        qtype = quant_map.get(quantize, "Q8_0")
        if qtype != "F16":
            quantized_path = gguf_path.replace(".gguf", f"_{qtype}.gguf")
            print(f"Quantizing to {qtype}…")
            subprocess.run([
                "/opt/llama.cpp/build/bin/llama-quantize",
                gguf_path, quantized_path, qtype,
            ], check=True)
            os.remove(gguf_path)
            gguf_path = quantized_path

    size_mb = os.path.getsize(gguf_path) / (1024 * 1024)
    print(f"GGUF export complete: {gguf_path} ({size_mb:.1f} MB)")
    print("Copy into the Android app and use via llama.cpp JNI bindings.")
    return gguf_path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_dir", default="ml/output/lora_merged")
    parser.add_argument("--out", default="ml/output/idleharvest_model.pte")
    parser.add_argument("--quantize", choices=["none", "int8", "int4"], default="int8")
    parser.add_argument("--max_seq_len", type=int, default=512)
    parser.add_argument("--format", choices=["executorch", "gguf", "auto"],
                        default="auto")
    args = parser.parse_args()

    out_dir = os.path.dirname(args.out) or "."
    os.makedirs(out_dir, exist_ok=True)

    if args.format in ("executorch", "auto"):
        try:
            export_executorch(args.model_dir, args.out, args.max_seq_len, args.quantize)
            return
        except Exception as exc:
            print(f"ExecuTorch export failed ({exc}), falling back to GGUF…")
            if args.format == "executorch":
                sys.exit(1)

    export_gguf(args.model_dir, out_dir, args.quantize)


if __name__ == "__main__":
    main()
