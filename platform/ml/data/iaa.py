"""A1-3 · IAA ölçümü (inter-annotator agreement).

İki (veya daha fazla) etiketçi dosyası arasında anlaşmayı ölçer:
  - Cohen's Kappa (ikili/çok-sınıflı etiketler için)
  - tam eşleşme oranı (exact agreement)

Kullanım:
    python data/iaa.py --label sentiment annotator1.jsonl annotator2.jsonl
    python data/iaa.py --label intent gold.jsonl annotator_alt.jsonl

Dosya formatı: her satır {"id": ..., "<label>": ...} (JSON Lines).
"""
from __future__ import annotations

import argparse
import json
import sys

from sklearn.metrics import cohen_kappa_score


def load(path: str, label: str, key: str = "id") -> dict:
    labels: dict = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if not line.strip():
                continue
            rec = json.loads(line)
            if label not in rec:
                raise ValueError(f"{path} içinde '{label}' alanı yok: {line[:80]}")
            labels[str(rec[key])] = rec[label]
    return labels


def main() -> None:
    parser = argparse.ArgumentParser(description="IAA (inter-annotator agreement) ölçümü")
    parser.add_argument("--label", required=True, help="ölçülecek etiket alanı (örn. sentiment)")
    parser.add_argument("files", nargs="+", help="etiketçi dosyaları (en az 2)")
    args = parser.parse_args()

    if len(args.files) < 2:
        parser.error("en az 2 etiketçi dosyası verilmelidir")

    annotator_maps = [load(f, args.label) for f in args.files]
    common = set(annotator_maps[0])
    for m in annotator_maps[1:]:
        common &= set(m)
    if not common:
        print("Ortak örnek yok — IAA hesaplanamadı.", file=sys.stderr)
        sys.exit(1)

    aligned = [[m[k] for m in annotator_maps] for k in sorted(common)]
    # aligned[i] = tüm etiketçilerin i. örnekteki etiketi

    # Anlaşma oranı
    exact = sum(1 for row in aligned if len(set(row)) == 1)
    agree = exact / len(aligned)

    print(f"Etiket animatorları: {len(args.files)}, ortak örnek: {len(aligned)}")
    print(f"Tam anlaşma: {agree:.2%} ({exact}/{len(aligned)})")

    if len(args.files) == 2:
        y1, y2 = zip(*aligned)
        kappa = cohen_kappa_score(y1, y2)
        print(f"Cohen's Kappa: {kappa:.3f}")
        if kappa < 0.20:
            print("Yorum: Zayıf anlaşma — etiketleme kılavuzu gözden geçirilmeli.")
        elif kappa < 0.6:
            print("Yorum: Orta anlaşma — farklılıklar analiz edilmeli.")
        else:
            print("Yorum: Güçlü anlaşma.")
    else:
        print("(3+ etiketçi için pairwise Cohen's Kappa önerilir; script tek pair için basittir.)")


if __name__ == "__main__":
    main()
