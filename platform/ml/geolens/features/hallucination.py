"""A2-4 · Cross-source hallüsinasyon tespiti + URL doğrulama.

Mevcut Go tarafı (internal/sentiment/engine.go) tek-cevap kural kurallarına dayanır
(T1–T4). Bu modül, 0421 A2-4'ün Python ayağı:
  - **cross-source**: aynı prompt'a birden çok motorun (perplexity/chatgpt/gemini)
    cevabını karşılaştırır; çelişen sayısal claim ve marka konumu tutarsızlıklarını
    işaretler (higher-recall go/LLM fallback).
  - **URL doğrulama**: citation URL'lerinin erişilebilirliğini HTTP HEAD isteğiyle
    dogrular (offline fallback: ağ yoksa 'unknown').

T1–T5 tip isimleri SCHEMA.md (hallucination.type) ile uyumludur.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Callable, Iterable

from geolens.features.entity_rules import extract_brands

# Sayısal claim regex: "900 milyon", "$2.5 milyar", "%30" vb.
# Öndeki '%' işareti de yakalanır ("%30") ve sayı sonrası birim seçilir.
_NUMBER_CLAIM = re.compile(
    r"(?:%\s*)?(?P<num>\d[\d.,]*)\s*(?P<unit>milyon|milyar|bin|million|billion|bn|mln|%|percent|₺|tl|usd|\$|eur)?",
    re.IGNORECASE,
)


URL_CHECKER: Callable[[str], bool] | None = None
"""URL doğrulama fonksiyonu (e2e'de enjekte edilebilir). None = çevrimdışı 'unknown'."""


@dataclass
class CrossClaim:
    claim: str
    response_id: str
    engine: str
    number: float


@dataclass
class HallucinationFlag:
    type: str            # T1..T5, 'none'
    severity: str        # low|medium|high|critical
    description: str
    confidence: float    # 0..1
    source: str          # 'cross-source' | 'url-check'
    engine: str = ""     # ilişkili motor (serving attribution; boş olabilir)


def _extract_numbers(text: str) -> list[tuple[str, float, str]]:
    """(ham claim, sayı, birim) seçimi — sayısal ifadeleri normalize eder."""
    out = []
    for m in _NUMBER_CLAIM.finditer(text):
        raw = m.group("num").replace(",", ".").replace(".", "", 1) if "," in m.group("num") else m.group("num")
        raw = raw.replace(" ", "").replace(",", ".")
        try:
            num = float(raw)
        except ValueError:
            continue
        unit = m.group("unit") or ""
        if unit == "" and m.group(0).lstrip().startswith("%"):
            unit = "%"
        out.append((m.group(0), num, unit))
    return out


def cross_source_check(responses: list[dict]) -> list[HallucinationFlag]:
    """Birden çok motor cevabı arasındaki çelişkileri tespit eder.

    responses: [{id, engine, text}] — aynı prompt'a verilen motor cevapları.
    Kurallar:
      - Aynı birim ile farklı sayı (örn. %30 vs %60) → T3 (fabrication)
      - Bir motor markayı anarken diğeri hiç anmayor → T1 (wrong factual/brand)
    """
    flags: list[HallucinationFlag] = []
    if len(responses) < 2:
        return flags

    engines = {r["id"]: r.get("engine", "") for r in responses}

    # Sayısal çelişki: aynı birimdeki sayıları eşleştir
    all_numbers: dict[str, list[tuple[str, float, str]]] = {}
    for resp in responses:
        all_numbers[resp["id"]] = _extract_numbers(resp["text"])

    ids = [r["id"] for r in responses]
    for i, rid in enumerate(ids):
        nums_i = all_numbers[rid]
        for other in ids[i + 1 :]:
            nums_j = all_numbers[other]
            for (claim_i, num_i, unit_i) in nums_i:
                for (claim_j, num_j, unit_j) in nums_j:
                    if unit_i and unit_j and unit_i.lower() == unit_j.lower() and abs(num_i - num_j) > 1e-9:
                        flags.append(
                            HallucinationFlag(
                                type="T3",
                                severity="high",
                                description=f"Çelişik sayısal claim: '{claim_i}' vs '{claim_j}' (motor {rid}×{other})",
                                confidence=0.7,
                                source="cross-source",
                                engine=engines.get(rid, ""),
                            )
                        )

    # Marka anılması tutarsızlığı: bir cevap markayı anıyor, diğeri anmayabiliyorsa
    brand_sets = {r["id"]: set(extract_brands(r["text"])) for r in responses}
    for rid, brands in brand_sets.items():
        for other in ids:
            if rid == other:
                continue
            if brands and not brand_sets[other]:
                flags.append(
                    HallucinationFlag(
                        type="T1",
                        severity="low",
                        description=f"Motor {rid} marka anıyor ama {other} anmıyor — marka hakimiyeti işareti",
                        confidence=0.4,
                        source="cross-source",
                        engine=engines.get(rid, ""),
                    )
                )

    return flags


def check_citation_urls(citations: Iterable[str], timeout: float = 5.0) -> list[HallucinationFlag]:
    """Citation URL'lerinin erişilebilirliğini HEAD isteğiyle kontrol eder.

    URL_CHECKER enjekte edilmemişse (çevrimdışı) 'unknown' döner, flag üretmez.
    """
    flags: list[HallucinationFlag] = []
    if URL_CHECKER is None:
        return flags

    for url in citations:
        ok = URL_CHECKER(url)
        if not ok:
            flags.append(
                HallucinationFlag(
                    type="T2",
                    severity="medium",
                    description=f"Citation URL erişilebilir değil: {url}",
                    confidence=0.8,
                    source="url-check",
                )
            )
    return flags


def default_url_checker(timeout: float = 5.0) -> Callable[[str], bool]:
    """Gerçek HTTP HEAD doğrulaması (ağ erişimi varsa)."""

    def check(url: str) -> bool:
        try:
            import urllib.request

            req = urllib.request.Request(url, method="HEAD")
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.status < 400
        except Exception:
            return False

    return check


def flag_to_record(flag: HallucinationFlag, engine: str, prompt_id: str) -> dict:
    """gold.jsonl hallucination alanına dönüştürür."""
    return {
        "prompt_id": prompt_id,
        "engine": engine,
        "type": flag.type,
        "severity": flag.severity,
        "description": flag.description,
        "confidence": flag.confidence,
        "source": flag.source,
    }
