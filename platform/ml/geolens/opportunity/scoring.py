"""A3-4 · Opportunity Scoring (İP-07).

Formula: OpportunityScore = Impact × Urgency × Confidence
  - Impact    1-10  : müşterinin visibility'ine potansiyel etki
  - Urgency   1-10  : aksiyon alınmazsa kaybın büyüme hızı
  - Confidence 0-1  : tespitin doğruluk olasılığı (deterministik veya ML)

Örnek (İP-07): score-drop → {impact: 9, urgency: 8, confidence: 0.95} → 68.4
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, Mapping, Optional

OPP_RANGE = (0.0, 100.0)


def opportunity_score(impact: float, urgency: float, confidence: float) -> float:
    if not (1 <= impact <= 10):
        raise ValueError(f"Impact 1-10 aralığında olmalı — gelen: {impact}")
    if not (1 <= urgency <= 10):
        raise ValueError(f"Urgency 1-10 aralığında olmalı — gelen: {urgency}")
    if not (0 <= confidence <= 1):
        raise ValueError(f"Confidence 0-1 aralığında olmalı — gelen: {confidence}")
    return round(impact * urgency * confidence, 2)


@dataclass
class Opportunity:
    rule_id: str
    category: str
    title: str
    detail: str
    impact: int           # 1-10
    urgency: int          # 1-10
    confidence: float     # 0-1
    score: float = 0.0    # impact*urgency*confidence
    ml_rank: float = 0.0  # ML tarafından belirlenen etki katsayısı (0-1)
    evidence: Dict[str, object] = field(default_factory=dict)
    language: str = "tr"

    def __post_init__(self) -> None:
        self.score = opportunity_score(self.impact, self.urgency, self.confidence)

    def to_dict(self) -> dict:
        return {
            "rule_id": self.rule_id,
            "category": self.category,
            "title": self.title,
            "detail": self.detail,
            "impact": self.impact,
            "urgency": self.urgency,
            "confidence": self.confidence,
            "score": self.score,
            "ml_rank": round(self.ml_rank, 4),
            "evidence": self.evidence,
            "language": self.language,
        }


def sort_opportunities(items: list[Opportunity]) -> list[Opportunity]:
    """ML_rank > score önceliğiyle sıralar (İP-07: en yüksek etki/acil/güvenli)."""
    return sorted(items, key=lambda o: (o.ml_rank, o.score), reverse=True)


# ---- Kural tabanlı skor üretimi (deterministik; ML_rank=0) ----

def score_rule(
    rule_id: str,
    category: str,
    title: str,
    detail: str,
    impact_offset: int = 0,
    urgency_offset: int = 0,
    confidence: float = 0.8,
    evidence: Optional[Mapping[str, object]] = None,
) -> Opportunity:
    base_impact: Dict[str, int] = {
        "visibility_loss": 9, "citation_gap": 8, "content_gap": 6,
        "authority_gap": 7, "structured_data": 6, "competitor_threat": 9,
        "engine_gap": 6, "recommendation": 5, "measurement": 4,
    }
    base_urgency: Dict[str, int] = {
        "visibility_loss": 8, "citation_gap": 7, "content_gap": 5,
        "authority_gap": 6, "structured_data": 4, "competitor_threat": 8,
        "engine_gap": 5, "recommendation": 4, "measurement": 3,
    }
    impact = max(1, min(10, base_impact.get(category, 5) + impact_offset))
    urgency = max(1, min(10, base_urgency.get(category, 4) + urgency_offset))
    opp = Opportunity(
        rule_id=rule_id, category=category, title=title, detail=detail,
        impact=impact, urgency=urgency, confidence=confidence,
    )
    if evidence:
        opp.evidence = dict(evidence)
    return opp
