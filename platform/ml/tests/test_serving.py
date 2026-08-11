"""Serving API testleri (pytest + httpx).

Python ortamı olmayan CI'da bu testler ml job'ı içinde çalışır (0421 A0-5).
"""
import pytest
from fastapi.testclient import TestClient

from geolens.serving.app import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def _clean_registry():
    import geolens.serving.app as app_module

    app_module.registry._models = {}
    yield


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_health_empty_models():
    resp = client.get("/health")
    assert resp.json()["models"] == []


def test_list_models():
    resp = client.get("/v1/models")
    assert resp.status_code == 200
    assert resp.json()["models"] == []


def test_predict_missing_model():
    resp = client.post("/v1/predict", json={"model": "bu-yok"})
    assert resp.status_code == 404
    assert "bulunamadı" in resp.json()["detail"]


def test_predict_missing_model_field():
    resp = client.post("/v1/predict", json={"text": "Acme"})
    assert resp.status_code == 422


def test_unknown_model_id():
    resp = client.post("/v1/predict", json={"model": "yok", "text": "Acme"})
    assert resp.status_code == 404


class _FakePromptModel:
    """ONNXModel.predict çıktı şemasını taklit eder (skl2onnx: output_label/output_probability)."""

    def __init__(self, model_id, label, probs):
        self.model_id = model_id
        self._label = label
        self._probs = probs

    def predict(self, payload):
        return {
            "model": self.model_id,
            "model_version": "1.0.0",
            "outputs": {
                "output_label": [[self._label]],
                "output_probability": [self._probs],
            },
        }


def test_prompt_classify_success():
    import geolens.serving.app as app_module

    app_module.registry._models = {
        "prompt_intent": _FakePromptModel("prompt_intent", "comparison", [0.05, 0.92, 0.03]),
        "prompt_topic": _FakePromptModel("prompt_topic", "brand", [0.88, 0.12]),
        "prompt_persona": _FakePromptModel("prompt_persona", "consumer", [0.71, 0.29]),
        "prompt_funnel": _FakePromptModel("prompt_funnel", "evaluation", [0.65, 0.35]),
    }
    resp = client.post("/v1/prompt/classify", json={"text": "Acme'nin en iyi rakibi kim?"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["intent"] == {"label": "comparison", "confidence": 0.92}
    assert body["topic"] == {"label": "brand", "confidence": 0.88}
    assert body["funnel"] == {"label": "evaluation", "confidence": 0.65}


def test_prompt_classify_model_missing():
    resp = client.post("/v1/prompt/classify", json={"text": "x"})
    assert resp.status_code == 404


def test_prompt_classify_missing_text():
    resp = client.post("/v1/prompt/classify", json={})
    assert resp.status_code == 422


def test_prompt_classify_dict_probability():
    """Gerçek skl2onnx çıktısı: seq(map(string,tensor(float))) — dict listesi.

    onnxruntime output_probability değerini [{"presence": 0.62, ...}] olarak
    döndürür; 2D sayısal dizi DEĞİLDİR (regresyon: TypeError float() not 'dict').
    """
    import geolens.serving.app as app_module

    class _DictProbsModel:
        def predict(self, payload):
            return {
                "model": "prompt_intent",
                "model_version": "1.0.0",
                "outputs": {
                    "output_label": ["comparison"],
                    "output_probability": [
                        {
                            "category": 0.06,
                            "comparison": 0.92,
                            "presence": 0.01,
                            "problem": 0.01,
                            "recommendation": 0.0,
                        }
                    ],
                },
            }

    app_module.registry._models = {
        "prompt_intent": _DictProbsModel(),
        "prompt_topic": _DictProbsModel(),
        "prompt_persona": _DictProbsModel(),
        "prompt_funnel": _DictProbsModel(),
    }
    resp = client.post("/v1/prompt/classify", json={"text": "Acme'nin en iyi rakibi kim?"})
    assert resp.status_code == 200
    body = resp.json()
    # label'ı çıktı_dict'inde bulunan değerle eşleşen confidence seçilir
    assert body["intent"]["label"] == "comparison"
    assert body["intent"]["confidence"] == 0.92


def test_prompt_classify_dict_label_missing_from_row():
    """output_label dict'te bulunamayan bir sınıf verirse argmax ile uzlaştırılır.

    Yanıltıcı 'label A + confidence B' eşleşmesi üretmemek için confidence
    en yüksek olasılıklı sınıftan alınır (ve label boşsa o sınıfa eşitlenir).
    """
    import geolens.serving.app as app_module

    class _MismatchedLabelModel:
        def predict(self, payload):
            return {
                "model": "prompt_intent",
                "model_version": "1.0.0",
                "outputs": {
                    # label dict'te yok ("presence" yerine "unknown")
                    "output_label": ["unknown"],
                    "output_probability": [
                        {"category": 0.1, "comparison": 0.85, "presence": 0.05}
                    ],
                },
            }

    app_module.registry._models = {
        "prompt_intent": _MismatchedLabelModel(),
        "prompt_topic": _MismatchedLabelModel(),
        "prompt_persona": _MismatchedLabelModel(),
        "prompt_funnel": _MismatchedLabelModel(),
    }
    resp = client.post("/v1/prompt/classify", json={"text": "test"})
    assert resp.status_code == 200
    # label dict'te yok → confidence en yüksek sınıftan (comparison 0.85)
    assert resp.json()["intent"]["confidence"] == 0.85


def test_hallucination_detect_contradiction():
    resp = client.post(
        "/v1/hallucination/detect",
        json={
            "responses": [
                {"id": "r1", "engine": "chatgpt", "text": "MobiTel 2023'te %30 büyüme bildirdi."},
                {"id": "r2", "engine": "gemini", "text": "MobiTel 2023'te %60 büyüme iddia ediyor."},
            ]
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert any(f["type"] == "T3" and f["engine"] == "chatgpt" and f["confidence"] >= 0.7 for f in body["findings"])


def test_hallucination_detect_single_response_empty():
    resp = client.post(
        "/v1/hallucination/detect", json={"responses": [{"id": "r1", "engine": "p", "text": "tek cevap"}]}
    )
    assert resp.status_code == 200
    assert resp.json()["findings"] == []


def test_hallucination_detect_missing_field():
    resp = client.post("/v1/hallucination/detect", json={})
    assert resp.status_code == 200
    assert resp.json()["findings"] == []


def test_hallucination_detect_no_contradiction():
    resp = client.post(
        "/v1/hallucination/detect",
        json={
            "responses": [
                {"id": "r1", "engine": "chatgpt", "text": "Şirket 5 milyon kullanıcıya sahip."},
                {"id": "r2", "engine": "gemini", "text": "Şirket 5 milyon kullanıcıya sahiptir."},
            ]
        },
    )
    assert resp.status_code == 200
    assert resp.json()["findings"] == []
