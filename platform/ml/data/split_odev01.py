"""0421-8INTENT Faz B · odev01 raw (8-intent) split.

`prompts_v1.jsonl` (8 intent / 5 persona / 5 funnel) intent × sector kırılımında
stratifiye %80/%20 train/test'e ayrılır (rastgele çekirdek 42) — mapped split'lerle
(`split/train_prompts_v1.mapped.jsonl` vb.) aynı yöntem.

Çıktı:
  data/odev01/split/train_prompts_v1.jsonl
  data/odev01/split/test_prompts_v1.jsonl
"""
from __future__ import annotations

import argparse
import json
import os
import random
from collections import defaultdict

SEED = 42
TEST_RATIO = 0.20


def sharded_split(items: list[dict], ratio: float, stratum_key, seed: int) -> tuple[list, list]:
    """stratum'a göre grupla, her grupta oranlı ayır, kategorik dengeli."""
    rng = random.Random(seed)
    strata = defaultdict(list)
    for it in items:
        strata[stratum_key(it)].append(it)
    train, test = [], []
    for group in strata.values():
        group = list(group)
        rng.shuffle(group)
        n_test = max(1, round(len(group) * ratio))
        test.extend(group[:n_test])
        train.extend(group[n_test:])
    return train, test


def main() -> None:
    parser = argparse.ArgumentParser(description="odev01 raw veri split'i (0421-8INTENT Faz B)")
    parser.add_argument("--data", default=os.path.join(os.path.dirname(__file__), "odev01", "prompts_v1.jsonl"))
    parser.add_argument("--outdir", default=os.path.join(os.path.dirname(__file__), "odev01", "split"))
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()

    records = [json.loads(line) for line in open(args.data, encoding="utf-8") if line.strip()]
    train, test = sharded_split(records, TEST_RATIO, lambda r: f"{r.get('intent')}|{r.get('sector')}", seed=args.seed)

    os.makedirs(args.outdir, exist_ok=True)
    for name, rows in (("train_prompts_v1.jsonl", train), ("test_prompts_v1.jsonl", test)):
        with open(os.path.join(args.outdir, name), "w", encoding="utf-8") as fh:
            for r in rows:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
        print(f"{name}: {len(rows)}")

    print(f"\nÖzet: train %{100*len(train)/len(records):.0f} / test %{100*len(test)/len(records):.0f}")


if __name__ == "__main__":
    main()
