"""A3-1/A3-2/A3-3 · Rapor üretici CLI (ml/data/ altına markdown yazar).

Çıktılar:
  - `data/vi_ahp_report.md`      A3-1 · AHP ağırlık tespiti + varsayılan profil
  - `data/vi_profiles_report.md` A3-2 · sektör profilleri + duyarlılık analizi
  - `data/vi_validation_report.md` A3-3 · gold verisi üzerinde VI doğrulama
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from geolens.vi.ahp import ahp_weights, default_profile_pairwise, pairwise_for_profile
from geolens.vi.model import (
    COMPONENTS,
    DEFAULT_WEIGHTS,
    from_gold_record,
)
from geolens.vi.profiles import (
    PROFILE_LABELS,
    PROFILES,
    sensitivity,
)
from geolens.vi.validation import validate_gold

REPORTS_DIR = Path(__file__).resolve().parents[2] / "data"


def ahp_report() -> str:
    weights, cr = ahp_weights(pairwise_for_profile(default_profile_pairwise()))
    lines = [
        "## A3-1 · AHP Ağırlık Tespiti (İP-06 §Ağırlıklandırma)",
        "",
        "Method: Saaty 1-9 ikili karşılaştırma, geometric mean ile özvektör tahmini.",
        "Sonuç modelin varsayılan ağırlıklarının bilimsel temelidir.",
        "",
        "| Bileşen | AHP ağırlığı |",
        "|:-------|:------------:|",
    ]
    for c in COMPONENTS:
        lines.append(f"| {c} | {weights[c]:.3f} |")
    lines += [
        "",
        f"Consistency Ratio (CR): {cr:.3f} " + ("" if cr < 0.10 else "(>0.10 — pairwise gözden geçirilmeli)"),
        "",
        "Referans varsayılan ağırlıklar (0409 v1.3 §2.1): " + ", ".join(
            f"{c}={DEFAULT_WEIGHTS[c]:.2f}" for c in COMPONENTS
        ),
    ]
    return "\n".join(lines)


def profiles_report(sample_components: dict[str, float]) -> str:
    lines = [
        "## A3-2 · Sektör Ağırlık Profilleri ve Duyarlılık Analizi (İP-06 §çıktı 2)",
        "",
        "| Sektör profili | " + " | ".join(COMPONENTS) + " |",
        "|:--|" + "---|" * len(COMPONENTS),
    ]
    for name, w in PROFILES.items():
        lines.append(f"| {PROFILE_LABELS.get(name, 'Varsayılan')} | " + " | ".join(f"{w[c]:.2f}" for c in COMPONENTS) + " |")
    lines += [
        "",
        "### Duyarlılık Analizi (tek bileşen ağırlığı +%5, geri kalan orantılı düşürülür)",
        "",
        "| Bileşen | ΔVI (puan) |",
        "|:--|:--|",
    ]
    for c, dv in sensitivity(sample_components, DEFAULT_WEIGHTS, delta=0.05).items():
        lines.append(f"| {c} | {dv:+.3f} |")
    lines += [
        "",
        f"Örnek bileşen seti: {json.dumps(sample_components, ensure_ascii=False)}",
        "",
        "Yorum: presence/position yüksek ağırlıklı bileşenlerdeki artış VI'yı en çok",
        "etkiler; düşük değerli bileşenlerin ağırlığının artması bağıl dağıtımdan",
        "dolayı küçük negatif etki yaratabilir. Sektör profili seçimi (profile_by_sector)",
        "API'nin `X-GeoLens-Profile` başlığıyla yönetilir.",
    ]
    return "\n".join(lines)


def validation_report(gold_path: Path) -> str:
    results = validate_gold(gold_path)
    n = results["n"]
    dist = results["distribution"]
    corr = results["correlation"]
    lines = [
        "## A3-3 · Gold Doğrulama Raporu (İP-06 §Doğrulama)",
        "",
        f"- Numune: **{n}** gold kayıt (sentetik, A1-2)",
        f"- Yeni VI (7 bileşen) dağılımı: min={dist['new_min']}, max={dist['new_max']}, "
        f"mean={dist['new_mean']}, median={dist['new_median']}",
        f"- Korelasyon (yeni vs legacy 4 bileşen): Pearson={corr['pearson']}, Spearman={corr['spearman']}",
        f"- MAE (legacy vs new): {results['legacy_vs_new']['mae']} · RMSE: {results['legacy_vs_new']['rmse']} · "
        f"R²: {results['legacy_vs_new']['r2']}",
        "",
        "| Metrik | Legacy (4 bileşen) | Yeni VI (7 bileşen) |",
        "|:--|:--|:--|",
        f"| MAE | {results['legacy_vs_new']['mae']} | — (kendine karşı 0) |",
        f"| RMSE | {results['legacy_vs_new']['rmse']} | — |",
        f"| R² | {results['legacy_vs_new']['r2']} | — |",
        "",
        "Not: Rapor sentetik gold üzerinde metodolojik gösterimdir; gerçek uzman",
        "etiketli veri (A0-3) geldiğinde aynı pipeline manuel skorlarla doğrulanır.",
    ]
    return "\n".join(lines)


def main() -> int:
    gold_path = REPORTS_DIR / "gold.jsonl"
    if not gold_path.exists():
        print(f"gold.jsonl bulunamadı: {gold_path}", file=sys.stderr)
        return 1

    sample = from_gold_record(json.loads(
        next(line for line in gold_path.read_text(encoding="utf-8").splitlines() if line.strip())
    ))

    targets = [
        (REPORTS_DIR / "vi_ahp_report.md", ahp_report()),
        (REPORTS_DIR / "vi_profiles_report.md", profiles_report(sample)),
        (REPORTS_DIR / "vi_validation_report.md", validation_report(gold_path)),
    ]
    for path, content in targets:
        path.write_text(content + "\n", encoding="utf-8")
        print(f"yazildi: {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
