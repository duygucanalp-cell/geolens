"""A1-2 · Etiketlenen kayıt doğrulayıcı (0421).

export_unlabeled.py ile üretilen etiketlenmemiş şablonları dolduran etiketleyicinin
çıktısını GoldRecord şemasına karşı doğrular:

  - zorunlu alanlar (id, lang, prompt, response_text, engine) dolu mu?
  - mentions[].type ∈ {brand, product, competitor}; sentiment ∈ {positive, neutral, negative}
  - citations[].type ∈ {direct, attribution, directional}
  - hallucination.type ∈ {none, T1..T7, contradiction, fabrication, outdated}
  - recommendation boolean mı?

Kullanım:
  python data/validate_labeled.py real/annotator1.jsonl
  python data/validate_labeled.py --strict real/annotator1.jsonl   # hatada exit 1

CI entegrasyonu: etiketlenen dosyalar commit edilmeden önce bu doğrulayıcıdan
geçmelidir (IAA >%90 eşiği ayrıca iaa.py ile ölçülür).
"""
from __future__ import annotations

import argparse
import json
import sys

MENTION_TYPES = {"brand", "product", "competitor"}
SENTIMENTS = {"positive", "neutral", "negative"}
CITATION_TYPES = {"direct", "attribution", "directional"}
ENTITY_TYPES = {"brand", "product", "competitor", "technology", "organization", "location", "person", "event", "date", "money", "percent"}
HALLUCINATION_TYPES = {"none", "T1", "T2", "T3", "T4", "T5", "T6", "T7", "contradiction", "fabrication", "outdated"}

REQUIRED = ["id", "lang", "prompt", "response_text", "engine", "mentions", "citations", "entities", "hallucination"]


def validate_record(rec: dict, errors: list[str], idx: int) -> None:
    rid = rec.get("id", f"#{idx}")
    for field in REQUIRED:
        if field not in rec:
            errors.append(f"{rid}: '{field}' alanı eksik")
            continue
    if rec.get("lang") not in ("tr", "en"):
        errors.append(f"{rid}: lang 'tr'/'en' olmalı (gerçek: {rec.get('lang')!r})")

    for m in rec.get("mentions") or []:
        if not isinstance(m, dict) or not m.get("text"):
            errors.append(f"{rid}: mention 'text' boş olamaz")
        if m.get("type") not in MENTION_TYPES:
            errors.append(f"{rid}: mention type geçersiz: {m.get('type')!r}")
        if m.get("sentiment") not in SENTIMENTS:
            errors.append(f"{rid}: mention sentiment geçersiz: {m.get('sentiment')!r}")

    for c in rec.get("citations") or []:
        if not isinstance(c, dict) or not c.get("url", "").startswith(("http://", "https://")):
            errors.append(f"{rid}: citation url geçersiz: {c!r}")
        if c.get("type") not in CITATION_TYPES:
            errors.append(f"{rid}: citation type geçersiz: {c.get('type')!r}")

    for e in rec.get("entities") or []:
        if not isinstance(e, dict) or not e.get("value"):
            errors.append(f"{rid}: entity 'value' boş olamaz")
        if e.get("type") not in ENTITY_TYPES:
            errors.append(f"{rid}: entity type geçersiz: {e.get('type')!r}")

    h = rec.get("hallucination") or {}
    if h.get("type") not in HALLUCINATION_TYPES:
        errors.append(f"{rid}: hallucination.type geçersiz: {h.get('type')!r}")
    if h.get("type") != "none" and h.get("severity") not in ("low", "medium", "high", "critical"):
        errors.append(f"{rid}: hallucination.severity geçersiz: {h.get('severity')!r}")

    if rec.get("recommendation") is not None and not isinstance(rec.get("recommendation"), bool):
        errors.append(f"{rid}: recommendation boolean olmalı")


def main() -> None:
    parser = argparse.ArgumentParser(description="Etiketlenen gold kayıtlarını doğrula")
    parser.add_argument("files", nargs="+", help="etiketlenen JSONL dosyaları")
    parser.add_argument("--strict", action="store_true", help="hata varsa exit 1")
    args = parser.parse_args()

    total_errors = 0
    for path in args.files:
        errors: list[str] = []
        n = 0
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if not line.strip():
                    continue
                n += 1
                try:
                    rec = json.loads(line)
                except json.JSONDecodeError as exc:
                    errors.append(f"#{n}: JSON ayrıştırma hatası: {exc}")
                    continue
                validate_record(rec, errors, n)
        status = "✓" if not errors else "✗"
        print(f"{status} {path}: {n} kayıt, {len(errors)} hata")
        for e in errors[:10]:
            print(f"    - {e}")
        if len(errors) > 10:
            print(f"    ... +{len(errors) - 10} hata daha")
        total_errors += len(errors)

    if args.strict and total_errors:
        sys.exit(1)


if __name__ == "__main__":
    main()
