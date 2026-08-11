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
