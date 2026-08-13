"""A2-3 · Prompt sınıflandırma modeli (TF-IDF + LogisticRegression).

Ekran: intent/topic/persona/funnel sınıflandırması (0420 WP-04, İP-01).
- Eğitim verisi: ml/data/train/prompts.jsonl (A1-4 çıktısı)
- Değerlendirme: ml/data/test/prompts.jsonl (>%85 F1 hedefi)
- Export: her hedef için ONNX (skl2onnx) / fallback joblib

0421-8INTENT (Faz B): 8-intent taksonomisiyle yeniden eğitim —
- intent/persona/funnel, odev01/prompts_v1.jsonl split'inden (8/5/5 sınıf)
- sınıf dengesizliği için LogisticRegression(class_weight='balanced')
  (opinion 2400 vs complaint 600)
- topic, data/full üzerinde kalır (1.1.0)

Kullanım:
    python -m geolens.prompt_classifier.train
    python -m geolens.prompt_classifier.train --train data/odev01/split/train_prompts_v1.jsonl \
        --test data/odev01/split/test_prompts_v1.jsonl --targets intent,persona,funnel
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


def train_target(train_records: list[dict], y_key: str, seed: int = 42, class_weight: str = "balanced") -> Pipeline:
    # "none" → None (varsayılan eşit sınıf ağırlığı); "balanced" → otomatik dengeleme.
    cw = None if class_weight == "none" else class_weight
    pipeline = Pipeline(
        [
            ("tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=1, sublinear_tf=True)),
            ("clf", LogisticRegression(max_iter=1000, C=1.0, random_state=seed, class_weight=cw)),
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
    parser.add_argument("--class-weight", default="balanced", choices=["none", "balanced"],
                        help="LogisticRegression class_weight (0421-8INTENT Faz B: dengesiz sınıflar)")
    parser.add_argument("--targets", default=",".join(TARGETS),
                        help="Eğitilecek hedefler (virgülle ayrılmış); topic ayrı çalıştırılır")
    args = parser.parse_args()

    targets = [t.strip() for t in args.targets.split(",") if t.strip()]
    unknown = set(targets) - set(TARGETS)
    if unknown:
        parser.error(f"bilinmeyen hedef: {sorted(unknown)}")

    train = load_jsonl(args.train)
    test = load_jsonl(args.test)
    os.makedirs(args.out, exist_ok=True)

    X_test = [r["text"] for r in test]
    for target in targets:
        print(f"=== {target} ===")
        model = train_target(train, target, seed=args.seed, class_weight=args.class_weight)
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
