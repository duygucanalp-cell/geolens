# GeoLens ML Serving imajı (0421 A0-2).
# FastAPI + ONNX Runtime ile inference API. ONNX modelleri çalışma zamanında
# ./ml/models volume'undan /models'e bağlanır (image'a kopyalanmaz — büyük artefakt).
#
# İki hedef:
#   ml-dev  — tüm dev bağımlılıkları (pytest, ruff, httpx, openai, psycopg dahil).
#             CI ml-eval job'ının adımlarıyla birebir aynı ortamı üretir:
#               docker build --target ml-dev -t geolens-ml-dev .
#             Testler:  docker run --rm -v ${PWD}/ml/tests:/app/tests geolens-ml-dev pytest
#   default (ml-serving) — yalnızca çalışma zamanı bağımlılıkları (hafif, prod).
FROM python:3.11-slim AS base

# ONNX Runtime'ın StringNormalizer operatörü (skl2onnx çıktıları) çalışma anında
# locale gerektirir; slim imajında varsayılan locale yoktur → C++ exception:
#   "Failed to construct locale with name:en_US.UTF-8"
# locales paketi + en_US.UTF-8 üretimi bu hatayı giderir.
RUN apt-get update && apt-get install -y --no-install-recommends locales \
    && sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen \
    && locale-gen \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

WORKDIR /app

COPY pyproject.toml ./
COPY geolens/ ./geolens/

# ---- Dev hedefi: tüm test/CI bağımlılıkları (0421 A0-5 ml-eval ile eş) ----
FROM base AS ml-dev

# [dev] ekstrası: pytest, httpx, ruff, transformers, torch, datasets, accelerate,
# onnx, skl2onnx, onnxscript, psycopg (export_unlabeled.py), openai (LLM-as-Judge).
RUN pip install --no-cache-dir -e ".[dev]"

CMD ["pytest"]

# ---- Serving hedefi: yalnızca çalışma zamanı bağımlılıkları ----
FROM base AS ml-serving

# Serving çalışma zamanı bağımlılıkları (pyproject.toml'daki `dependencies` +
# transformers). transformers: sentiment ONNX processor'ı AutoTokenizer kullanır
# (inference'te torch gerektirmez; ONNX Runtime çalıştırır).
RUN pip install --no-cache-dir . "transformers>=4.36"

EXPOSE 8900

# Modeller volume'dan gelir:
#   docker compose up ml-serving  →  ./ml/models:/models, ML_MODEL_DIR=/models
CMD ["uvicorn", "geolens.serving.app:app", "--host", "0.0.0.0", "--port", "8900"]
