"""A3-2 · Sektör bazlı ağırlık profilleri + duyarlılık analizi (İP-06).

3 sektör profili (0420 İP-06 §çıktı 2):
  - Varsayılan (genel): 0409 v1.3
  - E-ticaret: recommendation/appearance ağırlıklı
  - Sağlık: authority/sentiment ağırlıklı (güven odaklı)
  - Teknoloji/B2B: citation/competitor ağırlıklı

Duyarlılık analizi: tek bir bileşen ağırlığı Δ değiştiğinde VI'nın puan değişimi —
rapor için formül `ΔVI = (Δweight) × component_value`.
"""
from __future__ import annotations

from typing import Dict, Mapping

from geolens.vi.model import COMPONENTS, DEFAULT_WEIGHTS, compute_vi

PROFILES: Dict[str, Dict[str, float]] = {
    "default": dict(DEFAULT_WEIGHTS),
    "ecommerce": {
        "presence": 0.20, "position": 0.15, "citation": 0.10,
        "competitor": 0.10, "appearance": 0.25, "sentiment": 0.10, "compvis": 0.10,
    },
    "health": {
        "presence": 0.15, "position": 0.10, "citation": 0.20,
        "competitor": 0.05, "appearance": 0.10, "sentiment": 0.30, "compvis": 0.10,
    },
    "technology": {
        "presence": 0.25, "position": 0.15, "citation": 0.20,
        "competitor": 0.20, "appearance": 0.10, "sentiment": 0.05, "compvis": 0.05,
    },
}

PROFILE_LABELS = {
    "ecommerce": "E-ticaret (recommendation/appearance odaklı)",
    "health": "Sağlık (authority/sentiment odaklı — güven)",
    "technology": "Teknoloji/B2B (citation/competitor odaklı)",
}


def profile_by_sector(sector: str | None) -> Dict[str, float]:
    """Sektör adına göre profil; bilinmeyen sektör → default."""
    mapping = {
        "perakende": "ecommerce",
        "finans": "health",
        "saglik": "health",
        "teknoloji": "technology",
        "telekom": "technology",
    }
    key = mapping.get((sector or "").lower().strip())
    return PROFILES.get(key or "default", PROFILES["default"])


def sensitivity(
    components: Mapping[str, float],
    weights: Mapping[str, float],
    delta: float = 0.05,
) -> Dict[str, float]:
    """Her bileşenin ağırlığı Δ artırılıp diğerlerine orantılı dağıltınca VI değişimi.

    ΔVI_i ≈ weight_i × (1 - weight_i) × component_i ... doğru orantı yerine
    basit yaklaşım: tek bileşen ağırlığı delta kadar kaydırılır, toplam korunur.
    Rapor tablosu için {component: ΔVI_puan}.
    """
    base = compute_vi(components, weights)
    out: Dict[str, float] = {}
    for c in COMPONENTS:
        shifted = dict(weights)
        # delta'yı seçilen bileşene taşı, kalanlardan düş (oransal)
        others = [x for x in COMPONENTS if x != c]
        total_others = sum(shifted[x] for x in others) or 1.0
        shifted[c] = shifted[c] + delta
        excess = 0.0
        for x in others:
            share = shifted[x] / total_others
            shifted[x] -= delta * share
            excess += shifted[x]
        new_vi = compute_vi(components, shifted)
        out[c] = round(new_vi.value - base.value, 3)
    return out


def profile_report() -> str:
    lines = ["## Sektör Ağırlık Profilleri (A3-2)", ""]
    table = ["| Sektör | " + " | ".join(COMPONENTS) + " |", "|:--|" + "---|" * len(COMPONENTS)]
    for name, w in PROFILES.items():
        label = PROFILE_LABELS.get(name, "Varsayılan / genel")
        table.append(f"| {label} | " + " | ".join(f"{w[c]:.2f}" for c in COMPONENTS) + " |")
    lines.append("\n".join(table))
    lines.append("")
    lines.append("Duyarlılık analizi: `profile_report` + `sensitivity()` komutuyla hesaplanır;")
    lines.append("her bileşen ağırlığı %5 artırılıp geri kalanlardan orantısal düşülür ve VI puan farkı raporlanır.")
    return "\n".join(lines)
