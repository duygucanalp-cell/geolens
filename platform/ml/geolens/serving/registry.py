"""Model kayıt defteri.

Model servisinin çekirdeği — ONNX session'larını model_id anahtarıyla tutar.
Model interface'i predict(payload: dict) -> dict şeklindedir; model versiyonu
her yanıtta döner (Go tarafında algorithm_version uyumu için).
"""
from __future__ import annotations

import os
from typing import Callable, Dict, Optional

import numpy as np

from geolens.serving.onnx_model import ONNXModel

MODEL_ID_TO_VERSION: Dict[str, str] = {
    # 0421 Aşama 2 model kayıtları: eğitim tamamlandıkça sürüm artırılır.
    # 1.1.0 — Ödev-01 verisiyle yeniden eğitim (ml/data/odev01, 2026-08-12).
    "prompt_intent": "1.1.0",
    "prompt_topic": "1.1.0",
    "prompt_persona": "1.1.0",
    "prompt_funnel": "1.1.0",
    # Sentiment ONNX input'ları (input_ids/attention_mask) özel processor
    # gerektirir; registry'ye alınınca processor aşağıdaki harita üzerinden gelir.
    "sentiment": "1.0.0",
}


def _build_sentiment_processor():
    """sentiment.onnx feed dict üretici: XLM-R tokenizer önbellekten çözülür.

    Tokenizer dizini ML_SENTIMENT_TOKENIZER_DIR ile override edilebilir (Docker/
    compose ortamı: /models/sentiment/tokenizer); varsayılan eğitim dizinidir
    (models/sentiment/tokenizer). Bulunamazsa HF hub'dan indirilir (offline
    senaryoda dosya kopyalanmalıdır).
    """
    try:
        from transformers import AutoTokenizer
    except ImportError:
        return None

    cache_path = os.environ.get("ML_SENTIMENT_TOKENIZER_DIR") or os.path.join(
        os.path.dirname(__file__), "..", "..", "models", "sentiment", "tokenizer"
    )
    try:
        if os.path.isdir(cache_path):
            tokenizer = AutoTokenizer.from_pretrained(cache_path)
        else:
            tokenizer = AutoTokenizer.from_pretrained(os.environ.get("GEOLENS_SENTIMENT_MODEL", "FacebookAI/xlm-roberta-base"))
    except Exception:
        return None

    def processor(text: str) -> Dict[str, object]:
        enc = tokenizer(text, padding="max_length", truncation=True, max_length=128, return_tensors="np")
        return {k: v.astype(np.int64) for k, v in enc.items()}

    return processor


_PROCESSORS: Dict[str, Callable[[str], Dict[str, object]]] = {
    "sentiment": _build_sentiment_processor(),
}


class ModelRegistry:
    """model_id -> Model eşlemesi. Thread-safe (çekirdek işlemler seri)."""

    def __init__(self) -> None:
        self._models: Dict[str, ONNXModel] = {}

    def load(self, model_id: str, path: str) -> None:
        self._models[model_id] = ONNXModel(model_id, path, self._version_for(model_id), _PROCESSORS.get(model_id))

    def get(self, model_id: str) -> Optional[ONNXModel]:
        return self._models.get(model_id)

    def ids(self) -> list[str]:
        return list(self._models.keys())

    def _version_for(self, model_id: str) -> str:
        return MODEL_ID_TO_VERSION.get(model_id, "0.0.0")
