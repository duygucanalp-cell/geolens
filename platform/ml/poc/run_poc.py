"""A4-1 · Tüm PoC'ları koşturur; İP-08 çıktı raporunu üretir.

Kullanım:  python -m poc.run_poc [--out ml/data/poc_report.md]
"""

from __future__ import annotations

import argparse
import importlib
import json
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT))


def run_single(module_name: str) -> dict:
    mod = importlib.import_module(module_name)
    start = time.perf_counter()
    rc = 0
    output = ""
    if hasattr(mod, "main"):
        try:
            import contextlib
            import io

            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                rc = mod.main()
            output = buf.getvalue()
        except Exception as exc:  # noqa: BLE001
            rc = 1
            output = f"EXC: {exc}"
    return {
        "module": module_name,
        "rc": rc,
        "runtime_ms": round((time.perf_counter() - start) * 1000, 2),
        "output": output,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(REPO_ROOT / "data" / "poc_report.md"))
    parser.add_argument("--json", default=str(REPO_ROOT / "data" / "poc_report.json"))
    args = parser.parse_args()

    poc_names = ["poc.poc_citation", "poc.poc_entity", "poc.poc_recommendation",
                 "poc.poc_prompt", "poc.poc_visibility"]
    results = []
    failed = 0
    for name in poc_names:
        res = run_single(name)
        results.append(res)
        if res["rc"] != 0:
            failed += 1
            print(f"[FAIL={name}] {res['output']}")

    # Markdown rapor
    lines = [
        "## A4-1 PoC Raporu (İP-08)",
        "",
        "| PoC | Durum | Süre (ms) |",
        "|:--|:--:|:--:|",
    ]
    for r in results:
        status = "PASS" if r["rc"] == 0 else "FAIL"
        mod = r["module"].removeprefix("poc.")
        lines.append(f"| {mod} | {status} | {r['runtime_ms']} |")
    lines.append("")
    total_pass = len(results) - failed
    goal_ok = total_pass >= int(len(results) * 0.8)
    goal_txt = "hedef geçildi" if goal_ok else "hedef eksik: %80 gerekiyor"
    lines.append(f"Toplam: {total_pass}/{len(results)} PoC geçti ({goal_txt})")
    lines.append("")
    lines.append("### Detaylı metrikler")
    lines.append("")
    lines.append("```")
    lines.append("\n\n".join(r["output"] for r in results if r["output"]))
    lines.append("```")
    Path(args.out).write_text("\n".join(lines) + "\n", encoding="utf-8")
    Path(args.json).write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n".join(lines))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
