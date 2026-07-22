# 0206 · Post-MVP Yol Haritası

| Alan | Değer |
|---|---|
| Doküman ID | 0206 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0205, 0201, 0204, 0003, 0004, specification/docs/00-overview/0005-version-sync-plan |

---

## 1. Amaç

Bu doküman Faz 2'yi kapatır. MVP sonrası GeoLens ürün evrimini takvim tarihleriyle değil, **tetikleyici tabanlı pencerelerle** tanımlar.

Girdisi: 0205 §4.3'teki bilinçli açıklar, daraltılmış kapsamlar ve persona/paket iskeleti.

---

## 2. Yol Haritası İlkeleri

| # | İlke | Anlamı |
|---|------|--------|
| **Y1** | Tarih değil tetikleyici | Pencereler koşulla açılır (kapı kriteri, karar, veri eşiği). Takvim taahhüdü verilmez. |
| **Y2** | Öğrenme yeniden sıralar | Pilot ve M4 geri bildirimi pencere içi sırayı değiştirebilir. |
| **Y3** | Güven gevşemez | Sert kurallar (fidelite, açıklanabilirlik, izolasyon) hiçbir pencerede pazarlığa açılmaz. |
| **Y4** | Tek platform korunur | Her pencere kalemi paket haklarıyla açılır; kod dalı yaratılmaz (İ1). |
| **Y5** | Pencere gözetimi | TR fırsat penceresi (0101 §8) ve motor erişim seyri (0102) düzenli izlenir; sıralama buna duyarlıdır. |

---

## 3. Pencere Modeli

| Pencere | İçerik | Giriş Tetikleyicisi |
|---------|--------|---------------------|
| **HT1** · Hızlı Takip 1 | Masa bahisi kapanışları, daraltılmış kapsam genişlemeleri, ikinci halka motorlar | Pilot çıkış kapısı geçildi (0205 §7) |
| **HT2** · Hızlı Takip 2 | Ticari açılış tamamlayıcıları, benchmark, yönetim görünürlüğü | Genel açılış + bekleyen politika kararları |
| **Kurumsal Kapı** | SSO, SOC 2, genişletilmiş tarihçe, P1 aktif satış | SOC 2 Tip 1 + 12 ay üretim tarihçesi + kurumsal pilot sinyali |
| **Platform Ufku** | Öngörü, anomali tespiti, mobil, EN açılımı | Veri hacmi ve kanıt eşikleri |

---

## 4. Hızlı Takip 1 — HT1

Pilot çıkış kapısından hemen sonra açılır. MVP'nin bilinçli açıklarını kapatır ve daraltılmış kapsamları genişletir.

| Kalem | Değer Gerekçesi | Bağımlılık |
|-------|-----------------|------------|
| **FR-F6** — REST API | Masa bahisi kapanışı. Ajans BI ihtiyacı. | API sözleşme tasarımı (ADR) |
| **FR-F5** — Zamanlanmış rapor | Ajans operasyonunun tam otomasyonu. | Zamanlama katmanı (0307) |
| **FR-E4** — Öneri-etki takibi | Güven halkasını kapatır: öneri, etkisiyle görünür. | MVP'den biriken M4 işaretleri |
| **FR-G3** — Müşteri arşivleme | Ajans ölçeklenmesi. Çalışma alanı hijyeni. | — |
| **FR-D6** — Çok müşteri panoraması | Ajans görünümünün grafik panoramaya genişlemesi. | Pilot geri bildirimi |
| **Daraltılmış genişlemeler** | FR-B4 (site denetimi), FR-D3 (derin kıyas), FR-E1 (öneri kütüphanesi), FR-F2 (kural editörü) | Pilot önceliklendirmesi (Y2) |
| **İkinci halka motorlar** | Claude, Grok, Copilot | K1 maliyet payı; Grok kurumsal şartları |

---

## 5. Hızlı Takip 2 — HT2

Genel açılış sonrası. Ticari tamamlayıcılar ve self-serve olgunlaştırma.

| Kalem | Değer Gerekçesi | Bağımlılık |
|-------|-----------------|------------|
| **FR-D5** — Benchmark bağlamı | P2 çerçeveleme ihtiyacı. Kategori farklılaştırıcısı. | ≥5 kiracı eşiği + gizlilik yöntemi |
| **FR-A6** — Self-serve ödeme (Genişletilmiş) | P4/P2 hunisinin sürtünmesiz dönüşümü. Ticari açılışla birlikte. | Ödeme altyapısı kararı |
| **FR-H2** — Denetim izi görünümü | Yönetici şeffaflığı. Kurumsal kapı ön hazırlığı. | Kayıt (NFR-6) zaten tam |
| **Bildirim zenginleştirme** | Webhook çeşitlendirme, digest ayarları. | API ile uyum |
| **E-posta kişiselleştirme** | M3 ve e-posta→pano geçiş oranını artırır. | MVP M3 verisi |

---

## 6. Kurumsal Kapı

Bu pencere P1 aktif satışının açılışıdır (0201 §6 ertelemesinin sonu).

**Giriş tetikleyicisi (bileşik):**
1. SOC 2 Tip 1 raporu alınmış
2. Kesintisiz ≥12 ay üretim tarihçesi birikmiş
3. Kurumsal pilot kiracılarından satın alma sinyali doğrulanmış

**Kalemler:**
- **FR-A4** — SSO/SAML oturum açma
- **SOC 2** sertifikasyonu (Tip 1 → Tip 2; kontrol yolu MVP'den beri işliyor)
- Genişletilmiş tarihçe ve dışa aktarım paketleri
- Kurumsal onboarding ve güvenlik inceleme paketi

---

## 7. Platform Ufku

| Kalem | Değer Gerekçesi | Tetikleyici |
|-------|-----------------|-------------|
| **Tahmine dayalı görünürlük** | Trendden öngörüye geçiş. Olasılık diliyle. | Yeterli tarihçe hacmi |
| **Öğrenen öneri sıralaması** | M4 + etki verisiyle önerilerin beklenen etkiye göre sıralanması. | FR-E4 verisi olgunlaştı |
| **Anomali kök neden** | Uyarıdan açıklamaya: kaynak kırılımı korelasyonları. | M11 kalibrasyonu oturdu |
| **Yerel mobil uygulama** | P4/P5 bildirim yüzeyinin derinleşmesi. Kategoride hâlâ boşluk. | Responsive web etkileşim verisi |
| **EN pazar açılımı** | TR çekirdeği kanıtlandıktan sonra İngilizce pazar. | PMF sinyali (TR'de M2≥%80 + M1≥%60) |
| **Yeni motor yüzeyleri** | Asistan/ajan yüzeyleri. Kademe modeliyle etiketli. | Resmî erişim olgunluğu |

> **Ufuk sınırı:** Hiçbir kalem kullanıcı onayı olmadan otomatik site/içerik değişikliği uygulamaz. Öneri üretimi NG sınırları içinde kalır.

---

## 8. Riskler ve Yeniden Önceliklendirme

| Senaryo | Etki | Sıralama Tepkisi |
|---------|------|------------------|
| TR penceresi erken kapanır | Bilinirlik yarışı sertleşir | Savunulabilirlik öne çekilir: istatistik derinliği, metodoloji yayınları (GAVF), benchmark |
| Motor erişimi sertleşir | Bağdaştırıcı yatırımı riski artar | İkinci halka ertelenir; K2 vekil-korelasyon pilotu öne alınır |
| Ajans talebi beklenenden hızlı büyür | Ajans kalemleri darboğaz olur | FR-G3 ve panorama genişlemesi HT1 başına çekilir |
| Pilot çıkış kapısı gecikir/geçilemez | TR penceresi (0101 §8) daralır | HT1 girişi ertelenir. Pilot süresi uzatılır, kriterler revize edilir. |
| Kaynak kısıtı | Pencere içi kalemler seyrelir | Tek platform ilkesi kaydırma maliyetini düşük tutar |

---

## 9. Pencere-Metrik Bağları

| Pencere | Başarı Sinyali | Yeni Metrik İhtiyacı |
|---------|----------------|----------------------|
| **HT1** | M4 artışı, M10/M11 hedef sürdürme | Öneri sonrası yeniden ölçüm oranı |
| **HT2** | M1 büyümesi, e-posta→pano geçişi, paket geçişleri | Dönüşüm ve geçiş oranları |
| **Kurumsal** | Kurumsal pilot sinyalleri | Kurumsal değerlendirme döngü süresi |
| **Ufuk** | Tarihçe hacmi ve model kalibrasyonu | Öngörü isabeti metriği |

---

## 10. GeoLens İçin Çıkarımlar

1. **Faz 2 bu dokümanla tamamlanmıştır.** 0201-0206 seti Draft durumundadır. Approved geçişleri tanımlı kapılara bağlıdır (0201 saha doğrulaması, pilot çıkış kapısı).
2. **Specification bağlantısı:** Platform ufkundaki metodoloji yayınları ve GAVF güncellemeleri, specification reposunda ayrı bir yol haritasıyla yönetilir. Versiyon senkronizasyon detayları için §12'ye bakın.
3. **Faz 3 açılışı:** Sıradaki doküman 0301 System Architecture'dır. Bu yol haritasının pencere yapısı, mimari esneklik gereksinimi olarak Faz 3'e taşınır.
4. **Kurumsal kapı tetikleyicisi** SOC 2 yol haritasının önceliğini belirler. Kontrol yolu MVP'den itibaren işletilir.
5. **TR penceresi gözetimi** (0101 §8'deki 12-18 ay varsayımı) 0007 kadansında izlenir. Erken kapanma sinyali gelirse §8'deki sıralama tepkisi devreye girer.

---

## 12. GAVF Specification Versiyon Senkronizasyonu

Platform sürümleri ile GAVF Specification versiyonları arasındaki eşleme, `specification/docs/00-overview/0005-version-sync-plan.md` dokümanında ayrıntılı olarak tanımlanmıştır. Aşağıdaki tablo, platform pencere modelini GAVF versiyonlarına bağlar.

| Platform Penceresi | Sürüm Etiketi | GAVF Versiyonu | Değişiklik Türü | Kilit Karar |
|:------------------:|:-------------:|:---------------:|:----------------:|:-----------:|
| **MVP** | `v1.0.0` | `1.0.0` | İlk eşzamanlı yayın | GAVF Temel + İleri seviye uyumluluğu sağlanır |
| **HT1** | `v1.1.0` | `1.0.x` (patch) veya değişmez | GAVF değişikliği yok | S4 derinleşmesi spec değişikliği gerektirmez |
| **HT2** | `v1.2.0` | `1.1.0` (minor adayı) | GAVF minor — yeni metodoloji | Benchmark, öz değerlendirme araçları spec'e geri beslenir |
| **Kurumsal** | `v2.x.0` | `1.x.0` veya `2.0.0` | Karara bağlı | SOC 2 ile birlikte GAVF sertifikasyon süreci başlar |
| **Ufuk** | `v2.y.z` | `1.x.0`+ | Yeni katmanlar | Tahmin, anomali gibi yeni bileşenler spec'e eklenir |

### Senkronizasyon Kuralları (Özet)

| # | Kural |
|:-:|------|
| SK-1 | Platform ve Specification bağımsız versiyonlanır; yalnızca GAVF etkileyen değişiklikler senkronizasyon gerektirir |
| SK-2 | Skor algoritması değişikliği → GAVF major, yeni skor bileşeni → GAVF minor, düzeltme → GAVF patch |
| SK-3 | Aynı anda yalnızca bir tarafta major versiyon değişikliği yapılır |
| SK-4 | Specification yayını öncesi platformun GAVF uyumluluk testlerini (spec/0304) geçtiği doğrulanır |
| SK-5 | Her platform release notu, hangi GAVF versiyonuyla uyumlu olduğunu belirtir |

### Release Notu Formatı

Her platform sürüm notu şu bilgiyi içerir:

```
## GAVF Uyumluluk
- GAVF Versiyonu: 1.0.0
- Uyumluluk Seviyesi: Temel + İleri
- Değişiklik: Major/Minor/Patch
- Specification Tag: gavf-1.0.0
```

### İlgili Doküman

Tüm detaylar (olay-matrisi, CI/CD entegrasyonu, geçiş senaryoları) için:
[specification/docs/00-overview/0005-version-sync-plan.md](../../specification/docs/00-overview/0005-version-sync-plan.md)

---

## 13. Açık Sorular

| ID | Soru | Not |
|----|------|-----|
| O-1 | Yerel mobil uygulama değerlendirme kriterleri | Responsive web etkileşim verisiyle; ADR kararı. |
| O-2 | EN pazar açılımı tetikleyici eşikleri | PMF sinyali bileşik: TR'de M2 ve M1 hedefleri + gelen talep. |

---

## Kaynaklar

- 0205 MVP Scope — bilinçli açıklar, daraltılmış kapsamlar, pilot çıkış kapısı
- 0201 User Personas — paket iskeleti, segment önceliği, kurumsal kapı koşulu
- 0204 PRD — FR/NFR öncelikleri
- 0101 Pazar Analizi — TR pencere varsayımı
- 0102 Rekabet Analizi — motor kademe modeli

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform post-MVP yol haritası. 5 yol haritası ilkesi, 4 pencereli tetikleyici modeli, HT1/HT2/kurumsal/ufuk kalemleri, risk senaryoları. Faz 2 kapanışı. |
| 1.1 | 22.07.2026 | §12 GAVF Specification versiyon senkronizasyonu eklendi: her platform penceresi için GAVF versiyon eşlemesi, 5 senkronizasyon kuralı (SK-1–SK-5), release notu formatı. İlişkili alanına specification sync plan referansı eklendi. |
