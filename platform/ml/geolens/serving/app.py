"""AI model serving API'si (FastAPI + ONNX Runtime).

Go backend'den POST /v1/predict ile çağrılır (bkz. platform/internal/ml/client.go).
Model kayıtlı değilse 404, inference hatasında 500 döner.
"""
from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

import numpy as np
from fastapi import FastAPI, HTTPException

from geolens.features.hallucination import cross_source_check
from geolens.features.judge import EvidenceBag, build_judge
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
            # İlk run maliyeti peşin ödenir — Go ML_TIMEOUT=2s içinde ilk isteğin
            # timeout olmasını önler (ilkinference soğukta 5-10s sürebilir).
            model = registry.get(model_id)
            if model is not None:
                model.warmup()
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


def _parse_label_confidence(result: dict) -> tuple[str, float]:
    """skl2onnx pipeline çıktısından etiket + güven çözer (0421 A2-3).

    Tipik çıktı adları output_label (string dizi) ve output_probability
    ([1, n_sınıf] sayısal dizi). Adlar modele göre değişebileceğinden bilinmeyen
    adlar için string dizi → etiket, 2D sayısal dizi → olasılık fallback'i vardır.
    """
    outputs = result.get("outputs", {})
    label = ""
    conf = 0.0

    label_arr = outputs.get("output_label")
    if label_arr is not None:
        flat = np.asarray(label_arr).reshape(-1)
        if len(flat):
            label = str(flat[0])

    prob_arr = outputs.get("output_probability")
    if prob_arr is not None:
        # skl2onnx LabelEncoder + TreeEnsemble: seq(map(string,tensor(float)))
        # biçiminde bir dict listesi döner: [{"presence": 0.62, ...}]. Eski
        # sürümler 2D sayısal dizi döndürebilir — ikisi de desteklenir.
        if isinstance(prob_arr, (list, tuple)) and prob_arr and isinstance(prob_arr[0], dict):
            row = prob_arr[0]
            if row and label in row:
                conf = float(row[label])
            elif row:
                # Label dict'te yoksa (sınıf seti uyuşmazlığı) en yüksek olasılıklı
                # sınıfı label ile UZLAŞTIR — yanıltıcı "label A, confidence B"
                # eşleşmesi üretme.
                top = max(row, key=row.get)
                conf = float(row[top])
                if not label:
                    label = top
        else:
            probs = np.asarray(prob_arr, dtype=float)
            if probs.ndim >= 1 and len(probs):
                row = probs[0] if probs.ndim == 2 else probs
                if row.size:
                    conf = float(np.max(row))

    if not label or conf == 0.0:
        for name, value in outputs.items():
            if name in ("output_label", "output_probability"):
                continue
            arr = np.asarray(value)
            if arr.dtype.kind in "OUS" and not label:
                flat = arr.reshape(-1)
                if len(flat):
                    label = str(flat[0])
            elif arr.dtype.kind == "f" and arr.ndim >= 1 and conf == 0.0:
                row = arr[0] if arr.ndim == 2 else arr
                if row.size:
                    conf = float(np.max(row))
    return label, round(conf, 4)


@app.post("/v1/prompt/classify")
def classify_prompt(payload: dict) -> dict:
    """Prompt sınıflandırma (0421 A2-3): intent/topic/persona/funnel.

    İstek: {"text": "..."}
    Yanıt: {"intent": {"label", "confidence"}, "topic": ..., "persona": ..., "funnel": ...}
    Modeller (prompt_* ONNX) yüklü değilse 404 döner — Go tarafı varsayılan
    VI ağırlıklarına düşer (0421 M-4).
    """
    text = payload.get("text", "")
    if not isinstance(text, str) or not text.strip():
        raise HTTPException(status_code=422, detail="'text' alanı zorunlu")
    out = {}
    for model_id in ("prompt_intent", "prompt_topic", "prompt_persona", "prompt_funnel"):
        model = registry.get(model_id)
        if model is None:
            raise HTTPException(status_code=404, detail=f"model bulunamadı: {model_id}")
        result = model.predict({"text": text})
        label, conf = _parse_label_confidence(result)
        out[model_id.removeprefix("prompt_")] = {"label": label, "confidence": conf}
    return out


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

    # A2-5 LLM-as-Judge: şüphe eşiği (varsayılan 3) aşılırsa pahalı LLM yargıcı
    # tetiklenir (0421 §6 maliyet eşikli tetikleme). GEOLENS_JUDGE_API_KEY yoksa
    # kural tabanlı fallback döner — serving key'siz çalışır (M-1 fallback ilkesi).
    threshold = int(os.getenv("GEOLENS_JUDGE_THRESHOLD", "3"))
    judge = build_judge(threshold=threshold, lang=payload.get("lang", "tr"))
    verdict = judge.judge(
        EvidenceBag(
            prompt_id=payload.get("prompt_id", ""),
            responses=responses,
            cross_flags=findings,
        )
    )
    return {"findings": findings, "judge": {"verdict": verdict.verdict, "triggered": verdict.triggered, "confidence": verdict.confidence}}


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
