"""A2-5 · LLM-as-Judge birim testleri (0421)."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "geolens"))

from features.judge import EvidenceBag, HallucinationJudge  # noqa: E402


def _bag(flags: list[dict]) -> EvidenceBag:
    return EvidenceBag(
        prompt_id="p1",
        responses=[{"id": "r1", "engine": "a", "text": "örnek cevap"}],
        cross_flags=flags,
    )


def test_threshold_triggered_calls_llm():
    """Eşik aşılırsa LLM çağrılır ve verdict işlenir (A2-5 maliyet eşikli tetikleme)."""
    bag = _bag([{"severity": "critical"}, {"severity": "high"}])  # skor 3+2=5 ≥ 3
    judge = HallucinationJudge(call_llm=lambda _p: "YANLIŞ ve doğrulanamaz", threshold=3)
    v = judge.judge(bag)
    assert v.triggered is True
    assert v.verdict == "fabricated"


def test_below_threshold_no_llm_call():
    """Eşik altındaysa LLM pahalı çağrısı yapılmaz — kural tabanlı fallback."""
    bag = _bag([{"severity": "low"}])  # skor 1 < 3
    judge = HallucinationJudge(call_llm=lambda _p: "YANLIŞ", threshold=3)
    v = judge.judge(bag)
    assert v.triggered is False
    assert v.verdict == "disputed"  # manuel inceleme önerilir


def test_no_flags_supported():
    """Hiç şüphe yoksa desteklenir — LLM gerekmez."""
    judge = HallucinationJudge(call_llm=lambda _p: "YANLIŞ", threshold=3)
    v = judge.judge(_bag([]))
    assert v.triggered is False
    assert v.verdict == "supported"


def test_english_disputed():
    """EN template: 'DISPUTED' işareti disputed verdict üretir."""
    bag = _bag([{"severity": "critical"}])  # skor 3 ≥ 1
    judge = HallucinationJudge(call_llm=lambda _p: "DISPUTED conflicting sources", threshold=1, lang="en")
    v = judge.judge(bag)
    assert v.triggered is True
    assert v.verdict == "disputed"


def test_default_caller_none_without_key(monkeypatch):
    """API key yoksa default caller None döner — serving key'siz fallback çalışır (M-1)."""
    monkeypatch.delenv("GEOLENS_JUDGE_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    from features.judge import default_llm_caller

    assert default_llm_caller() is None
