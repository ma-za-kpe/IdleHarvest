"""
Synthetic dataset generator for IdleHarvest on-device inference model.

Generates realistic mobile device usage data for African/emerging-market
prepaid users. Outputs CSV + JSONL for QLoRA fine-tuning.

Usage:
    python ml/generate_dataset.py --rows 10000 --out ml/data/
"""

import argparse
import csv
import json
import math
import random
import os
from datetime import datetime, timedelta

random.seed(42)  # for reproducibility

NETWORK_TYPES = ["2G", "3G", "4G", "WiFi", "None"]
LOCATION_TYPES = ["home", "work", "transit", "rural", "urban"]
USER_PROFILES = ["heavy", "light", "traveler", "saver"]

PROFILE_CONFIG = {
    "heavy":    {"data_mean": 150, "screen_mean": 240, "airtime_mean": 800,  "top_up_prob": 0.15},
    "light":    {"data_mean": 20,  "screen_mean": 60,  "airtime_mean": 200,  "top_up_prob": 0.05},
    "traveler": {"data_mean": 80,  "screen_mean": 120, "airtime_mean": 1200, "top_up_prob": 0.12},
    "saver":    {"data_mean": 40,  "screen_mean": 90,  "airtime_mean": 500,  "top_up_prob": 0.08},
}


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def generate_row(user_id: str, ts: datetime, profile: str, prev: dict) -> dict:
    cfg = PROFILE_CONFIG[profile]
    hour = ts.hour

    # Battery drains during the day, charges overnight
    if 0 <= hour < 6:
        battery_delta = random.gauss(3, 1)   # charging
    elif 6 <= hour < 22:
        battery_delta = random.gauss(-4, 2)  # discharging
    else:
        battery_delta = random.gauss(1, 1)

    battery = clamp((prev.get("battery_level", 80) + battery_delta), 5, 100)

    # Screen usage peaks in morning and evening
    screen_factor = 1.5 if (7 <= hour <= 9 or 18 <= hour <= 23) else 0.5
    screen_on = clamp(random.gauss(cfg["screen_mean"] * screen_factor / 24, 5), 0, 60)

    # Data usage follows screen usage pattern
    data_used = clamp(random.gauss(cfg["data_mean"] / 24 * screen_factor, 5), 0, 500)

    # Airtime balance decreases with calls; occasional top-ups
    airtime = prev.get("airtime_balance", cfg["airtime_mean"])
    airtime -= random.uniform(0, 30) * (1 if hour >= 7 else 0)
    if random.random() < cfg["top_up_prob"]:
        airtime += random.choice([100, 200, 500, 1000])
    airtime = clamp(airtime, 0, 5000)

    # Network type varies by location and time
    if hour < 6:
        network = random.choices(["WiFi", "None"], weights=[0.7, 0.3])[0]
    else:
        network = random.choices(NETWORK_TYPES, weights=[0.1, 0.2, 0.4, 0.2, 0.1])[0]

    location = random.choices(LOCATION_TYPES, weights=[0.4, 0.3, 0.1, 0.1, 0.1])[0]

    # Temperature rises with CPU usage
    cpu_usage = clamp(random.gauss(20 + screen_on * 0.3, 10), 0, 100)
    temperature = clamp(random.gauss(30 + cpu_usage * 0.15, 2), 25, 45)

    # Derive recommendation label for training signal
    days_until_expiry = random.randint(0, 30)
    should_sell_airtime = (airtime > 100 and days_until_expiry <= 3 and hour >= 8)
    should_share_bandwidth = (battery > 40 and network in ("4G", "WiFi") and cpu_usage < 60)

    return {
        "timestamp": ts.isoformat(),
        "user_id": user_id,
        "user_profile": profile,
        "battery_level": round(battery, 1),
        "screen_on_time_min": round(screen_on, 1),
        "data_used_mb": round(data_used, 2),
        "airtime_balance": round(airtime, 1),
        "days_until_airtime_expiry": days_until_expiry,
        "apps_running": random.randint(1, 15),
        "cpu_usage_pct": round(cpu_usage, 1),
        "network_type": network,
        "location_type": location,
        "temperature_c": round(temperature, 1),
        # Training labels
        "label_sell_airtime": int(should_sell_airtime),
        "label_share_bandwidth": int(should_share_bandwidth),
    }


def generate_dataset(total_rows: int, out_dir: str):
    os.makedirs(out_dir, exist_ok=True)
    csv_path = os.path.join(out_dir, "device_usage.csv")
    jsonl_path = os.path.join(out_dir, "device_usage.jsonl")

    rows = []
    users_per_profile = total_rows // (len(USER_PROFILES) * 24 * 7)
    users_per_profile = max(1, users_per_profile)

    uid = 0
    for profile in USER_PROFILES:
        for _ in range(users_per_profile):
            user_id = f"user_{uid:04d}_{profile}"
            uid += 1
            start = datetime(2025, 1, 1) + timedelta(days=random.randint(0, 180))
            prev = {}
            for hour_offset in range(7 * 24):
                ts = start + timedelta(hours=hour_offset)
                row = generate_row(user_id, ts, profile, prev)
                rows.append(row)
                prev = row

    random.shuffle(rows)
    rows = rows[:total_rows]

    # Write CSV
    with open(csv_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)

    # Write JSONL (instruction-tuning format for QLoRA)
    with open(jsonl_path, "w") as f:
        for row in rows:
            instruction = (
                f"Device state: battery={row['battery_level']}%, "
                f"airtime={row['airtime_balance']} ({row['days_until_airtime_expiry']}d left), "
                f"network={row['network_type']}, data={row['data_used_mb']}MB, "
                f"cpu={row['cpu_usage_pct']}%, temp={row['temperature_c']}C. "
                f"Should the agent sell airtime or share bandwidth?"
            )
            response_parts = []
            if row["label_sell_airtime"]:
                response_parts.append("SELL_AIRTIME: airtime is expiring soon and balance is sufficient")
            if row["label_share_bandwidth"]:
                response_parts.append("SHARE_BANDWIDTH: battery and network conditions are good")
            if not response_parts:
                response_parts.append("WAIT: conditions not optimal for agent actions")
            response = "; ".join(response_parts)

            f.write(json.dumps({"instruction": instruction, "output": response}) + "\n")

    print(f"Generated {len(rows)} rows → {csv_path}")
    print(f"JSONL (instruction tuning) → {jsonl_path}")
    return csv_path, jsonl_path


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--rows", type=int, default=10000)
    parser.add_argument("--out", type=str, default="ml/data")
    args = parser.parse_args()
    generate_dataset(args.rows, args.out)
