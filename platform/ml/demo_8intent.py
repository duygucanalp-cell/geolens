"""Faz B/D demo — 8-intent serving + classify (0421-8INTENT, model v2.0.0).

Gerçek ONNX/joblib artefaktlarıyla serving'i ayağa kaldırır ve birkaç örnek
prompt'ta intent/persona/funnel sınıflandırmasını gösterir.

Çalıştırma:
  cd platform/ml && .venv/Scripts/python.exe demo_8intent.py
"""
from __future__ import annotations

import os

from fastapi.testclient import TestClient

from geolens.serving.app import app
from geolens.serving.registry import MODEL_ID_TO_VERSION

MODELS = (
    "prompt_intent=prompt_classifier/prompt_intent.onnx,"
    "prompt_topic=prompt_classifier/prompt_topic.onnx,"
    "prompt_persona=prompt_classifier/prompt_persona.onnx,"
    "prompt_funnel=prompt_classifier/prompt_funnel.onnx"
)

# Her intent'ten bir örnek (odev01 eğitim dağılımı içi ifadeler)
DEMO_PROMPTS = [
    ("information", "Acme markası hakkında ne biliyorsun? Sektördeki konumu, yenilikleri ve rakiplerine göre farklılaştığı noktaları kaynak göstererek anlat."),
    ("comparison", "Acmenin en iyi rakibi kim? Karşılaştırma yap."),
    ("opinion", "Sence Acme bu yıl nasıl bir yıl geçirdi? Görüşünü paylaşır mısın?"),
    ("recommendation", "Acme ürününü bana önerir misin?"),
    ("purchase", "Acme'yi seçmeden önce hangi detayları kontrol etmem gerekir? Karar vermeden önce bilmem gereken en önemli şeyler neler?"),
    ("complaint", "Acme hakkında insanlar en çok neden şikayet ediyor? En sık duyulan sorunlar hangileri?"),
]


def main() -> None:
    os.environ.setdefault("ML_MODEL_DIR", "models")
    os.environ["MODELS"] = MODELS

    client = TestClient(app)
    with client:
        health = client.get("/health").json()
        print("=== Faz B/D demo — 8-intent model serving (v2.0.0) ===")
        print(f"yüklü modeller: {health['models']}")
        print("sürümler: " + ", ".join(f"{k}={v}" for k, v in MODEL_ID_TO_VERSION.items()))
        print()

        # /v1/predict ile model_version doğrulaması
        r = client.post("/v1/predict", json={"model": "prompt_intent", "lang": "tr", "text": DEMO_PROMPTS[0][1]})
        pred = r.json()
        print(f"/v1/predict  → model_version={pred.get('model_version')}  "
              f"(intent={pred.get('label')})")
        print()

        # /v1/prompt/classify — intent/topic/persona/funnel
        print(f"{'örnek':<14}{'intent':<16}{'persona':<12}{'funnel':<14}güven")
        print("-" * 62)
        for expected, text in DEMO_PROMPTS:
            r = client.post("/v1/prompt/classify", json={"text": text})
            data = r.json()
            intent = data["intent"]
            ok = "✓" if intent["label"] == expected else f"✗ (beklenen: {expected})"
            print(
                f"{expected:<14}{intent['label']:<16}"
                f"{data['persona']['label']:<12}{data['funnel']['label']:<14}"
                f"{intent['confidence']:.3f}  {ok}"
            )


if __name__ == "__main__":
    main()
