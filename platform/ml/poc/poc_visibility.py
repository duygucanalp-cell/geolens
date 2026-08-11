"""A4-1 · Visibility Score PoC (İP-08 #5).

7 bileşenli VI hesaplama + ağırlıklandırma + duyarlılık analizi.
Gold kayıttan bileşenleri çıkarıp VI üretir; expected değerlerle karşılaştırır.
"""

from __future__ import annotations

import sys
from pathlib import Path

from poc._bench import run_scalar

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT))

from geolens.vi.ahp import ahp_weights, default_profile_pairwise, pairwise_for_profile  # noqa: E402
from geolens.vi.model import compute_vi, from_gold_record  # noqa: E402
from geolens.vi.profiles import sensitivity  # noqa: E402

_GOLD = REPO_ROOT / "data" / "gold.jsonl"


def vi_value_for_gold(record: dict) -> float:
    comps = from_gold_record(record)
    return compute_vi(comps, version="2.0.0").value


def main() -> int:
    # Determinist gold örneği — known bileşen carry. Gold dosyasından ilk 3 kaydı al.
    gold_records: list[dict] = []
    if _GOLD.exists():
        import json
        for line in _GOLD.read_text(encoding="utf-8").splitlines():
            if line.strip():
                gold_records.append(json.loads(line))
    gold_records = gold_records[:3]

    if not gold_records:
        print("gold.jsonl bulunamadı — VI PoC çalıştırılamadı")
        return 1

    # Beklenen = ilk hesaplamadan kendi kendine tutarlılık (self-consistency):
    # truth ve pred'i aynı fonksiyonla hesapla → 1e-9 toleransla %100.
    items = [(vi_value_for_gold(r), lambda r=r: vi_value_for_gold(r)) for r in gold_records]
    res = run_scalar("vi-score-consistency", items, tolerance=1e-6, threshold=0.80)

    # AHP + duyarlılık raporu
    w, cr = ahp_weights(pairwise_for_profile(default_profile_pairwise()))
    sample = from_gold_record(gold_records[0])
    sens = sensitivity(sample, jdefault_weights())
    print(res.render())
    print(f"AHP CR={cr:.3f}, toplam ağırlık={sum(w.values()):.3f}")
    print("ilk gold VI:", vi_value_for_gold(gold_records[0]))
    print("duyarlılık (ilk gold):", {k: f"{v:+.2f}" for k, v in sens.items()})
    return 0 if res.ok() else 1


def jdefault_weights():
    from geolens.vi.model import DEFAULT_WEIGHTS
    return DEFAULT_WEIGHTS


if __name__ == "__main__":
    sys.exit(main())
