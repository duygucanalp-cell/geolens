"""AI model serving API'si (FastAPI + ONNX Runtime).

Go backend'den POST /v1/predict ile çağrılır (bkz. platform/internal/ml/client.go).
Model kayıtlı değilse 404, inference hatasında 500 döner.
"""
from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from geolens.features.hallucination import cross_source_check
from geolens.serving.registry import ModelRegistry

logger = logging.getLogger(__name__)

registry = ModelRegistry()
_model_dir = os.getenv("ML_MODEL_DIR", os.path.join(os.getcwd(), "models"))
_default_model = os.getenv("ML_DEFAULT_MODEL", "")


def _parse_models(spec: str) -> list[tuple[str, str]]:
    """MODELS env'ini ayrıştırır.

    Her öğe iki biçimde olabilir:
      - "id"          → <ML_MODEL_DIR>/<id>/model.onnx (varsayılan konvansiyon)
      - "id=path"     → açık dosya yolu (repo artefakt düzeni için: sentiment/sentiment.onnx,
                         prompt_classifier/prompt_intent.onnx vb.)
    Bağıl path'ler ML_MODEL_DIR'e göre çözülür.
    """
    out: list[tuple[str, str]] = []
    for item in [m.strip() for m in spec.split(",") if m.strip()]:
        if "=" in item:
            model_id, path = item.split("=", 1)
            model_id, path = model_id.strip(), path.strip()
        else:
            model_id, path = item, os.path.join(_model_dir, item, "model.onnx")
        if not model_id or not path:
            continue
        if not os.path.isabs(path):
            path = os.path.join(_model_dir, path)
        out.append((model_id, path))
    return out


@asynccontextmanager
async def lifespan(_app: FastAPI):
    """Uygulama ömrü boyunca modelleri yükler.

    MODELS env örn:
      "sentiment=/models/sentiment/sentiment.onnx,prompt_intent=/models/prompt_classifier/prompt_intent.onnx"
    """
    for model_id, path in _parse_models(os.getenv("MODELS", "")):
        if os.path.exists(path):
            registry.load(model_id, path)
            logger.info("model yüklendi: %s (%s)", model_id, path)
        else:
            logger.warning("model bulunamadı: %s", path)
    yield


app = FastAPI(title="GeoLens ML Serving", version="0.1.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict:
    """Go client canlılık kontrolü. Yüklü model listesini döner."""
    return {"status": "ok", "models": sorted(registry.ids())}


@app.get("/v1/models")
def list_models() -> dict:
    """Kayıtlı model ID listesi (Go client model_version doğrulaması için)."""
    return {"models": sorted(registry.ids())}


@app.post("/v1/hallucination/detect")
def detect_hallucinations(payload: dict) -> dict:
    """Cross-source hallüsinasyon tespiti (0421 A2-4).

    ONNX modeli değildir — geolens/features/hallucination.py cross_source_check
    kural ayağı serving içinde çalışır (Go tarafı 0421 M-4: hata olursa T1-T4
    kural tabanlıya düşer).

    İstek: {"responses": [{"id": "...", "engine": "...", "text": "..."}]}
    Yanıt: {"findings": [{"type", "severity", "description", "confidence", "engine"}]}
    En az 2 yanıt gerekir; tek yanıtta cross-source anlamsız olduğundan boş döner.
    """
    raw = payload.get("responses")
    if not isinstance(raw, list):
        return {"findings": []}
    responses = []
    for r in raw:
        if isinstance(r, dict) and r.get("id") and r.get("text"):
            responses.append(
                {
                    "id": str(r["id"]),
                    "engine": str(r.get("engine", "")),
                    "text": str(r["text"]),
                }
            )
    if len(responses) < 2:
        return {"findings": []}
    findings = [
        {
            "type": f.type,
            "severity": f.severity,
            "description": f.description,
            "confidence": f.confidence,
            "engine": f.engine,
        }
        for f in cross_source_check(responses)
    ]
    return {"findings": findings}


@app.post("/v1/predict")
def predict(payload: dict) -> dict:
    """Tek örnek inference.

    İstek gövdesi: {"model": "sentiment", "lang": "tr", "text": "..."}
    Yanıt: model infer dict'i + model_version.
    """
    model_id = payload.get("model")
    if not model_id:
        raise HTTPException(status_code=422, detail="'model' alanı zorunlu")
    model = registry.get(model_id)
    if model is None:
        raise HTTPException(status_code=404, detail=f"model bulunamadı: {model_id}")
    try:
        return model.predict(payload)
    except Exception as exc:  # pragma: no cover
        logger.exception("inference hatası: %s", model_id)
        raise HTTPException(status_code=500, detail=str(exc)) from exc
