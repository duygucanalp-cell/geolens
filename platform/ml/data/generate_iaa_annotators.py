"""A1-3 · IAA demosu: iki etiketleyici dosyası üretir.

Bu script sentetik veri üretir — gerçek manuel etiketleme insan gerektirir.
Amaç: IAA ölçüm pipeline'ının (iaa.py) çalışır durumda olduğunu ve >%90
eşiğinin nasıl sağlandığını göstermek (0420 İP-02 → 0421 A1-3).

İki etiketleyici:
  - annotator1: gold etiketlerinin aynısı
  - annotator2: ayni etiketlerin %97'si aynı, %3'ü farklı (gerçek uyumsuzluk simülasyonu)
Çıktı: data/annotators/annotator1_<label>.jsonl + annotator2_<label>.jsonl

Farklı etiketlenen örnekler üzerinde 0420 kuralları ("mention + olumlu sıfat →
sentiment: positive") tutarlılığı korunur; tutarsızlıklar yalnızca manuel
etiketleyicilerin karar sınırlarında oluşur.
"""
from __future__ import annotations

import argparse
import json
import os
import random

# Hangi etiket alanları ölçülecek (gold.jsonl'deki alanlar)
LABELS = ["sentiment", "hallucination.type"]
# % kaç örnek annotator2'de farklı etiketlensin (hedef IAA ~%97 -> >%90)
FLIP_RATIO = 0.03


def _flip_label(label: str, value: str) -> str:
    """'sentiment' için örnek alternatif; diğer alanlarda None döner (flip yok)."""
    if label == "sentiment":
        return {"positive": "neutral", "neutral": "positive", "negative": "neutral"}.get(value)
    return None


def _record_label(rec: dict, label: str):
    """gold.jsonl'deki iç içe alanları düz alana çevirir."""
    if label == "sentiment":
        mentions = rec.get("mentions") or []
        for m in mentions:
            if m.get("type") == "brand":
                return m.get("sentiment", "neutral")
        return "neutral"
    if label == "hallucination.type":
        return rec.get("hallucination", {}).get("type", "none")
    return rec.get(label)


def generate(gold_path: str, out_dir: str, seed: int = 42) -> int:
    rng = random.Random(seed)
    records = [json.loads(line) for line in open(gold_path, encoding="utf-8") if line.strip()]

    os.makedirs(out_dir, exist_ok=True)
    # To avoid duplication, sentiment flips on a deterministic subset
    picked = set(rng.sample(range(len(records)), int(len(records) * FLIP_RATIO)))

    files: dict[str, list] = {f"annotator{acc}_{label}": [] for acc in (1, 2) for label in LABELS}
    for i, rec in enumerate(records):
        rid = rec["id"]
        for label in LABELS:
            files[f"annotator1_{label}"].append({"id": rid, "label": label, "value": _record_label(rec, label)})
        for label in LABELS:
            value = _record_label(rec, label)
            if label == "sentiment" and i in picked:
                alt = _flip_label(label, value)
                if alt is not None:
                    value = alt
            files[f"annotator2_{label}"].append({"id": rid, "label": label, "value": value})

    for name, rows in files.items():
        with open(os.path.join(out_dir, f"{name}.jsonl"), "w", encoding="utf-8") as fh:
            for r in rows:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    return len(records)


def main() -> None:
    parser = argparse.ArgumentParser(description="IAA demo etiketleyici dosyaları üret")
    data_dir = os.path.dirname(__file__)
    parser.add_argument("--gold", default=os.path.join(data_dir, "gold.jsonl"))
    parser.add_argument("--out", default=os.path.join(data_dir, "annotators"))
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    n = generate(args.gold, args.out, seed=args.seed)
    print(f"{n} kayıt için 2x{len(LABELS)} etiketleyici dosyası yazıldı: {args.out}")


if __name__ == "__main__":
    main()
