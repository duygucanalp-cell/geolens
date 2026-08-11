"""A4-1 · Recommendation Detection PoC (İP-08 #3).

"öneririm / tavsiye ederim / alternatif / bunun yerine" kalıplarını tespit eder
ve öneri gücünü skorlar (1-10). Türkçe + İngilizce destekli regex.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from poc._bench import POCResult, run_classification_v2

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT))

# Öneri kalıpları — TR + EN. Güç: bağlam sözcüklerine göre
_STRONG = re.compile(
    r"(?i)\b(kesinlikle|mutlaka|en iyi seçim|tavsiye ederim|öneririm|şiddetle öneririm|"
    r"strongly recommend|highly recommend|best choice|i recommend)\b"
)
_WEAK = re.compile(
    r"(?i)\b(alternatif|bunun yerine|değerlendirebilirsiniz|düşünebilirsiniz|"
    r"alternative|consider|recommend|suggest|maybe try)\b"
)
# "check out" is tricky; keep explicit set. Ek: "önerilir"
_PASSIVE = re.compile(r"(?i)\b(önerilir|önerebilir|you might like)\b")

# Sahte/öneri-olmayan geri çekilme kalıpları (NG önleme: "en iyi" → "en iyi değil")
_NEGATION = re.compile(r"(?i)\b(değil|not|yok|no|ama|however|ilk değil)\b")


def detect_recommendation(text: str) -> tuple[bool, float]:
    """(öneri var mı, güç 1-10)."""
    strong = bool(_STRONG.search(text))
    weak = bool(_WEAK.search(text))
    passive = bool(_PASSIVE.search(text))
    # NG10: olumsuzluk varsa öneri olarak kabul etme (güç düşür)
    negated = bool(_NEGATION.search(text))
    if negated:
        return False, 0.0
    if strong:
        return True, 9.0
    if weak:
        return True, 6.0
    if passive:
        return True, 4.0
    return False, 0.0


def rec_presence(text: str) -> str:
    yes, _score = detect_recommendation(text)
    return "yes" if yes else "no"


def main() -> int:
    samples: list[tuple[str, str, callable]] = [
        ("Kesinlikle MobiTel'in premium planını tavsiye ederim.", "yes", rec_presence),
        ("Bu konuda VekoCom alternatif olarak değerlendirilebilir.", "yes", rec_presence),
        ("Turkcell en iyi seçim olarak öne çıkıyor.", "yes", rec_presence),
        ("Marka yalnızca sektör verilerini paylaşıyor, öneri yok.", "no", rec_presence),
        ("MobiTel en iyi sağlayıcı DEĞİL.", "no", rec_presence),
        ("I strongly recommend checking out Acme's pricing page.", "yes", rec_presence),
        ("The brand is one of the leading companies in the sector.", "no", rec_presence),  # NG değil
        ("Bu hızlı bir değerlendirme, tavsiye içermez.", "no", rec_presence),
    ]
    res: POCResult = run_classification_v2("recommendation", samples)
    print(res.render())
    for text in samples[:3]:
        print(f"güç({text[0][:40]}…):", detect_recommendation(text[0]))
    return 0 if res.ok() else 1


if __name__ == "__main__":
    sys.exit(main())
