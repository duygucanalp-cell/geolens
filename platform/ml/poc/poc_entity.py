"""A4-1 · Entity Extraction PoC (İP-08 #2).

Gerçek üretilen 7 entity tipi (A1-5 extract_entities): brand, sector,
product, technology, money, percent, date. Belirsizlik çözümü: regex bulamazsa
varsayılan 'none'. Hibrit NER pipeline'ı (geolens.features.ner) kural ayağıyla
çalışır; her örnek yalnızca tek tip içerir (temiz gold).
"""

from __future__ import annotations

import sys
from pathlib import Path

from poc._bench import POCResult, run_classification_v2

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT))

from geolens.features.ner import HybridEntityRecognizer, summarize  # noqa: E402

rec = HybridEntityRecognizer()

# extract_entities'in ürettiği tipler (7): brand, sector, product, technology,
# money, percent, date. Kalite öncelik sırası yalnızca birden çok tip yakalandığında.
_PRIORITY = {"brand": 7, "sector": 6, "money": 5, "percent": 4, "product": 3, "technology": 2, "date": 1}


def top_entity_type(text: str) -> str:
    """Metnin ilk 'önemli' entity tipini döner; yoksa 'none'."""
    ents = rec.recognize(text)
    if not ents:
        return "none"
    best = max(ents, key=lambda e: _PRIORITY.get(e.type, 0))
    return best.type


def main() -> int:
    samples: list[tuple[str, str, callable]] = [
        ("Turkcell ve Vodafone karşılaştırılıyor.", "brand", top_entity_type),
        ("Bankacılık sektöründe yeni düzenlemeler açıklandı.", "sector", top_entity_type),
        ("Şirket 2.5 milyar TL gelir açıkladı.", "money", top_entity_type),
        ("Şirket %30 oranında büyüme kaydetti.", "percent", top_entity_type),
        ("Yeni akıllı telefon lansmanı ertelendi.", "product", top_entity_type),
        ("Yapay zeka alanında araştırmalar artıyor.", "technology", top_entity_type),
        ("2025 yılında lansman planlanıyor.", "date", top_entity_type),
        ("Merhaba dünya, herhangi bir şey.", "none", top_entity_type),
    ]
    res: POCResult = run_classification_v2("entity-type", samples)
    print(res.render())
    print("örnek summarize:", summarize(rec.recognize(samples[0][0])))
    return 0 if res.ok() else 1


if __name__ == "__main__":
    sys.exit(main())
