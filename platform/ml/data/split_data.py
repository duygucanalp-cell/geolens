"""A1-4 · Train/test veri bölmesi.

Gold + prompt havuzunu %80 oranında alanla (sector/lang) dengeli biçimde
train/test'e ayırır. Aynı prompt_id'nin gold kopyası ve promptu aynı tarafta
kalır (veri sızıntısını önler).

Çıktı:
  data/split/train_prompts.jsonl, test_prompts.jsonl
  data/split/train_gold.jsonl,    test_gold.jsonl
"""
from __future__ import annotations

import argparse
import json
import os
from collections import defaultdict

SEED = 42
TEST_RATIO = 0.20


def sharded_split(items: list[dict], ratio: float, stratum_key, seed: int) -> tuple[list, list]:
    """stratum'a göre grupla, her grupta oranlı ayır, kategorik dengeli."""
    import random

    rng = random.Random(seed)
    strata = defaultdict(list)
    for it in items:
        strata[stratum_key(it)].append(it)
    train, test = [], []
    for key, group in strata.items():
        group = list(group)
        rng.shuffle(group)
        n_test = max(1, round(len(group) * ratio))
        test.extend(group[:n_test])
        train.extend(group[n_test:])
    return train, test


def main() -> None:
    parser = argparse.ArgumentParser(description="Train/test ayrımı (A1-4)")
    data_dir = os.path.dirname(__file__)
    parser.add_argument("--data", default=data_dir)
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()

    prompts = [json.loads(line) for line in open(os.path.join(args.data, "prompts.jsonl"), encoding="utf-8") if line.strip()]
    gold = [json.loads(line) for line in open(os.path.join(args.data, "gold.jsonl"), encoding="utf-8") if line.strip()]

    # prompt'ları böl; gold kayıtları prompt_id üzerinden aynı tarafa taşı.
    train_p, test_p = sharded_split(prompts, TEST_RATIO, lambda p: f"{p['lang']}|{p['sector']}", seed=args.seed)
    train_ids = {p["id"] for p in train_p}
    test_ids = {p["id"] for p in test_p}

    train_g, test_g = [], []
    for g in gold:
        if g["prompt_id"] in train_ids:
            train_g.append(g)
        elif g["prompt_id"] in test_ids:
            test_g.append(g)

    split_train = os.path.join(args.data, "train")
    split_test = os.path.join(args.data, "test")
    os.makedirs(split_train, exist_ok=True)
    os.makedirs(split_test, exist_ok=True)

    outputs = [
        (os.path.join(split_train, "prompts.jsonl"), train_p),
        (os.path.join(split_train, "gold.jsonl"), train_g),
        (os.path.join(split_test, "prompts.jsonl"), test_p),
        (os.path.join(split_test, "gold.jsonl"), test_g),
    ]
    for path, rows in outputs:
        with open(path, "w", encoding="utf-8") as fh:
            for r in rows:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
        name = os.path.relpath(path, args.data)
        print(f"{name}: {len(rows)}")

    print(f"\nÖzet: prompt train %{100*len(train_p)/(len(train_p)+len(test_p)):.0f} / test %{100*len(test_p)/(len(train_p)+len(test_p)):.0f}")
    print(f"      gold  train %{100*len(train_g)/(len(train_g)+len(test_g)):.0f} / test %{100*len(test_g)/(len(train_g)+len(test_g)):.0f}")


if __name__ == "__main__":
    main()
