# 0605 · Veri Saklama (Data Retention)

| Alan | Değer |
|---|---|
| Doküman ID | 0605 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0601, 0602, 0310, 0204, 0206 |

---

## 1. Amaç

Bu doküman GeoLens Platform veri saklama politikalarını tanımlar: saklama süreleri, depolama sınıfı geçişleri, silme prosedürleri ve KVKK/GDPR uyumu.

---

## 2. Saklama Süreleri

| Veri Kategorisi | PostgreSQL | S3 Arşivi | Toplam | Gerekçe |
|:---------------:|:----------:|:---------:|:------:|---------|
| Ham yanıt (raw) | 30 gün (meta) | 30g STANDARD → 90g GLACIER → sil | 120 gün | Maliyet-uyum dengesi (30+90) |
| Skor | Süresiz | — | Süresiz | Trend analizi |
| Alıntı | Süresiz | — | Süresiz | Kaynak analizi |
| Rapor (PDF) | 1 yıl (meta) | 1 yıl → sil | 1 yıl | Müşteri erişimi |
| Kullanıcı verisi | KVKK sil talebine kadar | — | KVKK sonrası anonimleştirme | Yasal uyum |
| Denetim izi | Süresiz | — | Süresiz | NFR-6, yasal |
| Geçici (outbox) | 7 gün | — | 7 gün | Dağıtım sonrası |
| Oturum | 7 gün | — | 7 gün | Güvenlik |

---

## 3. Depolama Sınıfı Geçişi (S3)

| Süre | Depolama Sınıfı | Maliyet |
|:----:|:----------------:|:-------:|
| 0-30 gün | STANDARD | Normal |
| 31-90 gün | GLACIER | Düşük |
| 90+ gün | Sil | —

---

## 4. KVKK/GDPR Silme Prosedürü

| Veri Türü | Yöntem | Detay |
|:---------:|:------:|-------|
| Kullanıcı hesabı | Anonimleştirme | İsim/e-posta değiştirilir; denetim izi korunur |
| Kiracı verisi | Kripto-silme | Zarf anahtarı imha edilir; S3 verisi erişilemez olur |
| Ham yanıtlar | Kripto-silme | Zarf anahtarı imha edilir |

---

## 5. Otomatik Temizleme İşleri

| İş | Periyot | Sorumlu |
|:--:|:-------:|---------|
| Bayat outbox temizliği | Günlük | governance |
| Süresi dolmuş rapor silme | Haftalık | governance |
| S3 GLACIER geçişi | Aylık | governance |
| KVKK bekleme kontrolü | Haftalık | governance |

---

## 6. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-54 | Saklama süreleri: ham veri 30gün STANDARD → 90gün GLACIER → sil. Bu doküman §2 ile birebir uyumlu. | AVIP 0303 O-2 (TL+PO 21.07.2026) |
| D-62 | Saklama politikası: D-54 ile uyumlu; rapor PDF'leri 1 yıl saklanır. Bu doküman §2'de embed. | AVIP 0204 O-4 (TL+PO 21.07.2026) |
| D-58 | KVKK silme prosedürü: kripto-silme (zarf anahtarı imhası). Bu doküman §4'te embed. | AVIP 0303 O-3 (TL 21.07.2026) |
| D-55 | Telafi/özet mekanizması: ham yanıt silindikten sonra özet metin korunur. Bu doküman kapsamında geçerlidir. | AVIP 0307 O-1 (TL 21.07.2026) |

---

## Kaynaklar

- 0601 Data Model — veri kategorileri
- 0310 Security — KVKK/GDPR, kripto-silme, anonimleştirme
- 0204 PRD — NFR-12 (veri silme/dışa aktarım)
- 0206 Roadmap — saklama politikası kararları
- archive/avip-v1/0303-database-design.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: saklama süreleri, S3 sınıf geçişi, KVKK prosedürü, otomatik temizleme. |
| 1.1 | 23.07.2026 | Devralınan AVIP Kararları eklendi: D-54 (saklama süreleri), D-62 (saklama politikası), D-58 (KVKK silme), D-55 (telafi/özet). |
