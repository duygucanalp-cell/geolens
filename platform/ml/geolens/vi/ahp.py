"""A3-1 · AHP (Analytic Hierarchy Process) ağırlık tespiti (İP-06 §Ağırlıklandırma).

Saaty'nin 9 ölçeğiyle ikili karşılaştırma matrisinden bileşen ağırlıklarını
çıkarır (geometric mean yöntemi) ve tutarlılık oranı (CR < 0.10) kontrol eder.

Kullanım örneği (0420 İP-06 default @ 0409 v1.3): presence position'dan "
biraz daha önemli"; her carşılaştırma Judge → CR. Bu modül CI'da deterministik
AHP sonucu üretir; gerçek sektör kurulunda pairwise değerleri doldurulacaktır.
"""
from __future__ import annotations

from typing import Dict, Mapping

import numpy as np

from geolens.vi.model import COMPONENTS, DEFAULT_WEIGHTS

# Saaty 1-9 ölçeği (1 = eşit, 3 = orta, 5 = güçlü, 7 = çok güçlü, 9 = aşırı)
RI = {1: 0.0, 2: 0.0, 3: 0.58, 4: 0.90, 5: 1.12, 6: 1.24, 7: 1.32, 8: 1.41, 9: 1.45}


def pairwise_for_profile(preferences: Mapping[str, Mapping[str, int]]) -> np.ndarray:
    """İkili karşılaştırma matrisi kurar.

    preferences: {compA: {compB: skor}} — skor Saaty ölçeğinde (compA vs compB).
    Matris simetrik: aij = skor, aji = 1/skor.
    """
    idx = {c: i for i, c in enumerate(COMPONENTS)}
    n = len(COMPONENTS)
    matrix = np.ones((n, n))
    for a, row in preferences.items():
        for b, score in row.items():
            i, j = idx[a], idx[b]
            matrix[i][j] = score
            matrix[j][i] = 1.0 / score
    return matrix


def ahp_weights(matrix: np.ndarray) -> tuple[Dict[str, float], float]:
    """Geometric mean yöntemiyle ağırlık üretir; CR ile tutarlılık döner.

    Dönen: ({comp: weight_sum_to_1}, consistency_ratio)
    """
    n = matrix.shape[0]
    gm = np.exp(np.log(matrix).mean(axis=1))  # geometric mean satır bazında
    weights = gm / gm.sum()
    # CR hesabı: lambda_max yaklaşımı (product-sum)
    col_sum = matrix.sum(axis=0)
    lam_max = (col_sum * weights).sum()
    ci = (lam_max - n) / (n - 1) if n > 1 else 0.0
    cr = ci / RI[n] if n in RI and RI[n] > 0 else 0.0
    weight_map = {COMPONENTS[i]: float(w) for i, w in enumerate(weights)}
    return weight_map, float(cr)


def default_profile_pairwise() -> Dict[str, Mapping[str, int]]:
    """0409 v1.3 ağırlık dağılımını (30/20/15/15/10/5/5) yansıtan pairwise seti."""
    # Relative importance rough: presence en kritik, sentiment/compvis en az.
    return {
        "presence": {"position": 2, "citation": 2, "competitor": 2, "appearance": 3, "sentiment": 5, "compvis": 5},
        "position": {"citation": 1, "competitor": 1, "appearance": 2, "sentiment": 3, "compvis": 3},
        "citation": {"competitor": 1, "appearance": 2, "sentiment": 3, "compvis": 3},
        "competitor": {"appearance": 2, "sentiment": 3, "compvis": 3},
        "appearance": {"sentiment": 2, "compvis": 2},
        "sentiment": {"compvis": 1},
    }


def ahp_default_weights() -> tuple[Dict[str, float], float]:
    """Varsayılan model ağırlıkları: 0409 v1.3 pairwise tabanlı AHP sonucu."""
    matrix = pairwise_for_profile(default_profile_pairwise())
    weights, cr = ahp_weights(matrix)
    return weights, cr


def explain(weights: Mapping[str, float], cr: float) -> str:
    lines = ["| Bileşen | AHP ağırlığı |", "|:-------|:------------:|"]
    for c in COMPONENTS:
        lines.append(f"| {c} | {weights[c]:.3f} |")
    lines.append("")
    lines.append(f"Consistency Ratio (CR): {cr:.3f} {'' if cr < 0.10 else '(>0.10 — matris tutarsız, pairwise gözden geçirilmeli)'}")
    lines.append(f"DEFAULT_WEIGHTS toplamı: {sum(DEFAULT_WEIGHTS.values()):.3f} (varsayılan her biri sabit referans)")
    return "\n".join(lines)
