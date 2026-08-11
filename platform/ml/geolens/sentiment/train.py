"""A2-1 · Sentiment modeli eğitimi (XLM-R fine-tune, 3 sınıf).

Veri: ml/data/train/gold.jsonl (A1-4) — her kaydın response_text'i, etiket
`mentions[].type == "brand"` öğesinin sentiment değeri (0420 İP-03 kuralı).
Export: torch.onnx.export ile ONNX (CPU'da serving için). transformers 5.x'te
`transformers.onnx` kaldırıldığından doğrudan torch export kullanılır (0421 M-1).

Model seçilebilir; varsayılan XLM-R base. CPU'da küçük epoch/örnek ile çalışır.

Kullanım:
    python -m geolens.sentiment.train [--epochs 2] [--max_train 200]
"""
from __future__ import annotations

import argparse
import json
import os
from typing import Optional

import numpy as np

LABELS = ["negative", "neutral", "positive"]


def _compute_metrics(eval_pred):
    """Trainer için F1 (macro) + accuracy hesabı (0421 A2-1 hedefi >%90 F1)."""
    import numpy as np
    from sklearn.metrics import accuracy_score, f1_score

    logits, labels = eval_pred.predictions, eval_pred.label_ids
    preds = np.argmax(logits, axis=-1)
    return {
        "accuracy": float(accuracy_score(labels, preds)),
        "f1_macro": float(f1_score(labels, preds, average="macro", zero_division=0)),
    }


def load_gold(path: str, max_train: Optional[int]) -> tuple[list[str], list[int]]:
    texts: list[str] = []
    labels: list[int] = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if not line.strip():
                continue
            rec = json.loads(line)
            label = None
            for m in rec.get("mentions", []):
                if m.get("type") == "brand":
                    label = m.get("sentiment")
                    break
            if label not in LABELS:
                continue
            texts.append(rec["response_text"])
            labels.append(LABELS.index(label))
            if max_train and len(texts) >= max_train:
                break
    return texts, labels


def build_dataset(tokenizer, texts: list[str], labels: list[int], device) -> dict:
    enc = tokenizer(texts, padding="max_length", truncation=True, max_length=128, return_tensors="pt")
    enc["labels"] = np.asarray(labels, dtype=np.int64)
    enc = {k: (v.to(device) if hasattr(v, "to") else v) for k, v in enc.items()}
    return enc


def main() -> None:
    parser = argparse.ArgumentParser(description="Sentiment fine-tune (A2-1)")
    parser.add_argument(
        "--model_name",
        default=os.environ.get("GEOLENS_SENTIMENT_MODEL", "FacebookAI/xlm-roberta-base"),
        help="HF model adı (varsayılan XLM-R base; mBERT de çalışır)",
    )
    parser.add_argument("--train", default="data/train/gold.jsonl")
    parser.add_argument("--test", default="data/test/gold.jsonl")
    parser.add_argument("--out", default="models/sentiment")
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--lr", type=float, default=2e-5)
    parser.add_argument("--batch_size", type=int, default=8)
    parser.add_argument("--max_train", type=int, default=None, help="hızlı smoke-run için eğitim sınırı")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    from collections import Counter

    import torch
    from torch.utils.data import Dataset
    from transformers import (
        AutoModelForSequenceClassification,
        AutoTokenizer,
        Trainer,
        TrainingArguments,
        set_seed,
    )

    set_seed(args.seed)
    os.makedirs(args.out, exist_ok=True)

    train_texts, train_labels = load_gold(args.train, args.max_train)
    test_texts, test_labels = load_gold(args.test, None)
    if not train_texts or not test_texts:
        raise SystemExit("gold verisi boş — önce A1-2 çıktısını üretin.")

    print(f"veri: train={len(train_texts)} test={len(test_texts)}")
    print(f"eğitim sınıf dağılımı: {dict(Counter([LABELS[i] for i in train_labels]))}")

    tokenizer = AutoTokenizer.from_pretrained(args.model_name)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model_name, num_labels=len(LABELS), id2label=dict(enumerate(LABELS)), label2id={lbl: i for i, lbl in enumerate(LABELS)}
    )

    # Sınıf dengesizliği (0420 İP-03 sentetik veride positive baskın): weighted loss
    counts = torch.tensor([train_labels.count(c) for c in range(len(LABELS))], dtype=torch.float)
    counts = counts.clamp(min=1)
    class_weights = (counts.sum() / (len(LABELS) * counts)).to(torch.float32)
    print(f"class_weights: {class_weights.tolist()}")

    class WeightedTrainer(Trainer):
        def compute_loss(self, model, inputs, return_outputs=False, num_items_in_batch=None):
            labels = inputs["labels"]
            outputs = model(**{**inputs, "labels": None})
            logits = outputs.logits
            loss_fct = torch.nn.CrossEntropyLoss(weight=class_weights)
            loss = loss_fct(logits.view(-1, len(LABELS)), labels.view(-1))
            return (loss, outputs) if return_outputs else loss

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}")

    class GoldDS(Dataset):
        def __init__(self, txts, lbs):
            self.enc = tokenizer(txts, padding="max_length", truncation=True, max_length=128, return_tensors="pt")
            self.lbs = torch.tensor(lbs, dtype=torch.long)

        def __len__(self):
            return len(self.lbs)

        def __getitem__(self, i):
            return {"input_ids": self.enc["input_ids"][i], "attention_mask": self.enc["attention_mask"][i], "labels": self.lbs[i]}

    train_ds = GoldDS(train_texts, train_labels)
    test_ds = GoldDS(test_texts, test_labels)

    training_args = TrainingArguments(
        output_dir=os.path.join(args.out, "checkpoints"),
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=16,
        learning_rate=args.lr,
        eval_strategy="epoch",
        save_strategy="no",
        logging_steps=50,
        report_to=[],
        seed=args.seed,
        disable_tqdm=True,
    )

    trainer = WeightedTrainer(model=model, args=training_args, train_dataset=train_ds, eval_dataset=test_ds, compute_metrics=_compute_metrics)
    trainer.train()
    metrics = trainer.evaluate(test_ds)
    print(f"eval: {metrics}")

    # PyTorch checkpoint (CPU'ya taşı) + ONNX export
    checkpoint = os.path.join(args.out, "model.pt")
    torch.save({"model_state": model.state_dict()}, checkpoint)
    print(f"checkpoint: {checkpoint}")

    # Serving/eval için tokenizer cache (registry sentiment processor kullanır)
    tokenizer_dir = os.path.join(args.out, "tokenizer")
    os.makedirs(tokenizer_dir, exist_ok=True)
    tokenizer.save_pretrained(tokenizer_dir)
    print(f"tokenizer: {tokenizer_dir}")

    model.eval()
    model.to("cpu")
    dummy = tokenizer(["örnek bir test cümlesi"], return_tensors="pt", padding="max_length", truncation=True, max_length=128)
    onnx_path = os.path.join(args.out, "sentiment.onnx")
    torch.onnx.export(
        model,
        (dummy["input_ids"], dummy["attention_mask"]),
        onnx_path,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={"input_ids": {0: "batch"}, "attention_mask": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=17,
    )
    print(f"onnx: {onnx_path}")


if __name__ == "__main__":
    main()
