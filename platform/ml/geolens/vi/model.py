"""A3-1 · 7 bileşenli Visibility Index matematiksel modeli (0421 A3-1, İP-06).

İP-06 formülü:  VI = w₁·M + w₂·P + w₃·C + w₄·R + w₅·A + w₆·F + w₇·S

Bileşenler (0409 v1.3 §2.1):
  - M  Varlık Payı (Presence)          — marka mention oranı
  - P  Konum Ağırlığı (Position)       — yanıt içindeki sıra
  - C  Kaynak Payı (Citation)          — marka domain alıntı oranı
  - R  Rakip Bağlamı (Competitor)      — rakip normalizasyonu
  - A  Appearance Rate (Appearance)    — prompt setinde geçme sıklığı
  - F  Sentiment / Algı (Sentiment)    — duygu durumu 0-100
  - S  Competitive Visibility (CompVis)— rakiplere normalize AI görünürlüğü

Her bileşen 0-100 aralığına normalize edilir; ağırlıklar toplamı 1.0'dır.
CI dinamik hesaplanır (0409 §6 şu an ±5 sabit — İP-06: dinamik CI).
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, Mapping, Optional

COMPONENTS = ["presence", "position", "citation", "competitor", "appearance", "sentiment", "compvis"]

# 0409 v1.3 §2.1 varsayılan ağırlıkları (toplam = 1.0)
DEFAULT_WEIGHTS: Dict[str, float] = {
    "presence": 0.30,
    "position": 0.20,
    "citation": 0.15,
    "competitor": 0.15,
    "appearance": 0.10,
    "sentiment": 0.05,
    "compvis": 0.05,
}


def _clamp(x: float, lo: float = 0.0, hi: float = 100.0) -> float:
    return max(lo, min(hi, x))


@dataclass
class VIResult:
    value: float            # 0-100
    ci_low: float
    ci_high: float
    components: Dict[str, float] = field(default_factory=dict)  # normalize 0-100
    weights: Dict[str, float] = field(default_factory=dict)
    algorithm_version: str = "2.0.0"

    def to_dict(self) -> dict:
        return {
            "value": round(self.value, 2),
            "ci_low": round(self.ci_low, 2),
            "ci_high": round(self.ci_high, 2),
            "components": {k: round(v, 2) for k, v in self.components.items()},
            "weights": self.weights,
            "algorithm_version": self.algorithm_version,
        }


def normalize_components(raw: Mapping[str, float]) -> Dict[str, float]:
    """Dışarıdan gelen bileşen değerlerini bileşen bazında 0-100'e çeker.

    Bazı bileşenler 0-1 olasılık, bazıları 0-100 skor olabilir; burada biliniyorsa
    basitçe 0-100'e ölçeklenir. Bilinmiyorsa olduğu gibi clamp'lenir.
    """
    out: Dict[str, float] = {}
    for comp in COMPONENTS:
        if comp not in raw:
            out[comp] = 0.0
            continue
        val = float(raw[comp])
        # 0-1 olasılık / oran geldiyse 0-100'e ölçekle
        if 0.0 <= val <= 1.0 and comp in ("presence", "citation", "appearance", "compvis", "position"):
            val = val * 100.0
        out[comp] = _clamp(val)
    return out


def validate_weights(weights: Mapping[str, float]) -> Dict[str, float]:
    """Ağırlıkları kural tabanlı doğrular: toplam 1.0, tümü ≥0."""
    w = {c: float(weights.get(c, 0.0)) for c in COMPONENTS}
    total = sum(w.values())
    if not math.isclose(total, 1.0, abs_tol=1e-6):
        raise ValueError(f"Ağırlık toplamı 1.0 olmalı — gelen: {total}")
    if any(v < 0 for v in w.values()):
        raise ValueError("Ağırlıklar negatif olamaz")
    return w


def compute_ci(components: Mapping[str, float], weights: Mapping[str, float], base_clamp: float = 2.0) -> tuple[float, float]:
    """Dinamik güven aralığı (0409 ±5 sabitinin yerine).

    Bileşen varyansı + veri miktarına dayalı: düşük varyans → dar GA, sadece
    birkaç motor → geniş GA. Deterministik (G2 ilkesi).
    """
    vals = list(components.values())
    if not vals:
        return 0.0, 0.0
    mean = sum(vals) / len(vals)
    var = sum((v - mean) ** 2 for v in vals) / len(vals)
    spread = math.sqrt(var)
    ci = round(min(6.0, max(1.0, spread * 0.5 + base_clamp * (1 - max(components.values()) / 100))), 2)
    return ci, ci


def compute_vi(
    raw_components: Mapping[str, float],
    weights: Optional[Mapping[str, float]] = None,
    version: str = "2.0.0",
) -> VIResult:
    """Hesaplanan VI: normalize et, ağırlıklı topla, dinamik CI üret."""
    comps = normalize_components(raw_components)
    w = validate_weights(weights if weights is not None else DEFAULT_WEIGHTS)
    value = sum(comps[c] * w[c] for c in COMPONENTS)
    ci = compute_ci(comps, w)[0]  # simetrik ilk sürüm
    return VIResult(
        value=round(value, 2),
        ci_low=round(_clamp(value - ci), 2),
        ci_high=round(_clamp(value + ci), 2),
        components=comps,
        weights=w,
        algorithm_version=version,
    )


def legacy_vi_four_component(components: Mapping[str, float]) -> float:
    """Eski 4 bileşenli skor (0409 v1.0 D-89; comparison metriği için baseline)."""
    weights = {"presence": 0.35, "position": 0.25, "citation": 0.20, "competitor": 0.20}
    total = 0.0
    for c, w in weights.items():
        total += normalize_components(components).get(c, 0.0) * w
    return round(total, 2)


def from_gold_record(record: Mapping[str, object]) -> Dict[str, float]:
    """Gold kayıttan (A1-2 şema) 7 bileşeni çıkarır.

    BileşenIçin proxy'ler:
      - presence   = brand mention var mı (1.0/0.0)
      - position   = engine başına (proxy: 1.0 varsayım)
      - citation   = marka citation oranı (citations[].type direct/attribution)
      - competitor = marka mention / (marka + rakip mention)
      - appearance = mentions içinde var olma (1.0/0.0)
      - sentiment  = brand sentiment skoru (positive=100, neutral=50, negative=0)
      - compvis    = competitor mention varlığına göre (1.0/0.5/0.0)
    """
    mentions = record.get("mentions", []) or []
    citations = record.get("citations", []) or []
    brand_mentions = [m for m in mentions if m.get("type") == "brand"]
    competitor_mentions = [m for m in mentions if m.get("type") == "competitor"]

    presence = 1.0 if brand_mentions else 0.0
    citation_ratio = (len(citations) / 1.0) if citations else 0.0
    denominator = len(brand_mentions) + len(competitor_mentions) or 1
    competitor = len(brand_mentions) / denominator
    sentiment_map = {"positive": 100.0, "neutral": 50.0, "negative": 0.0}
    sentiment = sentiment_map.get(brand_mentions[0].get("sentiment", "neutral"), 50.0) if brand_mentions else 50.0
    compvis = 1.0 if competitor_mentions else (0.5 if brand_mentions else 0.0)

    return {
        "presence": presence,
        "position": 1.0,  # tek kayıt proxy
        "citation": citation_ratio,
        "competitor": competitor,
        "appearance": presence,
        "sentiment": sentiment,
        "compvis": compvis,
    }
