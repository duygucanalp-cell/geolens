"""A1-5 · entity_rules testleri (regex extraction)."""

from geolens.features.entity_rules import extract_brands, extract_entities, format_entities


def test_extract_brand_case_insensitive():
    assert "MobiTel" in extract_brands("Mobitel yeni tarifesini duyurdu")
    assert "Turkcell" in extract_brands("turkcell ve Vodafone kıyaslanıyor")


def test_extract_brands_multi():
    hits = extract_brands("FinBank ile AktifKredi hakkında bilgi ver")
    assert hits == ["AktifKredi", "FinBank"]


def test_extract_entities_sector_and_money():
    e = extract_entities("Telekom sektöründe 50% büyüme, 2.5 milyar TL gelir 2023'te")
    assert "telekom" in e["sector"]
    assert e["percent"]
    assert e["money"]
    assert "2023" in e["date"]


def test_format_entities_dedup():
    raw = {"brand": ["MobiTel", "MobiTel"], "date": []}
    out = format_entities(raw)
    assert out["brand"] == ["MobiTel"]
    assert out["date"] == []


def test_extract_no_entities():
    e = extract_entities("Merhaba dünya, bugün hava güzel")
    assert all(not v for k, v in e.items())
