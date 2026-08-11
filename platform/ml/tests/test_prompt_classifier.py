"""A2-3 · Prompt sınıflandırıcı eğitim pipeline testleri.

Gerçek eğitim (tam veri) CI'da yapılır; burada küçük veriyle pipeline'ın
çalıştığı ve export'un test edilebilir olduğu doğrulanır.
"""

from geolens.prompt_classifier.train import train_target


def test_train_target_small(tmp_path):
    records = []
    sectors = ["telekom", "finans"]
    for i, sec in enumerate(sectors):
        for intent in ["presence", "comparison"]:
            records.append({"id": f"p{i}{intent}", "text": f"{sec} hakkında {intent} soru", "intent": intent})
    model = train_target(records, "intent", seed=42)
    pred = model.predict(["telekom hakkında presence soru"])
    assert pred[0] == "presence"


def test_train_target_missing_label_ignored(tmp_path):
    records = [
        {"id": "p1", "text": "metin1", "intent": "presence"},
        {"id": "p2", "text": "metin2", "intent": "comparison"},
    ]
    model = train_target(records, "intent", seed=7)
    assert model is not None
