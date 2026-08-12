"""A1-2 · Gerçek veri etiketleme pipeline'ı: etiketlenmemiş export (0421).

Sentetik gold.jsonl (generate_gold.py) yalnızca şema + ölçüm pipeline'ı içindir —
asıl hedef, gerçek AI motor cevaplarının (measure.raw_responses) insan tarafından
etiketlenmesidir (0420 İP-02: IAA >%90). Bu araç:

  1. Platform PostgreSQL'inden gerçek motor cevaplarını okur
     (tenant/workspace/brand filtresi + `--limit` ile örnekleme).
  2. GoldRecord şemasında *etiketlenmemiş* kayıtlar üretir:
     prompt, response_text, engine, lang dolu; mention/citation/entity/
     recommendation/hallucination alanları boş — etiketleyici doldurur.
  3. Çıktı: data/real/real_<date>.jsonl (etiketlenecek çalışma seti).

Akış:
  python data/export_unlabeled.py --dsn "$DATABASE_URL" --out real/real_20260812.jsonl
  # etiketleyiciler şablonu doldurur → real/annotator1_*.jsonl, annotator2_*.jsonl
  python data/iaa.py --label sentiment real/annotator1.jsonl real/annotator2.jsonl
  python data/validate_labeled.py real/annotator1.jsonl   # şema doğrulama

Not: --dsn verilmezse örnek (sentetik) veriyle çalışır — geliştirme/tests için.
"""
from __future__ import annotations

import argparse
import json
import os
from datetime import datetime

# Boş etiketleme şablonu — etiketleyicinin doldurduğu alanlar (GoldRecord şeması).
EMPTY_LABELS = {
    "expected_summary": "",
    "mentions": [],
    "citations": [],
    "entities": [],
    "recommendation": None,
    "hallucination": {"type": "none", "severity": None},
}


def _lang_hint(text: str) -> str:
    """Basit dil tespiti (TR/EN) — sadece etiketleyiciye ön bilgi, etiket değil."""
    tr_markers = ("ş", "ğ", "ı", "ö", "ü", "ç", " ve ", " bir ", "hakkında")
    en_markers = (" the ", " is ", " about ", " and ", " of ")
    tr = sum(t in text.lower() for t in tr_markers)
    en = sum(t in text.lower() for t in en_markers)
    return "tr" if tr >= en else "en"


def export_from_db(dsn: str, tenant_id: str, workspace_id: str, brand_id: str, limit: int) -> list[dict]:
    """measure.raw_responses'tan gerçek cevapları çeker ve etiketlenmemiş kayıt üretir."""
    import psycopg

    with psycopg.connect(dsn) as conn:
        rows = conn.execute(
            """
            SELECT rr.id, rr.engine_name, rr.content_text, rr.prompt_text,
                   rr.brand_id, rr.workspace_id, rr.tenant_id
            FROM measure.raw_responses rr
            WHERE ($1 = '' OR rr.tenant_id = $1)
              AND ($2 = '' OR rr.workspace_id = $2)
              AND ($3 = '' OR rr.brand_id = $3)
              AND rr.prompt_text <> ''
            ORDER BY rr.created_at DESC
            LIMIT %s
            """,
            (tenant_id, workspace_id, brand_id, limit),
        ).fetchall()

    records: list[dict] = []
    for i, (rid, engine, content, prompt, b_id, w_id, t_id) in enumerate(rows):
        records.append(
            {
                "id": f"real_{rid[:8]}",
                "lang": _lang_hint(content or ""),
                "prompt_id": "",
                "prompt": prompt or "",
                "engine": engine,
                "response_text": content or "",
                # Export metadata — etiketleyici kararını kolaylaştırır.
                "_source": {"db_id": rid, "tenant_id": t_id, "workspace_id": w_id, "brand_id": b_id},
                **EMPTY_LABELS,
            }
        )
    return records


def _sample_records(n: int) -> list[dict]:
    """DB olmadan geliştirme/tests için örnek etiketlenmemiş kayıtlar."""
    samples = [
        ("perplexity", "MobiTel pazarda güçlü bir konuma sahiptir ve ürünleri yaygın olarak takdir edilmektedir.", "MobiTel hakkında bilgi ver"),
        ("chatgpt", "MobiTel 2023'te %30 büyüme iddia etmektedir ancak bu bilgi doğrulanmamıştır.", "MobiTel'in büyümesi hakkında ne biliyorsun?"),
        ("gemini", "Not much is known about MobiTel; it is just one of many players in the sector.", "What do you know about MobiTel?"),
    ]
    out = []
    for i, (engine, content, prompt) in enumerate(samples[:n]):
        out.append(
            {
                "id": f"real_sample_{i + 1:03d}",
                "lang": _lang_hint(content),
                "prompt_id": "",
                "prompt": prompt,
                "engine": engine,
                "response_text": content,
                **EMPTY_LABELS,
            }
        )
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description="Gerçek motor cevaplarını etiketlenmemiş gold şablonlarına export et")
    data_dir = os.path.dirname(__file__)
    parser.add_argument("--dsn", default=os.environ.get("DATABASE_URL", ""), help="PostgreSQL DSN (boşsa örnek veri)")
    parser.add_argument("--tenant", default="", help="tenant_id filtresi (boş = tümü)")
    parser.add_argument("--workspace", default="", help="workspace_id filtresi (boş = tümü)")
    parser.add_argument("--brand", default="", help="brand_id filtresi (boş = tümü)")
    parser.add_argument("--limit", type=int, default=200, help="maksimum kayıt sayısı")
    parser.add_argument("--out", default=os.path.join(data_dir, "real", f"real_{datetime.now():%Y%m%d}.jsonl"))
    args = parser.parse_args()

    records = (
        export_from_db(args.dsn, args.tenant, args.workspace, args.brand, args.limit)
        if args.dsn
        else _sample_records(min(args.limit, 3))
    )
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        for r in records:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"{len(records)} etiketlenmemiş kayıt yazıldı: {args.out}")
    print("Sonraki adım: kayıtları doldur (mention/citation/entity/hallucination) ve")
    print("  python data/iaa.py --label sentiment <annotator1>.jsonl <annotator2>.jsonl")


if __name__ == "__main__":
    main()
