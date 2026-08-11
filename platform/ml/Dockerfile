# GeoLens ML Serving imajı (0421 A0-2).
# FastAPI + ONNX Runtime ile inference API. ONNX modelleri çalışma zamanında
# ./ml/models volume'undan /models'e bağlanır (image'a kopyalanmaz — büyük artefakt).
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Serving çalışma zamanı bağımlılıkları (pyproject.toml'daki `dependencies` +
# transformers). transformers: sentiment ONNX processor'ı AutoTokenizer kullanır
# (inference'te torch gerektirmez; ONNX Runtime çalıştırır).
COPY pyproject.toml ./
COPY geolens/ ./geolens/

RUN pip install --no-cache-dir . "transformers>=4.36"

EXPOSE 8900

# Modeller volume'dan gelir:
#   docker compose up ml-serving  →  ./ml/models:/models, ML_MODEL_DIR=/models
CMD ["uvicorn", "geolens.serving.app:app", "--host", "0.0.0.0", "--port", "8900"]
