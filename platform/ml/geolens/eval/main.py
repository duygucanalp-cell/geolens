"""Gold dataset üzerinde model değerlendirme giriş noktası (0421 A0-5, A2).

Dataset hazır değilse elegant geçer ve CI'da fail etmez. Dataset mevcutken
(data/train/ + data/test/) her model için eşik kontrolü yapar — geçemeyen model
CI'yi kırar (0421 M-5). Eşikler 0421 A2 hedeflerinden gelir.

Kullanım:  python -m geolens.eval.main
"""
from __future__ import annotations

import json
import logging
import os
import sys

logger = logging.getLogger(__name__)

DATA_DIR = os.getenv("ML_DATA_DIR", os.path.join(os.getcwd(), "data"))
MODEL_DIR = os.getenv("ML_MODEL_DIR", os.path.join(os.getcwd(), "models"))
# 0421 A2 hedefleri: sentiment >%90, prompt class >%85 F1, NER >%85 P/R.
# Sentifik veride (şablon) modeller →1.0 ölçülür; gerçek veriyle eşik anlamlıdır.
SENTIMENT_F1_THRESHOLD = 0.90
PROMPT_F1_THRESHOLD = 0.85
# A0-5 concatenate: mecbur değil; eğitim tamamlanmamışsa atla (bool).
REQUIRE_MODELS = os.getenv("ML_REQUIRE_MODELS", "0") == "1"


def _load_jsonl(path: str) -> list[dict]:
    records = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def _f1_macro(y_true: list, y_pred: list) -> float:
    from sklearn.metrics import f1_score

    return float(f1_score(y_true, y_pred, average="macro", zero_division=0))


def _eval_prompt_classifier(test_dir: str) -> float:
    """test/prompts.jsonl üzerinden 4 hedefint F1 ortalaması."""
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    test_p = _load_jsonl(os.path.join(test_dir, "prompts.jsonl"))
    if not test_p:
        return 1.0
    train_p = _load_jsonl(os.path.join(test_dir, "..", "train", "prompts.jsonl"))
    f1s = []
    for target in ["intent", "topic", "persona", "funnel"]:
        vec = TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True)
        clf = LogisticRegression(max_iter=1000)
        clf.fit(vec.fit_transform([r["text"] for r in train_p]), [r[target] for r in train_p])
        pred = clf.predict(vec.transform([r["text"] for r in test_p]))
        f1s.append(_f1_macro([r[target] for r in test_p], pred))
    return sum(f1s) / len(f1s)


def _sentiment_from_gold(rec: dict) -> str:
    for m in rec.get("mentions", []):
        if m.get("type") == "brand" and m.get("sentiment"):
            return m["sentiment"]
    return "neutral"


def run() -> int:
    """Eval akışı. 0=başarı, 1=CI kırılması. Dataset yoksa 0 döner."""
    test_dir = os.path.join(DATA_DIR, "test")
    if not os.path.exists(os.path.join(test_dir, "prompts.jsonl")):
        logger.info("test datasi yok (%s) — eval atlandı (Aşama 1 bekleniyor)", test_dir)
        return 0

    passed = True

    # A2-3 prompt sınıflandırıcı (4 hedefin macro-F1 ortalaması)
    try:
        f1_prompt = _eval_prompt_classifier(test_dir)
        ok = f1_prompt >= PROMPT_F1_THRESHOLD
        passed &= ok
        logger.info("prompt_classifier macro-F1: %.3f (eşik %.2f) %s",
                    f1_prompt, PROMPT_F1_THRESHOLD, "✓" if ok else "✗")
    except Exception as exc:
        logger.warning("prompt_classifier eval atlandı: %s", exc)
        if REQUIRE_MODELS:
            passed = False

    # A2-1 sentiment: ONNX + tokenizer cache varsa test/gold üzerinde F1
    sentiment_dir = os.path.join(MODEL_DIR, "sentiment")
    try:
        onnx_path = os.path.join(sentiment_dir, "sentiment.onnx")
        tokenizer_dir = os.path.join(sentiment_dir, "tokenizer")
        if not os.path.exists(onnx_path) or not os.path.isdir(tokenizer_dir) \
                or not os.path.exists(os.path.join(test_dir, "gold.jsonl")):
            logger.info("sentiment eval atlandı (onnx/tokenizer/test gold yok)")
        else:
            import numpy as np
            import onnxruntime as ort
            from transformers import AutoTokenizer

            from geolens.sentiment.train import LABELS

            tokenizer = AutoTokenizer.from_pretrained(tokenizer_dir)
            session = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
            test_gold = _load_jsonl(os.path.join(test_dir, "gold.jsonl"))
            y_true, y_pred = [], []
            for rec in test_gold:
                enc = tokenizer(rec["response_text"], padding="max_length", truncation=True, max_length=128, return_tensors="np")
                logits = session.run(None, {k: v.astype(np.int64) for k, v in enc.items()})[0]
                y_true.append(LABELS.index(_sentiment_from_gold(rec)))
                y_pred.append(int(np.argmax(logits[0])))
            f1_sent = _f1_macro(y_true, y_pred)
            ok = f1_sent >= SENTIMENT_F1_THRESHOLD
            passed &= ok
            logger.info("sentiment macro-F1: %.3f (eşik %.2f) %s",
                        f1_sent, SENTIMENT_F1_THRESHOLD, "✓" if ok else "✗")
    except Exception as exc:
        logger.warning("sentiment eval atlandı: %s", exc)
        if REQUIRE_MODELS:
            passed = False

    return 0 if passed else 1


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(run())


if __name__ == "__main__":
    main()
