# GeoLens ML Katmanı

0421 planının (04-ai-framework/0421-ai-model-uygulama-plani.md) Aşama 0'ı:
kural tabanlı bileşenlerin ML/DL tabanlı karşılıklarını barındıran katman.

## Dizin Yapısı

```
ml/
├── pyproject.toml          # Python ortamı + bağımlılıklar (A0-1)
├── geolens/
│   ├── __init__.py
│   └── serving/            # FastAPI + ONNX Runtime inference API (A0-2)
│       ├── app.py          # /health, /v1/models, /v1/predict
│       ├── registry.py     # model kayıt defteri (model_id -> ONNX session)
│       └── onnx_model.py   # ONNX sarmalayıcı + model_version
├── data/                   # A0-4: gold dataset + prompt taksonomisi (Aşama 1'de doldurulur)
├── models/                 # Export edilmiş .onnx modeller (gitignore)
├── features/               # Regex/feature extraction kuralları (A1-5)
├── poc/                    # 5 PoC prototipi (Aşama 4)
└── tests/                  # pytest
```

## Çalıştırma

```bash
cd ml
python -m venv .venv
pip install -e ".[dev]"          # bağımlılıklar
pytest                            # testler
uvicorn geolens.serving.app:app --port 8900   # serving API
```

## Maybe Env Değişkenleri (serving)

| Env | Varsayılan | Açıklama |
|-----|-----------|----------|
| `ML_MODEL_DIR` | `./models` | ONNX model dizini (`<model_id>/model.onnx`) |
| `ML_DEFAULT_MODEL` | `""` | Varsayılan model id |
| `MODELS` | `""` | Başlangıçta yüklenecek model listesi (`sentiment=...,ner=...`) |

## Go Entegrasyonu

Go backend `platform/internal/ml/client.go` üzerinden `ML_SERVING_URL` adresindeki
serving API'ye `POST /v1/predict` çağrısı yapar. Serving ulaşılamazsa kural tabanlı
bileşen fallback olarak çalışır (0421 M-4).