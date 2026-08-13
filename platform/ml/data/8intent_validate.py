"""0421-8INTENT Faz D · 8-intent model doğrulama raporu üretici.

Eğitilmiş joblib modelleri (intent/persona/funnel, 2.0.0) üzerinde per-sınıf F1
ve karışım matrisi raporlar; serving'de kullanılacak 8 intent etiketini doğrular.
Çıktı: ml/data/8intent_f1_report.md

Kullanım: python data/8intent_validate.py
"""
from __future__ import annotations

import json
import os

import joblib
from sklearn.metrics import classification_report, confusion_matrix

BASE = os.path.dirname(__file__)
TEST = os.path.join(BASE, "odev01", "split", "test_prompts_v1.jsonl")
MODELS = os.path.join(BASE, "..", "models", "prompt_classifier")
OUT = os.path.join(BASE, "8intent_f1_report.md")

TARGETS = ["intent", "persona", "funnel"]
INTENT_LABELS = ["information", "recommendation", "comparison", "complaint", "problem", "purchase", "opinion", "news"]


def load_jsonl(path: str) -> list[dict]:
    with open(path, encoding="utf-8") as fh:
        return [json.loads(line) for line in fh if line.strip()]


def main() -> None:
    test = load_jsonl(TEST)
    lines: list[str] = [
        "# 0421-8INTENT · 8-Intent Model Doğrulama Raporu (Faz D)",
        "",
        "| Alan | Değer |",
        "|------|-------|",
        "| Doküman ID | VERİ-8INTENT-F1 |",
        "| Proje | GeoLens |",
        "| Durum | Draft |",
        "| Tarih | 2026-08-13 |",
        f"| Test seti | `data/odev01/split/test_prompts_v1.jsonl` ({len(test)} kayıt) |",
        "| Model sürümü | intent/persona/funnel 2.0.0 (topic 1.1.0) |",
        "",
        "## Per-sınıf F1",
        "",
    ]

    confusions: dict[str, list[tuple[str, str, int]]] = {}
    for target in TARGETS:
        model = joblib.load(os.path.join(MODELS, f"prompt_{target}.joblib"))
        y_true = [r[target] for r in test]
        y_pred = model.predict([r["text"] for r in test])
        report = classification_report(y_true, y_pred, digits=3, zero_division=0)
        lines.append(f"### {target}")
        lines.append("")
        lines.append("```")
        lines.append(report)
        lines.append("```")
        lines.append("")
        # intent karışımı: en yüksek yanlış eşleşmeler
        if target == "intent":
            cm = confusion_matrix(y_true, y_pred, labels=INTENT_LABELS)
            mis = []
            for i, ti in enumerate(INTENT_LABELS):
                for j, tj in enumerate(INTENT_LABELS):
                    if i != j and cm[i][j] > 0:
                        mis.append((ti, tj, int(cm[i][j])))
            confusions["intent"] = sorted(mis, key=lambda x: -x[2])

    lines.append("## Intent karışım analizi (yanlış eşleşmeler)")
    lines.append("")
    lines.append("| Gerçek → Tahmin | Adet |")
    lines.append("|-----------------|------|")
    mis = confusions.get("intent", [])
    if mis:
        for ti, tj, n in mis:
            lines.append(f"| {ti} → {tj} | {n} |")
    else:
        lines.append("| (karışım yok) | — |")
    lines.append("")

    # Serving uyumluluğu: tahmin kümesi 8-intent taksonomisiyle sınırlı mı?
    model = joblib.load(os.path.join(MODELS, "prompt_intent.joblib"))
    classes = sorted(model.classes_)
    lines.append("## Serving taksonomi uyumu")
    lines.append("")
    lines.append(f"- Model sınıfları: `{', '.join(classes)}`")
    lines.append(f"- 8-intent hedefi: `{', '.join(INTENT_LABELS)}`")
    lines.append(f"- Uyum: {'✅ tam' if set(classes) == set(INTENT_LABELS) else '❌ eksik: ' + str(set(INTENT_LABELS) - set(classes))}")
    lines.append("")

    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    print(f"\nRapor yazıldı: {os.path.relpath(OUT, BASE)}")


if __name__ == "__main__":
    main()
