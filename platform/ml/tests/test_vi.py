"""A3-1/A3-2/A3-3 · vi paketi testleri (7 bileşenli VI model + AHP + doğrulama)."""

import pytest

from geolens.vi.ahp import ahp_weights, default_profile_pairwise, explain, pairwise_for_profile
from geolens.vi.model import (
    COMPONENTS,
    DEFAULT_WEIGHTS,
    compute_ci,
    compute_vi,
    from_gold_record,
    legacy_vi_four_component,
    normalize_components,
    validate_weights,
)
from geolens.vi.profiles import PROFILES, profile_by_sector, sensitivity
from geolens.vi.validation import validate_gold


def test_default_weights_sum_one():
    assert sum(DEFAULT_WEIGHTS.values()) == pytest.approx(1.0)


def test_validate_weights_raises_on_wrong_total():
    with pytest.raises(ValueError):
        validate_weights({"presence": 0.5})  # toplam 0.5 ≠ 1.0


def test_compute_vi_basic():
    raw = {"presence": 1.0, "position": 1.0, "citation": 0.5, "competitor": 0.5,
           "appearance": 0.8, "sentiment": 75.0, "compvis": 0.6}
    result = compute_vi(raw)
    assert 0 <= result.value <= 100
    assert result.ci_low <= result.value <= result.ci_high
    assert set(result.components) == set(COMPONENTS)


def test_compute_vi_all_zero_scores_low():
    result = compute_vi({c: 0.0 for c in COMPONENTS})
    assert result.value == 0.0


def test_compute_vi_all_max_scores_high():
    result = compute_vi({c: 100.0 for c in COMPONENTS})
    assert result.value == pytest.approx(100.0, abs=0.01)


def test_normalize_scales_01_to_100():
    out = normalize_components({"presence": 0.4, "citation": 0.2, "position": 0.9,
                                "competitor": 0.5, "appearance": 0.3, "sentiment": 50.0, "compvis": 0.2})
    assert out["presence"] == pytest.approx(40.0)
    assert out["sentiment"] == pytest.approx(50.0)


def test_compute_ci_deterministic_and_bounded():
    comps = {"presence": 100.0, "position": 80.0, "citation": 60.0, "competitor": 40.0,
             "appearance": 30.0, "sentiment": 50.0, "compvis": 20.0}
    lo1, hi1 = compute_ci(comps, DEFAULT_WEIGHTS)
    lo2, hi2 = compute_ci(comps, DEFAULT_WEIGHTS)
    assert (lo1, hi1) == (lo2, hi2)
    assert 0.0 <= lo1 <= hi1 <= 6.0


def test_legacy_four_component_differs():
    raw = {"presence": 1.0, "position": 0.2, "citation": 0.9, "competitor": 0.1,
           "appearance": 0.3, "sentiment": 20.0, "compvis": 0.0}
    legacy = legacy_vi_four_component(raw)
    new = compute_vi(raw, version="2.0.0").value
    assert legacy != pytest.approx(new, abs=0.1)


def test_ahp_weights_converges_to_default_profile():
    matrix = pairwise_for_profile(default_profile_pairwise())
    weights, cr = ahp_weights(matrix)
    assert sum(weights.values()) == pytest.approx(1.0, abs=1e-6)
    assert abs(weights["presence"] - 0.30) < 0.08  # en yüksek bileşen
    assert cr < 0.10  # tutarlı matris


def test_ahp_explain_has_cr_line():
    matrix = pairwise_for_profile(default_profile_pairwise())
    weights, cr = ahp_weights(matrix)
    assert "Consistency Ratio" in explain(weights, cr)


def test_profiles_exist_and_sum_one():
    assert "default" in PROFILES and "ecommerce" in PROFILES and "health" in PROFILES and "technology" in PROFILES
    for profile in PROFILES.values():
        assert sum(profile.values()) == pytest.approx(1.0, abs=1e-6)


def test_profile_by_sector_mapping():
    assert profile_by_sector("saglik") is PROFILES["health"]
    assert profile_by_sector("perakende") is PROFILES["ecommerce"]
    assert profile_by_sector("unknown sector") is PROFILES["default"]


def test_sensitivity_shifts_presence():
    components = {"presence": 100.0, "position": 80.0, "citation": 60.0, "competitor": 40.0,
                  "appearance": 30.0, "sentiment": 50.0, "compvis": 20.0}
    delta = sensitivity(components, DEFAULT_WEIGHTS, delta=0.05)
    # Yüksek değerli bileşen (presence=100) ağırlığı artınca VI yükselir;
    # düşük değerli bileşen (compvis=20) artınca proposal dağıtımı nedeniyle VI düşer.
    assert delta["presence"] > 0
    assert delta["compvis"] < 0


def test_from_gold_record_proxies():
    rec = {
        "mentions": [
            {"type": "brand", "sentiment": "positive"},
            {"type": "competitor", "sentiment": "neutral"},
        ],
        "citations": [{"type": "direct"}, {"type": "attribution"}],
    }
    comps = from_gold_record(rec)
    assert comps["presence"] == 1.0
    assert comps["sentiment"] == 100.0
    assert comps["competitor"] == 0.5
    assert comps["compvis"] == 1.0


def test_validate_gold_returns_stats(tmp_path):
    gold = tmp_path / "gold.jsonl"
    with open(gold, "w", encoding="utf-8") as f:
        for k, sent in [("brand", "positive"), ("competitor", "neutral"), ("brand", "negative")]:
            f.write(json_record({"type": k, "sentiment": sent}) + "\n")
    result = validate_gold(gold)
    assert result["n"] == 3
    assert "mae" in result["new"] and "rmse" in result["new"]
    assert "spearman" in result["correlation"]


def json_record(mention):
    import json as _json
    return _json.dumps({"mentions": [mention], "citations": [{"type": "direct"}]})
