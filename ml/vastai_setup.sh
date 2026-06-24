#!/usr/bin/env bash
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# IdleHarvest â€” Vast.ai instance bootstrap script
#
# Run this once on a fresh Vast.ai GPU instance (CUDA 12.x, Python 3.11+).
# Uses a virtual environment to avoid conda base conflicts.
# Base model: TinyLlama/TinyLlama-1.1B-Chat-v1.0 (fully open, no HF login).
#
# Recommended instance: RTX 4090 / A100 (24â€“80 GB VRAM), ~$0.50â€“$1.20/hr
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
set -euo pipefail

echo "=== [1/7] System deps ==="
apt-get update -q && apt-get install -y -q git curl unzip

echo "=== [2/7] Create Python virtual environment ==="
python3 -m venv ~/idleharvest_env
source ~/idleharvest_env/bin/activate

echo "=== [3/7] Install PyTorch (CUDA 12.4) ==="
pip install -q torch==2.5.1 torchvision==0.20.1 \
  --index-url https://download.pytorch.org/whl/cu124

echo "=== [4/7] Install training dependencies ==="
pip install -q \
  "transformers==4.46.3" \
  "peft==0.14.0" \
  "trl==0.12.2" \
  "datasets==3.2.0" \
  "accelerate==1.2.1" \
  "bitsandbytes==0.45.0" \
  "scipy==1.14.1"

echo "=== [5/7] Clone IdleHarvest repo ==="
if [ ! -d "IdleHarvest" ]; then
  git clone https://github.com/ma-za-kpe/IdleHarvest.git
fi
cd IdleHarvest

echo "=== [6/7] Generate synthetic dataset (10k rows) ==="
python3 ml/generate_dataset.py --rows 10000 --out ml/data

echo "=== [7/7] Fine-tune model ==="
nohup python3 ml/train.py --data ml/data/device_usage.jsonl --base_model local-scratch --output_dir ml/output/lora_merged --epochs 3 --batch_size 8 > ml/output/train.log 2>&1 &
echo "Training started (PID $!). Monitor with: tail -f ml/output/train.log"

echo ""
echo "When training completes, export to ExecuTorch:"
echo "  python3 ml/export_to_executorch.py --model_dir ml/output/lora_merged --out ml/output/idleharvest_model.pte --quantize int8"
echo ""
echo "Then download:"
echo "  scp -P <PORT> root@<IP>:~/IdleHarvest/ml/output/idleharvest_model.pte ."
echo "Copy into app:"
echo "  cp idleharvest_model.pte shared/src/commonMain/composeResources/files/"
