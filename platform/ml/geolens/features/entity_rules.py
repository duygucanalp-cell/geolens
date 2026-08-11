"""A1-5 · Regex temelli entity/kınık çıkarımı (NER hybrid — regex ayağı).

Türkçe + İngilizce metinden:
  - marka (brand) / rakip (competitor)
  - ürün (product keywords)
  - sektör terimleri (sector)
  - teknoloji terimleri (technology)
  - para birimi / yüzde (money & percent)
  - tarih (date) mention'ları
çıkarır. Model ayağı (`ner/train_ner.py`) ile birleştirilir (A2-2).

Deterministik, bağımsız çalışabilir — Gold dataset'te entity etiketi karşılaştırması
ve A2-2 fark/koşullu fallback için temel oluşturur.
"""
from __future__ import annotations

import re

# --- Sözlük tabanlı (jenerik marka/rakip/ürün) ---
# Kanonik görünen ad -> norm anahtar (lower). Çıkarımda kanonik ad döndürülür.
BRAND_TERMS: dict[str, str] = {
    "MobiTel": "mobitel",
    "VekoCom": "vekocom",
    "FinBank": "finbank",
    "AktifKredi": "aktifkredi",
    "MarketGo": "marketgo",
    "SüperAlış": "süperalış",
    "SağlıkPlus": "sağlıkplus",
    "MediKlinik": "mediklinik",
    "TeknoStar": "teknostar",
    "PixelWare": "pixelware",
    "Turkcell": "turkcell",
    "Vodafone": "vodafone",
    "Türk Telekom": "türk telekom",
    "Türk Telekom Superonline": "türk telekom superonline",
    "Akbank": "akbank",
    "Garanti BBVA": "garanti bbva",
    "İş Bankası": "iş bankası",
    "Yapı Kredi": "yapı kredi",
    "Getir": "getir",
    "Trendyol": "trendyol",
    "Hepsiburada": "hepsiburada",
    "N11": "n11",
    "Amazon": "amazon",
    "Apple": "apple",
}
PRODUCT_TERMS = {
    "5g", "fiber", "gigafiber", "kredi kartı", "kredili", "mortgage",
    "bireysel emeklilik", "e-ticaret", "market", "pharmacy", "ilaç",
    "telefon", "akıllı telefon", "sensor", "bms", "battery", "pil",
}
SECTOR_TERMS = {
    "telekom", "telekomünikasyon", "telecom", "telecommunications",
    "bankacılık", "finans", "banking", "finance",
    "perakende", "perakendecilik", "retail", "e-ticaret", "ecommerce",
    "sağlık", "saglik", "health", "sağlık hizmetleri", "healthcare",
    "teknoloji", "technology", "bilişim", "data", "yazılım",
}
TECH_TERMS = {
    "yapay zeka", "artificial intelligence", "ai", "machine learning",
    "ml", "nesnelerin interneti", "iot", "5g", "fiber", "cloud", "bulut",
    "bms", "pilsan", "smart grid", "akıllı şebeke", "e-sim", "verta",
}

_TOKEN = r"[A-Za-zÀ-ÿ'\-0-9üöşçğıÜÖŞÇĞİ/.]*"
_TOKEN_RE = re.compile(r"[^\s,:;.!?()\"']+", re.UNICODE)

_DATE_EN = re.compile(
    r"\b(?:\d{1,2}[./-]\d{1,2}[./-]\d{2,4}|\d{4})\b"
    r"(?:\s+(?:points|growth|percent|%))?", re.IGNORECASE
)
_DATE_ISO = re.compile(r"\b(?:20\d{2}|19\d{2})\b")

_MONEY_TOKEN = r"(?:₺|tl|türk lirası|usd|\$|eur|€|try|dolar|lira)"
_MONEY_CURRENCY = rf"(?:{_MONEY_TOKEN})"
_MONEY_NUMBER = r"(?:\d[\d.,]*(?:\s*(?:milyar|milyon|bin|million|billion|bn|mln))?)"
_MONEY_RE = re.compile(
    rf"\b(?:{_MONEY_NUMBER}\s*{_MONEY_CURRENCY}|{_MONEY_CURRENCY}\s*{_MONEY_NUMBER})\b",
    re.IGNORECASE,
)
# "%30" ve "30%" ile "30 percent" desenleri (% öncesinde sözcük sınırı gerekmez)
_PERCENT_RE = re.compile(r"\b\d[\d.,]*\s*%|(?:^|\s)%\s*\d[\d.,]*\b|\b\d[\d.,]*\s*percent\b", re.IGNORECASE)


def _norm(tok: str) -> str:
    return tok.lower().strip(".,;:!?()\"'‑-")


def extract_brands(text: str, brand_allowlist: set[str] | None = None) -> list[str]:
    """Marka/rakip mention'ları (allowlist üzerinden, case-insensitive).

    Kanonik görünüm döndürür (örn. "turkcell" -> "Turkcell").
    """
    allow = {k: v for k, v in BRAND_TERMS.items()} if brand_allowlist is None else {
        k: v for k, v in BRAND_TERMS.items() if k in brand_allowlist or v in brand_allowlist
    }
    hits: set[str] = set()
    for token in _TOKEN_RE.findall(text):
        norm = _norm(token).replace("ı", "i")  # Türkçe 'ı' -> 'i' esnekliği
        for canon, key in allow.items():
            if norm == key or norm == key.replace("ü", "u").replace("ş", "s").replace("ö", "o").replace("ç", "c"):
                hits.add(canon)
    low = text.lower()
    # Çok sözcüklü markalar tümce aramasıyla
    for canon, key in allow.items():
        if len(key.split()) > 1 and (key in low or low.replace("ı", "i") == key):
            hits.add(canon)
    return sorted(hits, key=str.lower)


def extract_entities(text: str) -> dict[str, list[str]]:
    """Tüm varlık türlerini tek çağrıda döndürür."""
    low = text.lower()
    return {
        "brand": extract_brands(text),
        "sector": sorted({t for t in SECTOR_TERMS if t in low}),
        "product": sorted({t for t in PRODUCT_TERMS if t in low}),
        "technology": sorted({t for t in TECH_TERMS if t in low}),
        "money": _MONEY_RE.findall(text),
        "percent": _PERCENT_RE.findall(text),
        "date": _DATE_ISO.findall(text) + _DATE_EN.findall(text),
    }


def format_entities(entities: dict[str, list[str]]) -> dict[str, list[str]]:
    """Tekrarları ayıklar, sıralı, boş listeleri bırakır."""
    return {k: list(dict.fromkeys(v)) for k, v in entities.items()}
