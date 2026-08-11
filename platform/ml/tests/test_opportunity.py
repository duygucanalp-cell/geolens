"""A3-4 · opportunity paketi testleri (OpportunityScore + ML etki öğrenimi)."""

import pytest

from geolens.opportunity.learning import apply_ml_ranks, learn_impact
from geolens.opportunity.scoring import (
    opportunity_score,
    score_rule,
    sort_opportunities,
)


def test_opportunity_score_formula():
    assert opportunity_score(9, 8, 0.95) == pytest.approx(68.4)
    assert opportunity_score(5, 5, 0.5) == pytest.approx(12.5)


def test_opportunity_score_validates_ranges():
    with pytest.raises(ValueError):
        opportunity_score(0, 5, 0.5)
    with pytest.raises(ValueError):
        opportunity_score(5, 11, 0.5)
    with pytest.raises(ValueError):
        opportunity_score(5, 5, 1.5)


def test_score_rule_builds_opportunity():
    opp = score_rule("rule-score-drop", "visibility_loss", "Skor düşüşü", "Detay")
    assert opp.impact == 9
    assert opp.urgency == 8
    assert opp.confidence == 0.8
    assert opp.score == pytest.approx(9 * 8 * 0.8)


def test_sort_opportunities_prefers_ml_rank():
    a = score_rule("r1", "citation_gap", "A", "x")
    b = score_rule("r2", "visibility_loss", "B", "y")
    a.ml_rank = 0.9
    b.ml_rank = 0.3
    ordered = sort_opportunities([b, a])
    assert ordered[0].rule_id == "r1"


def test_learn_impact_odds_ratio():
    opps = [
        score_rule("r1", "citation_gap", "A", "x"),
        score_rule("r1", "citation_gap", "A", "x"),
        score_rule("r2", "content_gap", "B", "y"),
        score_rule("r2", "content_gap", "B", "y"),
    ]
    outcome = learn_impact(opps, [True, True, False, False])
    assert outcome.trained_rule_ids == 2
    assert outcome.sample_size == 4
    assert outcome.category_rank["citation_gap"] > outcome.category_rank["content_gap"]


def test_apply_ml_ranks_default():
    opps = [score_rule("r1", "unknown_cat", "A", "x")]
    apply_ml_ranks(opps, {})
    assert opps[0].ml_rank == 0.5


def test_learner_handles_unequal_lengths():
    outcome = learn_impact([score_rule("r1", "measurement", "A", "x")], [])
    assert outcome.category_rank == {}


def test_to_dict_roundtrip():
    opp = score_rule("r1", "visibility_loss", "T", "D")
    d = opp.to_dict()
    assert d["rule_id"] == "r1"
    assert d["score"] == pytest.approx(9 * 8 * 0.8)
    assert 0 <= d["ml_rank"] <= 1
