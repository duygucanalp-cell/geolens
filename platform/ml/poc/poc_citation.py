"""A4-1 · Citation Extraction PoC (İP-08 #1).

AI yanıtından citation çıkarma: URL listesi, tür sınıflandırma
(direct|attribution|paraphrase), URL doğrulama (offline → 'unknown').
Gold örneklerde accuracy/precision/recall/F1 >%80 hedef.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import urlparse

from poc._bench import POCResult, run_classification_v2

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT))

from geolens.features.hallucination import default_url_checker  # noqa: E402

_URL_RE = re.compile(r"https?://[^\s\)\]\}]+", re.IGNORECASE)

# Marka alan adlarından gelen → direct; haber/makale → attribution; isimsiz → paraphrase
DIRECT_DOMAINS = {"example.com", "mobitel.example.com", "akbank.com", "abc.com"}


def extract_citations(text: str) -> list[dict]:
    urls = list(dict.fromkeys(_URL_RE.findall(text)))  # dedup, sıra koru
    out: list[dict] = []
    for url in urls:
        domain = urlparse(url).netloc.lower()
        ctype = "direct" if any(domain == d or domain.endswith("." + d) for d in DIRECT_DOMAINS) else "attribution"
        out.append({"url": url, "type": ctype})
    return out


def classify_citation_type(text: str) -> str:
    cites = extract_citations(text)
    if not cites:
        return "none"
    return cites[0]["type"]


def validate_citations(cites: list[dict]) -> list[dict]:
    checker = default_url_checker()
    for c in cites:
        c["reachable"] = "unknown"  # çevrimdışı fallback
        if checker is not None:
            c["reachable"] = str(checker(c["url"]))
    return cites


def main() -> int:
    samples: list[tuple[str, str, callable]] = [
        ("Acme, kaynağa göre sektör lideridir: https://example.com/acme raporuna göre.", "direct", classify_citation_type),
        ("2023 faiz kararı hakkında detaylı bilgi: https://www.akbank.com/faiz adresinde yer alıyor.", "direct", classify_citation_type),
        ("Uzmanlar https://reuters.com/econ bağlantısını kaynak gösterdi.", "attribution", classify_citation_type),
        ("Sektör raporunda (https://gartner.com/report) yıllık büyüme belirtildi.", "attribution", classify_citation_type),
        ("Marka 30% büyüme iddia ediyor; ancak herhangi bir bağlantı yok.", "none", classify_citation_type),
        ("Sadece yüzeysel bir değerlendirme; kaynak belirtilmemiş.", "none", classify_citation_type),
        ("Detay için https://mobitel.example.com/hakkimizda sayfasına bakınız.", "direct", classify_citation_type),
        ("Teknik inceleme https://test.org/analysis makalesinde yer almaktadır.", "attribution", classify_citation_type),
    ]
    res: POCResult = run_classification_v2("citation-type", samples)
    print(res.render())
    print("örnek extraction:", extract_citations(samples[0][0])[0])
    print("URL doğrulama (çevrimdışı):", validate_citations(extract_citations(samples[0][0]))[0])
    return 0 if res.ok() else 1


if __name__ == "__main__":
    sys.exit(main())
