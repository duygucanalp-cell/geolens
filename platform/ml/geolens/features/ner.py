"""A2-2 · NER hibrit pipeline (regex + model + LLM fallback).

0421 A2-2 hedefi: çok dilli NER (>%85 F1, 7+ entity tipi) + hibrit fallback.
Bu dosya pipeline'ı tanımlar:
  - **leg 1 (kural)**: `geolens.features.entity_rules` — regex + sözlük
  - **leg 2 (model)**: opsiyonel ONNX model (çok dilli NER) — varsa kullanılır
  - **leg 3 (LLM fallback)**: kural+model yetmediğinde LLM'e düşülebilir

Modeli ortada üretmemek için leg2 pür opsiyoneldir (A1-5 regex ayağı şu an
ana yol; model ayağı gold entity span'leri toplandığında eğitilir — plan A2-2).
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Callable, Optional

from geolens.features.entity_rules import extract_entities, format_entities


@dataclass
class Entity:
    value: str
    type: str
    start: int
    end: int
    source: str  # rules | model | llm


class HybridEntityRecognizer:
    """Önce regex, opsiyonel model, sonra LLM fallback — zincirlenir."""

    def __init__(
        self,
        model_predict: Optional[Callable[[str], list[Entity]]] = None,
        llm_extract: Optional[Callable[[str], list[Entity]]] = None,
    ) -> None:
        self._model_predict = model_predict
        self._llm_extract = llm_extract

    def _rule_positions(self, text: str, value: str) -> tuple[int, int]:
        """Değerin metindeki ilk konumu (regex ile case-insensitive)."""
        for m in re.finditer(re.escape(value), text, re.IGNORECASE):
            return m.start(), m.end()
        return -1, -1

    def recognize(self, text: str) -> list[Entity]:
        entities: list[Entity] = []
        seen: set[tuple[str, str]] = set()  # (value_lower, type)

        raw = extract_entities(text)
        for etype, values in raw.items():
            for v in values:
                start, end = self._rule_positions(text, v)
                entities.append(Entity(v, etype, start, end, "rules"))
                seen.add((v.lower(), etype))

        # Model ayağı (opsiyonel) — ek entity'ler regex'ten gelmiyorsa
        if self._model_predict is not None:
            for e in self._model_predict(text):
                if (e.value.lower(), e.type) not in seen:
                    entities.append(e)
                    seen.add((e.value.lower(), e.type))

        # LLM fallback (opsiyonel) — hâlâ entity yoksa
        if self._llm_extract is not None and not entities:
            entities.extend(self._llm_extract(text))

        # Konum pozitifse sırala
        entities.sort(key=lambda e: (e.start if e.start >= 0 else float("inf"), e.end))
        return entities


def summarize(entities: list[Entity]) -> dict[str, list[str]]:
    """Detekt sonucunu {type: [değer]} sözlüğüne indirger (gold şema uyumlu)."""
    out: dict[str, list[str]] = {}
    for e in entities:
        out.setdefault(e.type, []).append(e.value)
    return format_entities(out)
