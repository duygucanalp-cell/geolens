# 0004 · Success Metrics

| Alan | Değer |
|---|---|
| Doküman ID | 0004 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.1 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 18 Temmuz 2026 |
| Karşıladığı madde | 6 · Başarı metrikleri |
| İlişkili | 0002, 0003, 0307, 0308, 0309, 0310, 0311 |

---

## 1. Amaç ve Kapsam

Bu doküman, 0003'teki G1–G9 hedeflerini ölçülebilir kılan metrik setini tanımlar: her metriğin formülü, veri kaynağı ve başlangıç eşiği. Finansal metrikler (gelir, fiyat) doküman setinin kapsamı dışındadır. [K] işaretli eşikler başlangıç kalibrasyonudur; pilot verisiyle revize edilir, formüller sabit kalır.

## 2. Metrik Çerçevesi

Her metrik dört kurala uyar: bir hedefe (G) bağlanır; formülü ve veri kaynağı tanımlıdır; tek bir sahibi vardır; eşik değişikliği 0007 süreciyle yapılır ve changelog'a işlenir. Metrikler beş kategoriye ayrılır: benimseme, ölçüm güvenilirliği, atıf, izleme sürekliliği ve kurumsal operasyon. Goodhart riski (metriğin kendisinin hedefe dönüşmesi) 7. bölümdeki gözden geçirme ritmiyle yönetilir.

## 3. North Star Metriği

> Haftalık Aktif Kiracı oranı (WAT%): son 7 günde en az bir görünürlük raporunu inceleyen ve en az bir skor veya kaynak detayına inen kiracıların, toplam aktif sözleşmeli kiracıya oranı.

Gerekçe: ürün, ekiplerin haftalık karar ritmine girdiğinde değer üretir (0001 §7 başarı tanımı). WAT% hem kullanım derinliğini (detaya inme) hem düzenliliği (haftalık) yakalar. Pilot fazında eşik: pilot kiracıların en az yüzde 60'ı, 8 haftalık pilot boyunca haftalık aktif kalır [K]. North Star, platform fazında yeniden değerlendirilir (0206).

## 4. Metrik Kataloğu

### A. Benimseme ve değer (G4, G8)

| ID | Metrik ve formül | Başlangıç eşiği | Bağ |
|---|---|---|---|
| M1 | WAT%: haftalık aktif kiracı / toplam kiracı (North Star, §3 tanımı). | Pilotta ≥ %60 [K] | G8 |
| M2 | Pilot tamamlama: pilotu 8 haftalık planında tamamlayan kiracı oranı. | ≥ %80 [K] | G8 |
| M3 | İçgörü tüketimi: kiracı başına haftalık rapor görüntüleme sayısı (medyan). | ≥ 3 [K] | G8 |
| M4 | Öneri etkileşimi: incelenen önerilerin "uygulandı" veya "reddedildi" olarak işaretlenme oranı (geri besleme döngüsü kapanışı). | ≥ %40 [K] | G4 |

### B. Ölçüm güvenilirliği (G1, G2; H3 doğrulaması)

| ID | Metrik ve formül | Başlangıç eşiği | Bağ |
|---|---|---|---|
| M5 | Örnekleme kararlılığı: aynı prompt ve motor için n tekrarlı ölçümde skor güven aralığı genişliği (0309 örnekleme tasarımıyla). | ≤ ±5 puan @ %95 GA [K] | G1, H3 |
| M6 | Hesap tekrarlanabilirliği: aynı calculation_run girdileriyle yeniden hesaplamada birebir aynı skor (deterministik hesap katmanı). | %100 (sert kural) | G2 |
| M7 | İzlenebilirlik kapsaması: calculation_run_id, faktör anlık görüntüsü ve şablon versiyonu taşıyan skor oranı. | %100 (sert kural) | G2 |
| M8 | Motor ölçüm başarısı: planlanan motor çağrılarının başarıyla tamamlanma oranı (motor bazında raporlanır). | ≥ %97 [K] | G1 |

### C. Atıf ve kaynak analizi (G3)

| ID | Metrik ve formül | Başlangıç eşiği | Bağ |
|---|---|---|---|
| M9 | Alıntı çözümleme oranı: yanıtlarda tespit edilen kaynakların başarıyla çıkarılıp sınıflandırılma oranı. | ≥ %90 [K] | G3, H4 |

### D. İzleme sürekliliği (G5, G6)

| ID | Metrik ve formül | Başlangıç eşiği | Bağ |
|---|---|---|---|
| M10 | Zamanlanmış ölçüm zamanındalığı: planlanan pencerede tamamlanan zamanlanmış ölçüm oranı (0307). | ≥ %99 [K] | G6 |
| M11 | Uyarı isabeti: anlamlı değişim uyarılarında kullanıcı tarafından "yanlış alarm" işaretlenme oranı. | ≤ %20 [K] | G6 |

### E. Kurumsal operasyon (G7)

| ID | Metrik ve formül | Başlangıç eşiği | Bağ |
|---|---|---|---|
| M12 | Tenant izolasyon ihlali: otomatik izolasyon test paketinde (0404) tespit edilen ihlal sayısı. | 0 (sert kural) | G7 |
| M13 | Erişilebilirlik: aylık uptime (0311 izlemesi). | ≥ %99.5 [K] | G7 |
| M14 | Denetim izi kapsaması: kritik işlemlerin (giriş, veri erişimi, konfigürasyon) audit log kaydı oranı. | %100 (sert kural) | G7 |

G9 (kategori referansı) platform ufkundadır; metriği 0206 ile birlikte tanımlanır.

### Aday metrikler (v1.1; 0202 §10 devri)

| Aday | Tanım (kalibrasyon pilotta) |
|---|---|
| TTFV | Kayıttan ilk etiketli skora geçen süre (ilk değer anı; 0202 §5) |
| Aktivasyon oranı | Yedi adımlı kurulum omurgasını tamamlayan kiracı payı |
| E-posta→pano geçişi | Haftalık özetten derin bağlantıyla panoya geçiş oranı (M1 kaynağı; 0306 §8) |
| Yeniden ölçüm oranı | Öneri uygulaması sonrası manuel yeniden ölçüm tetikleme payı |
| Tavsiye payı | Yeni kiracılarda kaynak olarak mevcut kullanıcı tavsiyesi |

Bu tanımlar adaydır; resmileştirme ayrı sürüm kararıyla yapılır.

## 5. Koruma Metrikleri (Guardrails)

Aşağıdakiler hedef değil sınırdır; ihlali, ilgili hedefin duraklatılmasını tetikler.

| ID | Koruma | Tetiklediği aksiyon |
|---|---|---|
| K1 | Birim ölçüm maliyeti trendi: prompt × motor × örnek başına motor çağrı maliyeti artış eğilimine girmez (0308 kota ve maliyet yönetimi). | Örnekleme planı ve motor kapsamı gözden geçirilir (R-03). |
| K2 | Motor politika uyumu: yalnızca izinli erişim yöntemleri; yetkisiz toplama sıfır (NG9). | İlgili motor entegrasyonu durdurulur (R-04). |
| K3 | Veri tazeliği: kiracıya gösterilen skorun yaşı tanımlı pencereyi aşmaz (pencere 0205'te). | Pano üzerinde bayatlık uyarısı, öncelikli yeniden ölçüm. |

## 6. Ölçüm Altyapısı

Metriklerin veri kaynakları: M1–M4 ürün telemetrisi (olay bazlı analitik); M5–M9 platform veri tabanındaki calculation_runs ve yanıt arşivi; M10 zamanlayıcı ve kuyruk günlükleri (0307); M11 kullanıcı geri bildirim olayları; M12 otomatik test hattı (0403, 0404); M13–M14 gözlemlenebilirlik katmanı (0311). Tüm metrikler kiracı bazında ayrıştırılabilir olmalıdır; toplulaştırma yalnızca raporlama içindir.

## 7. Kalibrasyon ve Gözden Geçirme

[K] işaretli eşikler pilot süresince iki haftada bir, MVP sonrası ayda bir gözden geçirilir. M6, M7, M12 ve M14 sert kuraldır; eşik pazarlığına kapalıdır. Eşik değişikliği 0007 karar süreciyle yapılır, gerekçesiyle changelog'a işlenir. H3 doğrulama pilotu (0309), M5 eşiğinin ilk gerçek kalibrasyonunu üretir; pilot öncesi M5 değeri taahhüt değil tasarım hedefidir.

## 8. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Pilot kiracı sayısı ve segmenti (M1, M2 paydası) | 0003 O-1 ile birlikte; 0201 persona kararı sonrası. |
| O-2 | Örnekleme büyüklüğü n ve ölçüm sıklığı | 0309 pilotunda maliyet (K1) ve kararlılık (M5) dengesiyle. |
| O-3 | Uptime hedefinin sözleşmesel SLO'ya dönüşüp dönüşmeyeceği | Kurumsal müşteri gereksinimlerine göre; 0310 ile. |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: North Star (WAT%), M1–M14 kataloğu, K1–K3 koruma metrikleri, sert kural ve [K] kalibrasyon ayrımı, ölçüm altyapısı eşlemesi. |
| 1.1 | 18.07.2026 | Aday metrik bölümü eklendi: TTFV, aktivasyon oranı, e-posta→pano geçişi, yeniden ölçüm oranı, tavsiye payı (0202 §10 devri). Tanımlar kalibrasyon öncesi adaydır. |
