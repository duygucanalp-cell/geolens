"""A2-3 · Prompt sınıflandırma modeli (TF-IDF + LogisticRegression).

Ekran: intent/topic/persona/funnel sınıflandırması (0420 WP-04, İP-01).
- Eğitim verisi: ml/data/train/prompts.jsonl (A1-4 çıktısı)
- Değerlendirme: ml/data/test/prompts.jsonl (>%85 F1 hedefi)
- Export: her hedef için ONNX (skl2onnx) / fallback joblib

Kullanım:
    python -m geolens.prompt_classifier.train
"""
from __future__ import annotations

import argparse
import json
import os

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report
from sklearn.pipeline import Pipeline

TARGETS = ["intent", "topic", "persona", "funnel"]
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "models", "prompt_classifier")


def load_jsonl(path: str) -> list[dict]:
    with open(path, encoding="utf-8") as fh:
        return [json.loads(line) for line in fh if line.strip()]


def train_target(train_records: list[dict], y_key: str, seed: int = 42) -> Pipeline:
    pipeline = Pipeline(
        [
            ("tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=1, sublinear_tf=True)),
            ("clf", LogisticRegression(max_iter=1000, C=1.0, random_state=seed)),
        ]
    )
    X = [r["text"] for r in train_records]
    y = [r[y_key] for r in train_records]
    pipeline.fit(X, y)
    return pipeline


def export_onnx(pipeline: Pipeline, out_path: str) -> bool:
    """ONNX export dener; başarısızsa False döner (joblib fallback kullanılır)."""
    try:
        from skl2onnx import to_onnx
        from skl2onnx.common.data_types import StringTensorType

        model_onnx = to_onnx(pipeline, initial_types=[("text", StringTensorType([None, 1]))])
        with open(out_path, "wb") as fh:
            fh.write(model_onnx.SerializeToString())
        return True
    except Exception as exc:  # pragma: no cover - bağımlılık/uyum hatası fallback'i
        print(f"  (ONNX export atlandı: {exc})")
        return False


def main() -> None:
    parser = argparse.ArgumentParser(description="Prompt sınıflandırıcı eğit (A2-3)")
    parser.add_argument("--train", default=os.path.join(os.path.dirname(__file__), "..", "..", "data", "train", "prompts.jsonl"))
    parser.add_argument("--test", default=os.path.join(os.path.dirname(__file__), "..", "..", "data", "test", "prompts.jsonl"))
    parser.add_argument("--out", default=OUTPUT_DIR)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    train = load_jsonl(args.train)
    test = load_jsonl(args.test)
    os.makedirs(args.out, exist_ok=True)

    X_test = [r["text"] for r in test]
    for target in TARGETS:
        print(f"=== {target} ===")
        model = train_target(train, target, seed=args.seed)
        y_true = [r[target] for r in test]
        y_pred = model.predict(X_test)

        report = classification_report(y_true, y_pred, digits=3, zero_division=0, output_dict=True)
        f1_macro = report["macro avg"]["f1-score"]
        print(f"  F1 (macro): {f1_macro:.3f} | precision {report['macro avg']['precision']:.3f} | recall {report['macro avg']['recall']:.3f}")

        joblib.dump(model, os.path.join(args.out, f"prompt_{target}.joblib"))
        onnx_ok = export_onnx(model, os.path.join(args.out, f"prompt_{target}.onnx"))
        print(f"  exported: joblib + {'onnx' if onnx_ok else 'onnx-yok(joblib)'}")

        if f1_macro < 0.85:
            print(f"  ! UYARI: {target} F1 %85 hedefinin altında ({f1_macro:.3f})")

    print(f"\nModeller yazıldı: {os.path.abspath(args.out)}")


if __name__ == "__main__":
    main()
