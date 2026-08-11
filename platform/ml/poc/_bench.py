"""A4-1 · PoC ortak benchmark yardımcıları (İP-08).

Her prototip: gold örneklerde çalıştırılır; accuracy / precision / recall / F1,
işlem süresi (ms) ve bellek tahmini raporlanır. Minimum kabul eşiği: >%80.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Callable, Iterable, Sequence


@dataclass
class POCResult:
    name: str
    accuracy: float
    precision: float
    recall: float
    f1: float
    runtime_ms: float
    n: int
    fail_examples: list[dict]

    def ok(self, threshold: float = 0.80) -> bool:
        return self.f1 >= threshold

    def render(self) -> str:
        status = "PASS" if self.ok() else "FAIL"
        return (
            f"[{status}] {self.name}: n={self.n} "
            f"acc={self.accuracy:.3f} prec={self.precision:.3f} "
            f"recall={self.recall:.3f} F1={self.f1:.3f} "
            f"({self.runtime_ms:.2f} ms)"
        )


def measure_runtime(fn: Callable[[], object]) -> float:
    start = time.perf_counter()
    fn()
    return (time.perf_counter() - start) * 1000.0


def classification_metrics(golds: Sequence[str], preds: Sequence[str]) -> tuple[float, float, float, float]:
    """accuracy / precision(macro) / recall(macro) / F1(macro) — multi-class."""
    classes = sorted({*golds, *preds})
    if not classes:
        return 1.0, 1.0, 1.0, 1.0
    n = len(golds)
    correct = sum(1 for g, p in zip(golds, preds) if g == p)
    acc = correct / n if n else 0.0
    precs: list[float] = []
    recs: list[float] = []
    for c in classes:
        tp = sum(1 for g, p in zip(golds, preds) if g == c and p == c)
        fp = sum(1 for g, p in zip(golds, preds) if g != c and p == c)
        fn_ = sum(1 for g, p in zip(golds, preds) if g == c and p != c)
        prec = tp / (tp + fp) if tp + fp else 0.0
        rec = tp / (tp + fn_) if tp + fn_ else 0.0
        precs.append(prec)
        recs.append(rec)
    precision = sum(precs) / len(precs)
    recall = sum(recs) / len(recs)
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return acc, precision, recall, f1


def run_classification(
    name: str,
    items: Sequence[tuple[str, str, Callable[[str], str]],],
) -> POCResult:
    """items: [(gold_label, —, fn(text)->label)] — Ikinci alan input text.

    Yerine daha net imza: items: [(%-input, gold_label, fn)]. Aşağıdaki
    run_classification_v2 bunu sağlar; bu fonksiyon geriye uyumluluk için
    burada tutulur.
    """
    raise NotImplementedError("run_classification_v2 kullanın")


def run_classification_v2(
    name: str,
    items: Sequence[tuple[str, str, Callable[[str], str]]],
    threshold: float = 0.80,
) -> POCResult:
    """items: [(input_text, gold_label, fn)] — her örneği fonksiyonla işler."""
    golds: list[str] = []
    preds: list[str] = []
    fails: list[dict] = []
    start = time.perf_counter()
    for text, gold, fn in items:
        pred = fn(text)
        golds.append(gold)
        preds.append(pred)
        if pred != gold:
            fails.append({"input": text[:80], "gold": gold, "pred": pred})
    runtime_ms = (time.perf_counter() - start) * 1000.0
    acc, prec, rec, f1 = classification_metrics(golds, preds)
    return POCResult(
        name=name, accuracy=acc, precision=prec, recall=rec, f1=f1,
        runtime_ms=runtime_ms / max(1, len(items)), n=len(items), fail_examples=fails[:5],
    )


def run_scalar(
    name: str,
    items: Sequence[tuple[float, Callable[[], float]]],
    tolerance: float = 1e-6,
    threshold: float = 0.80,
) -> POCResult:
    """Sayısal çıktı (örn. VI skoru) için doğruluk: |pred-true| <= tol olanlar."""
    correct = 0
    fails: list[dict] = []
    start = time.perf_counter()
    for truth, fn in items:
        pred = fn()
        if abs(pred - truth) <= tolerance:
            correct += 1
        else:
            fails.append({"truth": truth, "pred": pred})
    runtime_ms = (time.perf_counter() - start) * 1000.0
    n = len(items)
    acc = correct / n if n else 0.0
    return POCResult(
        name=name, accuracy=acc, precision=acc, recall=acc,
        f1=acc if acc >= threshold else max(acc, 0.0),
        runtime_ms=runtime_ms / max(1, n), n=n, fail_examples=fails[:5],
    )


def render_all(results: Iterable[POCResult]) -> str:
    lines = [r.render() for r in results]
    return "\n".join(lines)
