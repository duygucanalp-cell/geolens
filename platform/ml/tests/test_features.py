"""Aşama 2 feature testleri: hallucination (A2-4), judge (A2-5), NER hybrid (A2-2)."""

from geolens.features.hallucination import (
    HallucinationFlag,
    _extract_numbers,
    check_citation_urls,
    cross_source_check,
    flag_to_record,
)
from geolens.features.judge import EvidenceBag, HallucinationJudge, build_judge
from geolens.features.ner import Entity, HybridEntityRecognizer, summarize


# --- A2-4 cross-source ---
def test_cross_source_contradictory_numbers():
    responses = [
        {"id": "r1", "engine": "chatgpt", "text": "MobiTel 2023'te %30 büyüme bildirdi."},
        {"id": "r2", "engine": "gemini", "text": "MobiTel 2023'te %60 büyüme iddia ediyor."},
    ]
    flags = cross_source_check(responses)
    assert any(f.type == "T3" and f.confidence >= 0.7 for f in flags)


def test_cross_source_same_number_no_flag():
    responses = [
        {"id": "r1", "engine": "chatgpt", "text": "Şirket 5 milyon kullanıcıya sahip."},
        {"id": "r2", "engine": "gemini", "text": "Şirket 5 milyon kullanıcıya sahiptir."},
    ]
    assert cross_source_check(responses) == []


def test_cross_source_less_than_two_no_flags():
    assert cross_source_check([{"id": "r1", "engine": "p", "text": "tek cevap"}]) == []


def test_extract_numbers_basic():
    nums = _extract_numbers("30% büyüme, 1.5 milyon kullanıcı, $2.5 milyar gelir")
    assert len(nums) >= 3


def test_flag_to_record_schema():
    f = HallucinationFlag("T3", "high", "çelişki", 0.7, "cross-source")
    rec = flag_to_record(f, "chatgpt", "prompt_1")
    assert rec["type"] == "T3" and rec["prompt_id"] == "prompt_1" and rec["engine"] == "chatgpt"


def test_url_check_offline_fallback():
    assert check_citation_urls(["https://example.com/x"]) == []


# --- A2-5 judge ---
def test_judge_below_threshold_triggers_manual_review():
    judge = HallucinationJudge(call_llm=lambda p: "supported", threshold=5)
    bag = EvidenceBag(
        prompt_id="p1",
        responses=[{"id": "r1", "engine": "e", "text": "metin"}],
        cross_flags=[{"severity": "medium"}],  # suspicion 1 < 5
    )
    v = judge.judge(bag)
    assert v.triggered is False
    assert v.verdict == "disputed"


def test_judge_threshold_triggers_llm():
    judge = HallucinationJudge(call_llm=lambda p: "YANLIŞ: istatistik doğrulanamadı", threshold=1)
    bag = EvidenceBag(
        prompt_id="p1",
        responses=[{"id": "r1", "engine": "e", "text": "metin"}],
        cross_flags=[{"severity": "critical"}],
    )
    v = judge.judge(bag)
    assert v.triggered is True
    assert v.verdict == "fabricated"


def test_judge_no_flags_supported():
    judge = build_judge()  # key yok → kural fallback
    bag = EvidenceBag(prompt_id="p1", responses=[{"id": "r1", "engine": "e", "text": "metin"}], cross_flags=[])
    assert judge.judge(bag).verdict == "supported"


# --- A2-2 NER hybrid ---
def test_ner_rules_leg():
    ner = HybridEntityRecognizer()
    ents = ner.recognize("Turkcell 5G teknolojisiyle telekom sektöründe öncüdür.")
    types = {e.type for e in ents}
    assert "brand" in types
    assert "sector" in types


def test_ner_model_leg_adds_entities():
    ner = HybridEntityRecognizer(model_predict=lambda t: [Entity("Acme Mobile", "organization", 0, 11, "model")])
    ents = ner.recognize("Acme Mobile teknoloji alanında çalışır.")
    lower = {e.value.lower() for e in ents}
    assert "acme mobile" in lower
    assert any(e.source == "model" for e in ents)


def test_ner_llm_fallback_when_empty():
    ner = HybridEntityRecognizer(llm_extract=lambda t: [Entity("hava", "weather", 0, 4, "llm")])
    ents = ner.recognize("Bugün hava güzel.")
    assert ents and ents[0].source == "llm"


def test_ner_summarize_schema():
    ents = [Entity("Turkcell", "brand", 0, 8, "rules")]
    s = summarize(ents)
    assert s == {"brand": ["Turkcell"]}
