"""A4-1 · Prompt Classification PoC (İP-08 #4).

intent / topic / persona / funnel sınıflandırma. Eğitilmiş ONNX + joblib modeli
varsa onu kullanır; yoksa kural tabanlı fallback (deterministik).
"""

from __future__ import annotations

import sys
from pathlib import Path

from poc._bench import run_classification_v2

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT))

_MODEL_DIR = REPO_ROOT / "models" / "prompt_classifier"


def _load_model():
    try:
        from geolens.prompt_classifier.serve import PromptClassifier
        return PromptClassifier(str(_MODEL_DIR))
    except Exception:
        return None


_classifier = _load_model()


def classify_prompt_intent(text: str) -> str:
    if _classifier is not None:
        try:
            return _classifier.predict(text)["intent"]
        except Exception:
            pass
    # Kural tabanlı fallback
    t = text.lower()
    if "bilgi ver" in t or "nedir" in t:
        return "information"
    if "öner" in t or "tavsiye" in t or "hangisi" in t:
        return "recommendation"
    if "karşılaştır" in t or "kıyasla" in t:
        return "comparison"
    if "satın al" in t or "fiyat" in t or "paket" in t:
        return "purchase"
    return "information"


def classify_prompt_topic(text: str) -> str:
    t = text.lower()
    if "tarife" in t or "fiyat" in t:
        return "pricing"
    if "ürün" in t or "telefon" in t:
        return "product"
    if "şirket" in t or "hakkında" in t:
        return "company"
    return "general"


def main() -> int:
    mode = "model" if _classifier is not None else "fallback"
    samples: list[tuple[str, str, callable]] = [
        ("Smartfon satın almak istiyorum, hangi model önerirsiniz?", "recommendation", classify_prompt_intent),
        ("MobiTel hakkında bilgi verir misiniz?", "information", classify_prompt_intent),
        ("Turkcell ile VekoCom tarifelerini karşılaştır.", "comparison", classify_prompt_intent),
        ("En uygun internet paketi nedir, tavsiye eder misiniz?", "recommendation", classify_prompt_intent),
        ("Acme'nin telefon fiyat listesini göster.", "purchase", classify_prompt_intent),
    ]
    res = run_classification_v2("prompt-intent", samples)
    print(res.render())
    print(f"mode: {mode}")
    if _classifier is not None:
        for text in samples[:3]:
            print("öneri:", _classifier.predict(text[0]))
    return 0 if res.ok() else 1


if __name__ == "__main__":
    sys.exit(main())
