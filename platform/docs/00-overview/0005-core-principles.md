# 0005 · Temel İlkeler

| Alan | Değer |
|---|---|
| Doküman ID | 0005 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0000, 0001, 0204, 0102 |

---

## 1. Amaç

GeoLens ürününün temel ilkelerini tanımlar. Tüm ürün, mimari ve iş kararları bu ilkelere göre değerlendirilir.

---

## 2. Ürün İlkeleri

| # | İlke | Anlamı |
|---|------|--------|
| **P1** | Açıklanabilirlik | Her skor nedenini gösterir. Kara kutu yok. |
| **P2** | Dürüstlük | Ne ölçtüğümüzü, nasıl ölçtüğümüzü ve sınırlarımızı açıkça söyleriz. |
| **P3** | Bağımsızlık | Tek bir motora bağımlı değiliz. Çapraz-motor tarafsız katmanız. |
| **P4** | Ölçeklenebilirlik | 10M prompt/gün, 1.000 kurumsal müşteri için tasarlanır. |
| **P5** | Güvenlik-ilk | Multi-tenancy, RBAC, denetim izi, şifreleme — ilk günden. |
| **P6** | TR-öncelik | Ürün Türkçe-öncelikli tasarlanır; ikinci dil altyapısı hazırdır. |
| **P7** | Açık standart | GAVF, herkesin kullanabileceği açık bir standarttır. |

---

## 3. Tasarım Filtreleri

Her karar aşağıdaki altı filtreyle test edilir:

| # | Filtre | Soru |
|---|--------|------|
| **F1** | 5 yıl testi | Bu karar 5 yıl sonra hâlâ doğru olur mu? |
| **F2** | Ölçek testi | 10M prompt/gün, 1.000 kurumsal müşteri çalıştırabilir mi? |
| **F3** | Kurumsal test | Multi-tenancy, RBAC, KVKK/GDPR, SOC 2 uyumlu mu? |
| **F4** | Patent testi | Patentlenebilir bir yaklaşım içeriyor mu? |
| **F5** | Moat testi | Rakiplerin kopyalaması zor mu? Ekonomik hendek güçleniyor mu? |
| **F6** | Kategori testi | Bizi "özellik" olmaktan çıkarıp "kategori adı" olmaya yaklaştırıyor mu? |

---

## 4. Sınırlar

| # | Sınır | Açıklama |
|---|-------|----------|
| **S1** | Garanti yok | Olasılıksal ölçüm satılır, garanti satılmaz. |
| **S2** | Manipülasyon yok | Motor politikalarına aykırı taktik önerilmez. |
| **S3** | Yetkisiz veri yok | Yalnızca resmî API'ler. Kazıma yapılmaz. |
| **S4** | Kullanıcı onayı | Otomatik site/içerik değişikliği yapılmaz. |

---

## 5. GeoLens İçin Çıkarımlar

1. **P1-P7** tüm ürün kararlarının referansıdır. 0204 PRD'deki İ1-İ6 ilkeleri buradan türetilmiştir.
2. **F1-F6** filtreleri master plan (0000) ile tanımlanmıştır ve tüm dokümanlarda kullanılır.
3. **P7 (açık standart)** GeoLens'in en uzun vadeli farklılaştırıcısıdır.
4. **S1-S4** pazarlıksız sınırlardır. Hiçbir müşteri veya pazar baskısı bu sınırları esnetmez.

---

## 6. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Yeni bir ilke ekleme süreci nasıl işler? | ⏳ AVIP 0007 §3 (Tip 1/Tip 2 karar süreci) devralındı. İlke ekleme Tip 1 karar olarak işler: yazılı öneri → itiraz penceresi → PO onayı. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-81** | **Sentiment V1 kapsamı dışı.** Mention+öneri+alıntı MVP için yeterli. PO 21.07.2026. | AVIP 0005 O-2 |

---

## Kaynaklar

- 0000 Master Plan — 6 tasarım filtresi
- 0001 Vizyon — ürün felsefesi
- 0204 PRD — İ1-İ6 ilkeleri
- archive/avip-v1/0005-glossary.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens temel ilkeleri. P1-P7 ürün ilkeleri, F1-F6 tasarım filtreleri, S1-S4 sınırlar. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-81 (sentiment V1 dışı). Devralınan Kararlar eklendi. |
