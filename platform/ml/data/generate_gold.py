"""A1-2 · Gold dataset v1 üreticisi (0421).

500+ referans kayıt üretir (250 TR / 250 EN). Her kayıt bir prompt için AI motor
cevabını simüle eder ve mention/citation/entity/recommendation/hallucination
etiketlerini taşır. Çıktı: data/gold.jsonl (JSON Lines).

Not: Bu üretici *sentetik* bir başlangıç verisi üretir — şema + etiketlenmiş
kayıt yapısı ve train/test bölmesi için. Gerçek AI motor cevaplarıyla doldurulup
elle etiketleme (IAA >%90) sonrası üretime alınacaktır (0420 İP-02).
"""
from __future__ import annotations

import argparse
import itertools
import json
import os

SEED = 42

# Gold kayıtlar prompt havuzundan türetilir: marka {brand} cevabı, rakip
# vasıtası mention'ı ve citation etiketi. Her prompt için deterministik bir
# senaryo seçilir: gündem senaryoları mention var/yok, citation direct/attribution,
# sentiment pozitif/nötr/negatif arasında değişir.

# Senaryo şablonları (TR):
_TR_RESPONSES = [
    ("{{brand}} pazarda güçlü bir konuma sahiptir ve ürünleri yaygın olarak takdir edilmektedir.",
     "positive", ["direct"], "none"),
    ("{{brand}} hakkında bilinenler sınırlıdır; sektördeki birçok oyuncudan biridir.",
     "neutral", ["none"], "none"),
    ("{{brand}} bazı müşteri şikayetleriyle anılmaktadır ve hizmet kalitesi sorgulanmaktadır.",
     "negative", ["attribution"], "none"),
    ("{{brand}} en son raporlarında 2023 yılında %30 büyüme iddia etmektedir ancak bu bilgi doğrulanmamıştır.",
     "positive", ["direct"], "T6"),
    ("{{brand}} kuruluş tarihi hakkında kaynaklar çelişmektedir; bazı kaynaklar 2010, bazıları 2012 demektedir.",
     "neutral", ["attribute"], "T5"),
    ("{{brand}} rakiplere göre daha yenilikçi ürünler sunmaktadır ve tavsiye edilir.",
     "positive", ["direct", "attribution"], "none"),
]
_EN_RESPONSES = [
    ("{{brand}} holds a strong position in the market and its products are widely appreciated.",
     "positive", ["direct"], "none"),
    ("Not much is known about {{brand}}; it is just one of many players in the sector.",
     "neutral", ["none"], "none"),
    ("{{brand}} is mentioned with customer complaints and its service quality is being questioned.",
     "negative", ["attribution"], "none"),
    ("{{brand}} claims 30% growth in 2023 per recent reports, yet this figure is unverified.",
     "positive", ["direct"], "T6"),
    ("Sources conflict about {{brand}}'s founding year; some say 2010, others 2012.",
     "neutral", ["attribution"], "T5"),
    ("{{brand}} offers more innovative products than its rivals and is recommended.",
     "positive", ["direct", "attribution"], "none"),
]


def _engine_for(n: int) -> str:
    return ["perplexity", "chatgpt", "gemini"][n % 3]


def generate(prompts_path: str) -> list[dict]:
    with open(prompts_path, encoding="utf-8") as fh:
        prompts = [json.loads(line) for line in fh if line.strip()]

    gold: list[dict] = []
    # Yukarıdaki üreticiyle uyumlu brand isimleri (generate_prompts ile aynı sözlük).
    brands = {
        "telekom": ("MobiTel", "VekoCom"),
        "finans": ("FinBank", "AktifKredi"),
        "perakende": ("MarketGo", "SüperAlış"),
        "saglik": ("SağlıkPlus", "MediKlinik"),
        "teknoloji": ("TeknoStar", "PixelWare"),
    }

    # prompt başına dönüşümlü senaryo almak için sıralı sayaç (deterministik).
    counter = itertools.count()
    for p in prompts:
        n = next(counter)
        brand, competitor = brands[p["sector"]]
        pool = _TR_RESPONSES if p["lang"] == "tr" else _EN_RESPONSES
        text, sentiment, citation_types, htype = pool[n % len(pool)]
        response = text.replace("{{brand}}", brand)

        mentions = []
        if sentiment != "neutral" or n % 4 != 0:
            mentions.append({"text": brand, "type": "brand", "sentiment": sentiment})
        if n % 3 == 0:
            mentions.append({"text": competitor, "type": "competitor", "sentiment": "neutral"})

        citations = [
            {"url": f"https://{brand.lower().replace('ş', 's').replace('ü', 'u').replace('ı', 'i').replace('ö', 'o').replace('ç', 'c')}.example.com/hakkimizda", "type": t}
            for t in citation_types
            if t != "none"
        ]
        entities = [{"value": brand, "type": "brand"}]
        if competitor in mentions or "rakiplere" in response:
            entities.append({"value": competitor, "type": "competitor"})

        gold.append(
            {
                "id": f"gold_{n + 1:04d}",
                "lang": p["lang"],
                "prompt_id": p["id"],
                "prompt": p["text"],
                "engine": _engine_for(n),
                "response_text": response,
                "expected_summary": f"{brand} sektör durumu özeti",
                "mentions": mentions,
                "citations": citations,
                "entities": entities,
                "recommendation": "tavsiye edilir" in response or "is recommended" in response,
                "hallucination": {"type": htype, "severity": None if htype == "none" else "medium"},
            }
        )
    return gold


def main() -> None:
    parser = argparse.ArgumentParser(description="Gold dataset üret (A1-2)")
    data_dir = os.path.dirname(__file__)
    parser.add_argument("--prompts", default=os.path.join(data_dir, "prompts.jsonl"))
    parser.add_argument("--out", default=os.path.join(data_dir, "gold.jsonl"))
    args = parser.parse_args()

    gold = generate(args.prompts)
    with open(args.out, "w", encoding="utf-8") as fh:
        for g in gold:
            fh.write(json.dumps(g, ensure_ascii=False) + "\n")
    print(f"{len(gold)} gold kayıt yazıldı: {args.out}")


if __name__ == "__main__":
    main()
