"""ONNX model sarmalayıcı.

Model.onnx dosyasını ONNX Runtime session'ı olarak yükler. Girdi/çıktı adları
onnxruntime'dan okunur; predict() payload'daki "text" girdisini ilgili
input_name'e taşır ve çıktı dizilerini Python yerleşik türlerine çevirir.

Not: Tür dönüşümleri (tokenization) model özeldir ve Aşama 2'de her model için
ayrı processor ile genişletilir. Şablon şimdilik tek metin girdisi varsayar.
"""
from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)


class ONNXModel:
    def __init__(self, model_id: str, path: str, version: str, processor=None) -> None:
        self._id = model_id
        self._version = version
        self._path = path
        self._session: Optional[Any] = None
        self._input_names: List[str] = []
        self._output_names: List[str] = []
        # Processor: model özel ön işleme (örn. sentiment tokenization).
        # text -> feed dict döndürür. Yoksa varsayılan metin şablonu kullanılır.
        self._processor = processor

    def load(self) -> None:
        import onnxruntime as ort  # yavaş import; istek zamanı değil, başlangıçta

        session_options = ort.SessionOptions()
        self._session = ort.InferenceSession(
            self._path,
            sess_options=session_options,
            providers=["CPUExecutionProvider"],
        )
        self._input_names = [i.name for i in self._session.get_inputs()]
        self._output_names = [o.name for o in self._session.get_outputs()]
        logger.info("model %s yüklendi: inputs=%s outputs=%s", self._id, self._input_names, self._output_names)

    @property
    def model_id(self) -> str:
        return self._id

    @property
    def version(self) -> str:
        return self._version

    def predict(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        if self._session is None:
            self.load()

        assert self._session is not None
        text = payload.get("text", "")
        import numpy as np

        feed: Dict[str, Any] = {}
        if self._processor is not None:
            feed = self._processor(text)
        elif "text" in self._input_names:
            feed["text"] = np.array([[text.encode("utf-8")]], dtype=object)
        elif "input" in self._input_names:
            feed["input"] = np.array([[text]], dtype=object)

        if not feed:
            raise ValueError(f"model '{self._id}' için desteklenen girdi bulunamadı: {self._input_names}")

        outputs = self._session.run(self._output_names, feed)
        result: Dict[str, Any] = {}
        for name, arr in zip(self._output_names, outputs):
            # numpy dizisi → list; zaten list/map ise olduğu gibi bırak.
            result[name] = arr.tolist() if hasattr(arr, "tolist") else arr

        return {
            "model": self._id,
            "model_version": self._version,
            "outputs": result,
        }
