"""A1-1 · Prompt taksonomisi üreticisi (0421).

5 sektör × 5 intent × 5 topic × 4 persona × 2 funnel = 1000 etiketli prompt
üretir, TR/EN yarı yarıya dağıtır. Çıktı: data/prompts.jsonl (JSON Lines).

Şablon yaklaşımı: her kombinasyon intent+topic kalıbına sektör/brand/rakip
terimleri gömülerek oluşturulur. Deterministik (seed sabit) — CI'da "eskisinden
iyi mi?" karşılaştırması için tekrarlanabilir olmalıdır (0420 İP-01).
"""
from __future__ import annotations

import argparse
import json
import os

SEED = 42

INTENTS = ["presence", "comparison", "recommendation", "category", "problem"]
TOPICS = ["product", "service", "brand", "sector", "technology"]
PERSONAS = ["consumer", "expert", "journalist", "investor"]
FUNNELS = ["awareness", "decision"]
SECTORS = ["telekom", "finans", "perakende", "saglik", "teknoloji"]

# Sektör başına marka/ürün örnekleri (eğitim verisi yalnızca yapısal; gerçek
# markalar yerine jenerik isimler kullanılır — Gold dataset üretiminde elle
# etiketlenen gerçek verilerle değiştirilir).
BRANDS = {
    "telekom": ("MobiTel", "VekoCom"),
    "finans": ("FinBank", "AktifKredi"),
    "perakende": ("MarketGo", "SüperAlış"),
    "saglik": ("SağlıkPlus", "MediKlinik"),
    "teknoloji": ("TeknoStar", "PixelWare"),
}

# Intent + topic kalıbı -> prompt iskeleti ({brand}, {competitor}, {rival}sız).
# Intro ve kuyruk parçaları dil bağımsız iskelet; fiil/soru kalıbı dille eşlenir.
_TR_VERBS = {
    ("presence", "product"): "hakkında bilgi ver",
    ("presence", "service"): "ne kadar tanınıyor",
    ("presence", "brand"): "kimdir, ne yapar",
    ("presence", "sector"): "sektördeki konumu nedir",
    ("presence", "technology"): "hangi teknolojileri kullanıyor",
    ("comparison", "product"): "ürünleriyle rakiplerini karşılaştır",
    ("comparison", "service"): "hizmetlerini rakiple karşılaştır",
    ("comparison", "brand"): "rakipleriyle arasındaki farkı anlat",
    ("comparison", "sector"): "sektörde rakiplere göre yeri nedir",
    ("comparison", "technology"): "teknoloji yatırımlarını rakipleriyle kıyasla",
    ("recommendation", "product"): "ürününü önerir misin",
    ("recommendation", "service"): "hizmetini tavsiye eder misin",
    ("recommendation", "brand"): "markasını seçer misin",
    ("recommendation", "sector"): "bu sektörde ilk tercihin hangisi",
    ("recommendation", "technology"): "teknolojisi için ne önerirsin",
    ("category", "product"): "ürün kategorisinde nasıl sıralanır",
    ("category", "service"): "hangi hizmet kategorisine girer",
    ("category", "brand"): "hangi marka grubuna aittir",
    ("category", "sector"): "hangi sektör segmentinde yer alır",
    ("category", "technology"): "hangi teknoloji sınıfındadır",
    ("problem", "product"): "ürününde bilinen sorunlar neler",
    ("problem", "service"): "hizmet şikayetleri var mı",
    ("problem", "brand"): "en sık eleştirilen yönü nedir",
    ("problem", "sector"): "sektöründe karşılaştığı zorluklar neler",
    ("problem", "technology"): "teknoloji açığı veya riski var mı",
}
_EN_VERBS = {
    ("presence", "product"): "give information about",
    ("presence", "service"): "how well-known is",
    ("presence", "brand"): "who are they and what do they do",
    ("presence", "sector"): "what is their position in the sector",
    ("presence", "technology"): "which technologies do they use",
    ("comparison", "product"): "compare their products with competitors",
    ("comparison", "service"): "compare their services with the rival",
    ("comparison", "brand"): "explain how they differ from competitors",
    ("comparison", "sector"): "how do they rank versus rivals in the sector",
    ("comparison", "technology"): "compare their tech investments with rivals",
    ("recommendation", "product"): "would you recommend their product",
    ("recommendation", "service"): "would you recommend their service",
    ("recommendation", "brand"): "would you pick their brand",
    ("recommendation", "sector"): "which one is your first choice in this sector",
    ("recommendation", "technology"): "what do you suggest about their technology",
    ("category", "product"): "how do they rank in their product category",
    ("category", "service"): "which service category do they belong to",
    ("category", "brand"): "which brand group do they belong to",
    ("category", "sector"): "which sector segment do they sit in",
    ("category", "technology"): "which technology class are they in",
    ("problem", "product"): "are there known issues with their product",
    ("problem", "service"): "are there service complaints",
    ("problem", "brand"): "what is their most criticized aspect",
    ("problem", "sector"): "what challenges does the sector face",
    ("problem", "technology"): "do they have technology gaps or risks",
}

# Persona tonu -> prompt sarmalayıcı (iki dil).
_PERSONA_TR = {
    "consumer": "Bir müşteri olarak soruyorum: {q}",
    "expert": "Sektör uzmanı perspektifiyle değerlendirin: {q}",
    "journalist": "Haber için doğrulanabilir bilgi istiyorum: {q}",
    "investor": "Yatırım kararı için analiz edin: {q}",
}
_PERSONA_EN = {
    "consumer": "As a customer, I ask: {q}",
    "expert": "Evaluate from an industry expert perspective: {q}",
    "journalist": "I need verifiable information for a story: {q}",
    "investor": "Analyze this for an investment decision: {q}",
}

# Funnel tonu -> ek talep.
_FUNNEL_TR = {
    "awareness": "",
    "decision": " ve kısa gerekçe ver",
}
_FUNNEL_EN = {
    "awareness": "",
    "decision": " and give a short justification",
}


def render(lang: str, sector: str, intent: str, topic: str, persona: str, funnel: str) -> str:
    brand, competitor = BRANDS[sector]
    verbs = _TR_VERBS if lang == "tr" else _EN_VERBS
    q = verbs[(intent, topic)].format(brand=brand, competitor=competitor)
    q = f"{brand} {q}{_FUNNEL_TR[funnel] if lang == 'tr' else _FUNNEL_EN[funnel]}"
    personas = _PERSONA_TR if lang == "tr" else _PERSONA_EN
    return personas[persona].format(q=q)


def generate() -> list[dict]:
    prompts: list[dict] = []
    idx = 0
    # Deterministik sıra; dil yarı yarıya dengelenir (ilk yarı tr, ikinci yarı en).
    combos = [
        (s, i, t, p, f)
        for s in SECTORS
        for i in INTENTS
        for t in TOPICS
        for p in PERSONAS
        for f in FUNNELS
    ]
    total = len(combos)  # 1000
    for n, (sector, intent, topic, persona, funnel) in enumerate(combos):
        lang = "tr" if n < total // 2 else "en"
        idx += 1
        prompts.append(
            {
                "id": f"prompt_{idx:04d}",
                "lang": lang,
                "text": render(lang, sector, intent, topic, persona, funnel),
                "intent": intent,
                "topic": topic,
                "persona": persona,
                "funnel": funnel,
                "sector": sector,
            }
        )
    return prompts


def main() -> None:
    parser = argparse.ArgumentParser(description="1000 prompt üret (A1-1)")
    parser.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "prompts.jsonl"))
    args = parser.parse_args()

    prompts = generate()
    with open(args.out, "w", encoding="utf-8") as fh:
        for p in prompts:
            fh.write(json.dumps(p, ensure_ascii=False) + "\n")
    print(f"{len(prompts)} prompt yazıldı: {args.out}")


if __name__ == "__main__":
    main()
