# GeoLens ML serving testleri — ModelRegistry davranışını doğrular.

from geolens.serving.registry import ModelRegistry


def test_registry_empty():
    r = ModelRegistry()
    assert r.ids() == []
    assert r.get("sentiment") is None


def test_registry_load_and_get(tmp_path):
    r = ModelRegistry()
    # ONNX dosyası gerçekte Aşama 2'de üretilir; şablon yükleme akışını test ederiz.
    (tmp_path / "model.onnx").write_bytes(b"fake")
    r.load("sentiment", str(tmp_path / "model.onnx"))
    assert r.ids() == ["sentiment"]
    assert r.get("sentiment") is not None
    assert r.get("sentiment").version == "1.0.0"
    assert r.get("sentiment").model_id == "sentiment"


def test_registry_version_map():
    from geolens.serving.registry import ModelRegistry as MR

    r = MR()
    assert r._version_for("prompt_intent") == "1.0.0"
    assert r._version_for("sentiment") == "1.0.0"
    assert r._version_for("yok") == "0.0.0"
