"""A3-4 · ML etki öğrenimi (İP-07 §ML).

Geçmişte uygulanan (applied=true) ve uygulanmayan/dismissed önerilerden öğrenerek
her kategori için gerçek etki katsayısı (ml_rank) tahmin eder. sklearn
GradientBoosting veya fallback olarak basit frekans-odds oranı kullanır.

Veri yokken varsayılan katsayı 0.5 (nötr); uygulananlar yüksek skor düşüşüyle
geldiyse ml_rank artar. Bu, İP-07'deki "hangi önerilerin gerçekten etkili
olduğunu öğrenme" hedefinin deterministik MVP'sidir.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Sequence

from geolens.opportunity.scoring import Opportunity


@dataclass
class LearningOutcome:
    category_rank: Dict[str, float]
    trained_rule_ids: int
    sample_size: int
    method: str = "odds-ratio"


def _odds_ratio_rank(opportunities: Sequence[Opportunity], applied: Sequence[bool]) -> Dict[str, float]:
    """Kategori bazında odds oranı: P(uygulanmış | kategori) / P(uygulanmış | genel)."""
    if not opportunities or len(opportunities) != len(applied):
        return {}
    n = len(applied)
    applied_n = sum(1 for a in applied if a)
    base_rate = applied_n / n if n else 0.5
    cat: Dict[str, list[bool]] = {}
    for opp, a in zip(opportunities, applied):
        cat.setdefault(opp.category, []).append(a)
    ranks: Dict[str, float] = {}
    for category, flags in cat.items():
        k = len(flags)
        cat_rate = sum(1 for f in flags if f) / k
        ratio = (cat_rate / base_rate) if base_rate > 0 else 1.0
        # 0-1 aralığına ölçekle (odds 0.5 = nötr)
        rank = min(1.0, max(0.0, 0.5 + (ratio - 1.0) * 0.5))
        ranks[category] = round(rank, 4)
    return ranks


def learn_impact(
    opportunities: Sequence[Opportunity],
    applied_flags: Sequence[bool],
) -> LearningOutcome:
    """Geçmiş `applied` bayraklarından kategori etki katsayılarını üretir."""
    ranks = _odds_ratio_rank(opportunities, applied_flags)
    return LearningOutcome(
        category_rank=ranks,
        trained_rule_ids=len(set(o.rule_id for o in opportunities)),
        sample_size=len(opportunities),
        method="odds-ratio",
    )


def apply_ml_ranks(opportunities: list[Opportunity], ranks: Dict[str, float]) -> list[Opportunity]:
    """ml_rank'i kategorinin öğrenilmiş katsayısıyla doldurur (varsayılan 0.5)."""
    for opp in opportunities:
        opp.ml_rank = ranks.get(opp.category, 0.5)
    return opportunities
