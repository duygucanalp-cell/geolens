"""A2-5 · LLM-as-Judge pipeline (maliyet eşikli tetikleme).

0421 A2-5: cross-source tespiti (A2-4) bir şüphe eşiğini aştığında pahalı LLM
yargıcı tetiklenir; eşik altında kural tabanlı sonuç yeterli kabul edilir
(0421 §6 risk: "LLM-as-Judge maliyeti yüksek → şüphe eşiği bazlı tetikleme").

Yargıç arayüzü:
  - `LLMJudge.judge(evidence) -> dict` — dış LLM'e gönderilecek şablon.
  - Çevrimdışı ortamda (API key yok) kural tabanlı fallback devreye girer; bu
    sayede testler ve CI key'siz çalışır (M-1 fallback ilkesi).
"""
from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Callable, Optional

DEFAULT_SUSPICION_THRESHOLD = 3
"""Bu sayıda şüpheli flag birikince LLM yargıcı tetiklenir (0421 §6)."""

JUDGE_TEMPLATE_TR = """Aşağıdaki AI motor cevaplarını hallüsinasyon açısından değerlendir.

{evidence_text}

Aşağıdaki maddelerden hangileri doğrulanabilir değil?
1. Sayısal iddialar (istatistik, büyüme, gelir)
2. Kaynak gösterimi (citation URL)
3. Marka hakkında olgusal iddia

Yanıtını {independence} bağımsız olarak [DOĞRU|TARTIŞMALI|YANLIŞ] şeklinde listele.
"""

JUDGE_TEMPLATE_EN = """Evaluate the following AI engine responses for hallucination.

{evidence_text}

Which of the following cannot be verified?
1. Numerical claims (statistics, growth, revenue)
2. Source citations (citation URL)
3. Factual claims about the brand

List each as [TRUE|DISPUTED|FALSE] independently.
"""


@dataclass
class JudgeVerdict:
    verdict: str          # supported|disputed|fabricated
    reason: str
    triggered: bool       # LLM gerçekten çağrıldı mı?
    confidence: float


def _norm_lower(text: str) -> str:
    """Türkçe büyük/küçük harf normalizasyonu (ı/i/İ/I ve ş/Ş)."""
    tr_map = {"İ": "i", "I": "i", "Ş": "ş", "Ç": "ç", "Ğ": "ğ", "Ü": "ü", "Ö": "ö"}
    out = "".join(tr_map.get(ch, ch) for ch in text)
    return out.lower()


_FABRICATED_MARKERS = ("yanl", "fabricat", "hallucinat", "doğrulanamaz", "doğrulanamad", "unverifiable", "fake")
_DISPUTED_MARKERS = ("tartışmalı", "tartismali", "çelişkili", "disputed", "conflict", "belirsiz", "net değil")


@dataclass
class EvidenceBag:
    prompt_id: str
    responses: list[dict]          # [{id, engine, text}]
    cross_flags: list[dict]        # A2-4 sonucu
    suspicion_score: int = 0


class HallucinationJudge:
    """Eşik bazlı tetikleme; çağrıyı configurable 'caller' ile soyutlar."""

    def __init__(
        self,
        call_llm: Optional[Callable[[str], str]] = None,
        threshold: int = DEFAULT_SUSPICION_THRESHOLD,
        lang: str = "tr",
        max_evidence: int = 8,
    ) -> None:
        self._call_llm = call_llm
        self._threshold = threshold
        self._lang = lang
        self._max_evidence = max_evidence

    def _suspicion(self, flags: list[dict]) -> int:
        weights = {"critical": 3, "high": 2, "medium": 1, "low": 1}
        return sum(weights.get(f.get("severity"), 1) for f in flags)

    def _build_prompt(self, bag: EvidenceBag) -> str:
        text = "\n".join(f"--- {r['engine']} ---\n{r['text']}" for r in bag.responses[: self._max_evidence])
        template = JUDGE_TEMPLATE_TR if self._lang == "tr" else JUDGE_TEMPLATE_EN
        return template.format(evidence_text=text, independence="her_maddede_bölüm(ler)" if self._lang == "tr" else "per_item")

    def judge(self, bag: EvidenceBag) -> JudgeVerdict:
        score = self._suspicion(bag.cross_flags)
        if score < self._threshold or self._call_llm is None:
            # Eşiği aşamadık veya çağrıcı yok → kural tabanlı fallback.
            if not bag.cross_flags:
                return JudgeVerdict("supported", "çelişki yok — kural tabanlı sonuç", False, 0.9)
            # Şüpheli flag'ler var ama LLM eşiği aşılmadı → insan onayına bırak.
            return JudgeVerdict(
                "disputed",
                f"{len(bag.cross_flags)} şüphe var; LLM eşiği {self._threshold} aşılmadı, manuel inceleme önerilir",
                False,
                0.5,
            )

        # LLM yargıcını çağır.
        prompt = self._build_prompt(bag)
        raw = self._call_llm(prompt)
        lowered = _norm_lower(raw)
        if any(w in lowered for w in _FABRICATED_MARKERS):
            verdict, reason = "fabricated", raw[:400]
        elif any(w in lowered for w in _DISPUTED_MARKERS):
            verdict, reason = "disputed", raw[:400]
        else:
            verdict, reason = "supported", raw[:400]
        return JudgeVerdict(verdict, reason, True, 0.85)


def default_llm_caller() -> Optional[Callable[[str], str]]:
    """Env'de api key varsa OpenAI-uyumlu client kurar; yoksa None (fallback)."""
    key = os.environ.get("GEOLENS_JUDGE_API_KEY") or os.environ.get("OPENAI_API_KEY")
    if not key:
        return None
    try:
        from openai import OpenAI  # type: ignore

        client = OpenAI(api_key=key)
        return lambda prompt: str(
            client.completions.create(model=os.environ.get("GEOLENS_JUDGE_MODEL", "gpt-4o-mini"), prompt=prompt)
            .choices[0].text
        )
    except Exception:
        return None


def build_judge(threshold: int = DEFAULT_SUSPICION_THRESHOLD, lang: str = "tr") -> HallucinationJudge:
    """Üretim yargıcı: env key varsa LLM kullanır, yoksa kural tabanlı fallback."""
    return HallucinationJudge(call_llm=default_llm_caller(), threshold=threshold, lang=lang)
