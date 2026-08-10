# 0114 · Konuşma Tekrarı ve Yanıt Arşivi Standardı

| Alan | Değer |
|---|---|
| Doküman ID | 0114 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 10 Ağustos 2026 |
| İlişkili | 0103 (S2), 0000 (master plan §4), platform/docs/0415 |

---

## 1. Amaç

AI motor yanıtlarının **anlık görüntüsünü** ve **versiyonlu arşivini** saklama, yeniden oynatma (replay) ve denetim amacıyla kullanma standardını tanımlar. GAVF Yanıt Standardı (S2) kapsamındadır.

## 2. Conversation Replay

Replay, bir ölçümün üretildiği ham koşulları (prompt, motor, örnekleme indeksi, zaman) koruyarak yanıtın **birebir yeniden incelenmesini** sağlar.

### 2.1 Zorunlu Alanlar

| Alan | Açıklama |
|------|----------|
| `replay_id` | Tekil kayıt kimliği |
| `tenant_id` / `workspace_id` | Kiracı bağlamı |
| `brand_id` | Marka |
| `engine` | Motor adı |
| `prompt` | Gönderilen prompt |
| `sample_index` | n örnekleme içindeki indeks |
| `response` | Ham yanıt içeriği |
| `captured_at` | Yakalama zamanı (UTC) |

## 3. Response Archive

Ham yanıtlar arşivlenir ve ölçümün yeniden hesaplanabilirliğini (determinizm) kanıtlar.

- Arşiv nesnesi: ham yanıt + alıntılar + motor meta verisi.
- S3 uyumlu depolamada tutulur; nesne referansı skor kaydında `s3_ref` olarak saklanır.
- Şifreleme (beklemede) AES-256-GCM önerilir (kripto-silme desteği).

## 4. Saklama Politikaları

| Veri | Varsayılan Saklama | Not |
|------|:------------------:|-----|
| Ham yanıt (archive) | 12 ay | KVKK/GDPR uyumu |
| Replay kaydı | 12 ay | Denetim için |
| Skor kaydı | Kalıcı | Versiyonlanmış |

- Kiracı silme talebi (KVKK) durumunda arşiv ve replay kayıtları anonimleştirilir veya silinir.
- Saklama süreleri kiracı bazında yapılandırılabilir (retention politikası).

## 5. Veri Formatı

```json
{
  "replay_id": "rep-001",
  "tenant_id": "t-1",
  "workspace_id": "ws-1",
  "brand_id": "brand-1",
  "engine": "perplexity",
  "prompt": "Acme hakkında ne biliyorsun?",
  "sample_index": 0,
  "response": "Acme, sektöründe öncü bir firmadır...",
  "citations": ["https://example.com/acme"],
  "captured_at": "2026-08-10T12:00:00Z"
}
```

## 6. GAVF Uyumu

- S2 kapsamında arşiv, ölçümün tekrarlanabilirliği (S2 ilkesi) ve denetim izi (S3 sürüm bağımsızlığı) için zorunlu altyapıdır.
- Uyumluluk seviyeleri (0109): Temel → arşiv yok; İleri → arşiv + replay; Tam → şifreli arşiv + kripto-silme + saklama politikası.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 10.08.2026 | İlk yayın: replay zorunlu alanları, arşiv nesnesi, saklama politikaları, veri formatı, GAVF uyumu. Platform 0415'ten türetilmiştir. |
