# 0202 · Kullanıcı Yolculukları

| Alan | Değer |
|---|---|
| Doküman ID | 0202 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0201, 0203, 0204, 0205, 0307, 0004 |

---

## 1. Amaç

Bu doküman, 0201'de tanımlanan personaların GeoLens ile yaşam döngüsünü haritalar. Odak: **P3 (ajans)** ve **P2 (KOBİ)** — V1'in iki birincil ticari hedefi.

**Çıktıları:** 0203 (Use Cases) için aktör-adım envanteri, 0204 (PRD) için gereksinim adayları, 0205 (MVP) için kesit karar girdisi.

> **Tasarım filtresi bağlantısı:** Bu doküman özellikle **F2** (ölçek — yolculukların tekrarlanabilirliği) ve **F1** (5 yıl — her yolculuğun uzun vadeli sürdürülebilirliği) filtrelerine kanıt sağlar.

---

## 2. Yolculuk Çerçevesi

Her yolculuk altı aşamada haritalanır:

1. **Keşif** — Kullanıcı GeoLens'i nasıl bulur?
2. **Kurulum** — İlk oturumda neler olur?
3. **İlk Değer** — Kullanıcı ne zaman "işe yaradı" der?
4. **Haftalık Ritim** — Sürekli kullanım alışkanlığı nasıl oluşur?
5. **Genişleme** — Kullanıcı nasıl büyür (daha çok marka, daha çok müşteri)?
6. **Savunuculuk** — Kullanıcı nasıl referans olur?

**İki tasarım ilkesi:**

- **Güven anı:** Fidelite etiketi, güven aralığı ve kanıt derecesi kullanıcıya pazarlama sayfasında değil, **ürünün içinde ilk skorla birlikte** öğretilir. Bu, 0102'deki fidelite kuralının deneyime inişidir.
- **Haftalık ritim hedefi:** North Star metriği (WAT%) "raporu inceleyen ve detaya inen" kiracıyı sayar. Tüm yolculuklar kullanıcıyı haftalık karar ritmine taşıyacak biçimde kurgulanır.

---

## 3. Ortak Omurga: Kayıttan İlk Değere

Tüm personalar aynı çekirdek akıştan geçer. Farklar paket haklarıyla açılır (tek platform ilkesi, 0201 §2):

| # | Adım | Tasarım | Not |
|---|------|---------|-----|
| **1** | Kayıt | Düşük sürtünmeli kayıt (e-posta + şifre). Ödeme bilgisi istenmez. | Self-serve için kritik |
| **2** | Marka tanımı | İzlenecek marka adı, alan adı, rakipler. TR pazar seçimi varsayılan. | Ölçüm bölgesi tanımı |
| **3** | Prompt seti kurulumu | Sektöre göre TR-öncelikli şablon kütüphanesinden öneri. Markalı ve kategori promptları ayrı etiketlenir. | Boş sayfa yok — şablon zorunlu |
| **4** | 🚀 **Site erişim denetimi** | Bot izinleri, SSR, temel erişilebilirlik. Motor API'si gerektirmez — saniyeler içinde sonuç. | **İlk oturumda somut kazanım.** Kazara GPTBot engeli yaygın, hızlı düzeltme. |
| **5** | İlk ölçüm | Çekirdek motorlarda (ChatGPT, Gemini, Perplexity) örneklemli ilk ölçüm kuyruğa girer. İlerleme görünür. | Eşzamansız; K1 panel modeli |
| **6** | 🎯 **İlk skor + güven anı** | Skor, fidelite etiketi ve güven aralığıyla birlikte sunulur. "Bu skor nasıl hesaplandı?" bağlantısı detaya iner. | **Ürünün vaadinin kanıtlandığı an** |
| **7** | İlk öneri | Kanıt dereceli ilk öneri listesi. "Uyguladım/Reddettim" işaretleme. | Aksiyon döngüsü başlangıcı |

**İlk değer süresi hedefleri:**
- Adım 4 (site denetimi): **< 30 saniye** — tek oturumda
- Adım 6 (ilk skor): **< 24 saat** — aynı gün içinde

---

## 4. P3 · Ajans Yolculuğu — **Birincil Ticari Odak**

### 4.1 Yolculuk Haritası

| Aşama | Akış | Sürtünme Riski | İzleme |
|-------|------|----------------|--------|
| 🔍 **Keşif** | TR ekosistem içerikleri, metodoloji yayınları (GAVF söylemi), meslektaş tavsiyesi. **Niyet:** "Müşterime AI görünürlük raporu satmak istiyorum." | Kategori araçlarına güvensizlik (0102 §5) | Kanal atıf verisi |
| ⚙️ **Kurulum** | Ajans çalışma alanı oluşturma. Ortak omurga ilk iki müşteri için tekrarlanır. Müşteri başına marka/prompt seti. Ekip koltukları. | Müşteri başına kurulum yükü | Kurulum tamamlama oranı |
| 💎 **İlk Değer** | İlk müşteri raporu: **white-label PDF** + paylaşılabilir özet. Müşteriye sunulabilir metodoloji sayfası (fidelite dili satış aracına dönüşür). | Rapor kişiselleştirme ihtiyacı | İlk rapor üretim süresi |
| 🔄 **Haftalık Ritim** | Zamanlanmış müşteri raporları (M10). Slack uyarıları. Pano üzerinden çok müşteri panoraması. | Uyarı yorgunluğu (M11) | M1 (WAT%), M3, M10 |
| 📈 **Genişleme** | Müşteri ekleme. Koltuk artışı. BI/API ile ajans iç raporlamasına bağlama. GAVF uyumlu rapor standardı. | Müşteri başına maliyet endişesi (S5 panel modeliyle karşılanır) | Kiracı içi büyüme |
| 🗣️ **Savunuculuk** | Vaka çalışması ve referans. Ajans ağında yayılım. "Ben GeoLens kullanıyorum" — sektörde prestij. | Rakip white-label teklifleri (0102 §3.3) | Referans dönüşümü |

### 4.2 P3'e Özel Detaylar

**Ajans çalışma alanının kritik özellikleri:**

- Müşteri başına **izole marka/prompt seti/rapor** — veri sızıntısı yok
- **White-label rapor:** Ajansın kendi logosu, kendi renkleri, kendi alan adı
- **Toplu işlem:** Toplu müşteri ekleme, toplu prompt seti güncelleme
- **Kota yönetimi:** Müşteri başına prompt kotası, toplam kullanım görünürlüğü
- **Faturalandırma çıktısı:** Müşteri başına maliyet raporu (S5)

**P3'ün GeoLens specification avantajı:**

Ajans, müşterisine yalnızca bir rapor değil, **GAVF uyumlu bir AI görünürlük değerlendirmesi** satar. Bu, ajansın hizmetini farklılaştırır ve metodolojiyi savunmak zorunda kalmaz — standart konuşur.

---

## 5. P2 · KOBİ Yolculuğu — **Birincil Ticari Odak**

### 5.1 Yolculuk Haritası

| Aşama | Akış | Sürtünme Riski | İzleme |
|-------|------|----------------|--------|
| 🔍 **Keşif** | Ajans tavsiyesi (P3 kanalı çift işlevli — ajans hem müşteri hem dağıtım kanalı). Arama ve içerik. **Endişe:** "AI'da görünmüyoruz." | Kategori farkındalığı düşük — %92 plan, %40.6 uygulama (0101 §6) | Kanal atıf verisi |
| ⚙️ **Kurulum** | Türkçe sihirbaz. Sektör şablonundan otomatik prompt önerisi. Sözlük destekli arayüz — AI terimleri açıklamalı. | Terminoloji ve dil bariyeri. Zaman kısıtı. | Kurulum tamamlama, adım terk oranı |
| 💎 **İlk Değer** | 🚀 **Site erişim denetimi** hızlı kazanımı. İlk skor **benchmark bağlamıyla** sunulur: "Sektöründe tipik aralık X-Y." Düşük skor terke değil aksiyona yönlendirir. | İlk skor moral bozabilir — çerçeveleme kritik | Aktivasyon oranı |
| 🔄 **Haftalık Ritim** | **Ana yüzey haftalık e-posta özeti.** Özetten panoya "detaya in" bağlantıları. | E-posta okunmazlığı | M1 (WAT%), M3; e-posta→pano geçişi |
| 📈 **Genişleme** | Öneri-uygula-yeniden ölç döngüsü alışkanlığı (M4). Rakip ekleme. Pro'dan Business'a geçiş. | Aksiyonların etkisi görünmezse döngü kopar | M4; yeniden ölçüm oranı |
| 🗣️ **Savunuculuk** | Sektör içi tavsiye. Vaka verisiyle içerik katkısı. "AI görünürlüğünü GeoLens'le takip ediyorum." | — | Tavsiye kaynağı |

### 5.2 P2'ye Özel Detaylar

**KOBİ kullanıcısının ihtiyaç duyduğu basitlik:**

- **Tek sayfa pano:** En önemli 3 şey (genel skor, en iyi kaynak, en acil öneri)
- **Haftalık e-posta:** "Geçen haftaya göre durumun" — tek içgörü, tek aksiyon
- **Benchmark bağlamı:** "Sektöründeki diğer markalara göre" kıyası (anonim toplulaştırılmış)
- **Türkçe, her yerde:** Arayüz, rapor, e-posta, öneriler

**P2'nin ajans bağlantısı:**

P2'nin en güçlü keşif kanalı P3'tür — ajansı olan KOBİ, ajansı aracılığıyla GeoLens'e gelir. Doğrudan self-serve kanalı da açıktır (P4 benzeri) ama ajans kanalı daha hızlı adoptasyon sağlar.

---

## 6. Kanal ve Ritim Mimarisi

| Yüzey | Rol | Birincil Persona |
|-------|-----|:----------------:|
| 🌐 **Web pano** (responsive) | Derin analiz, kaynak detayı, yapılandırma. "Detaya inme"nin gerçekleştiği yer. | P3 (yoğun), P2 (haftalık) |
| 📧 **E-posta özetleri** | Haftalık ritmin taşıyıcısı. Panoya derin bağlantılar. Yönetici özeti formatı. | P2 (ana yüzey), P1 |
| 💬 **Slack/Webhook uyarıları** | Anlamlı değişim bildirimi. Eşikler oynaklık modeline bağlı. | P3 |
| 📄 **Zamanlanmış PDF/BI** | Müşteriye giden white-label rapor. BI beslemesi. | P3 |
| 📱 **Mobil bildirim** | Hafif sinyal katmanı. Responsive web'e köprü. | P2, P4, P5 (ileri) |

**Uyarı tasarım ilkeleri:**
- Uyarı yalnızca istatistiksel olarak anlamlı değişimde tetiklenir (güven aralığı dışı)
- Kullanıcı eşik ve kanal ayarı yapabilir
- Her uyarı "yanlış alarm" geri bildirimi taşır
- **Uyarı yorgunluğu**, haftalık ritmi öldüren birincil düşmandır

---

## 7. Sürtünme ve Terk Riskleri

| Aşama | Risk | Kişi | Belirti | Karşı Tasarım |
|-------|------|:----:|---------|---------------|
| **Kurulum** | Prompt seti boş kalır, kullanıcı ne soracağını bilemez | **P2** | Adım 3 terk oranı | Sektör şablonları varsayılan. Boş başlangıç yok. |
| **İlk Değer** | İlk skor düşük gelir → kullanıcı ürünü bırakır | **P2+P3** | İlk oturum sonrası dönüşsüzlük | Benchmark bağlamı + ilk öneriyle birlikte sunum. Site denetimi hızlı kazanımı önce. |
| **İlk Değer** | Oynaklık kafa karıştırır ("dün 62, bugün 55") | **P2+P3** | Destek soruları; güven kaybı | Güven aralığı görselleştirmesi. Fidelite eğitim katmanı. |
| **Haftalık Ritim** | E-posta özetleri okunmadan silinir | **P2** | M3 düşüşü | Özet: tek içgörü + tek aksiyon formatı. Kişiselleştirilmiş konu satırı. |
| **Haftalık Ritim** | Uyarı yorgunluğu | **P3** | M11 kötüleşir; kanal kapatma | Anlamlılık eşiği. Günlük birleştirme. Kanal ayarları. |
| **Genişleme** | Öneriler uygulanır ama etki görünmez; döngü kopar | **P2+P3** | M4 işaretleme sonrası yeniden ölçüm yok | Öneri-etki takibi: uygulanan önerinin sonraki ölçümde karşılaştırması. |
| **Genişleme** | Ajansın müşterisi ayrılır; çalışma alanı kirlenir | **P3** | Pasif müşteri alanları | Arşivleme ve devir akışı. Kota iadesi. |

---

## 8. Yolculuk-Metrik Eşlemesi

| Aşama | Mevcut Metrik | Yeni Metrik Adayı |
|-------|---------------|-------------------|
| **Kurulum** | — | Kurulum tamamlama oranı; adım bazlı terk |
| **İlk Değer** | — | İlk değere ulaşma süresi (iki kademeli); aktivasyon oranı |
| **Haftalık Ritim** | M1 (WAT%), M3, M10, M11 | E-postadan panoya geçiş oranı |
| **Genişleme** | M4 (öneri etkileşimi) | Öneri sonrası yeniden ölçüm oranı; paket geçiş oranı |
| **Savunuculuk** | — | Tavsiye kaynaklı kayıt payı |

---

## 9. GeoLens İçin Çıkarımlar

1. **0203 (Use Cases)** envanteri bu dokümanın adımlarından türetilir: her tablo satırı bir kullanım senaryosu adayıdır.
2. **0204 (PRD)** gereksinim adayları: kurulum sihirbazı, prompt şablon kütüphanesi, site erişim denetimi modülü, aşamalı ölçüm ilerleme görünümü, açıklama katmanı (calculation_run detayı), e-posta özet motoru, uyarı eşik/kanal ayarları, white-label rapor şablonu, ajans çalışma alanı.
3. **0205 (MVP)** ortak omurgayı çekirdek alır. Site denetimi (adım 4) düşük maliyet/yüksek değer MVP adayıdır.
4. **Specification bağlantısı:** GAVF standardı, P3 için raporu satılabilir bir hizmete dönüştürür. "GAVF uyumlu rapor" ajansın fiyatlandırabildiği bir çıktıdır. P2 için de metodoloji güveni sağlar — "GAVF standartlarıyla ölçülmüştür" ibaresi, KOBİ'nin raporu yönetimine veya müşterisine sunmasını kolaylaştırır.
5. **Filtre bağlantısı:** P3 ve P2 yolculuklarının tekrarlanabilirliği **F2** (ölçek), uzun vadeli ritim tasarımı ve e-posta odaklı iletişim **F1** (5 yıl) filtresini karşılar.
5. **Benchmark bağlamı** ("sektöründe tipik aralık") yeni bir veri ihtiyacı doğurur: anonim toplulaştırılmış kıyas. Gizlilik sınırları ayrıca tanımlanmalıdır.

---

## 10. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Benchmark kıyas verisinin gizlilik sınırları | ⏳ AVIP D-60: ≥5 kiracı eşiği devralındı. Anonim toplulaştırma kuralları pilotta kalibre edilecek. |
| ~~O-2~~ | ~~Uyarı eşik varsayılanları~~ | ✅ **KAPANDI** (AVIP D-12): Pilot verisiyle kalibre edilecek. MVP'de manuel eşik ayarı yeterli. |
| O-3 | Free kademede prompt kotası | ⏳ Pilot deneyiyle belirlenecek. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-07** | **Self-serve kayıt:** Sürtünmesiz kayıt. PO 21.07.2026. | AVIP 0202 O-2 |
| **D-11** | **İlk değer eşikleri:** Adım 4 <30sn, adım 6 <24sa. PO 21.07.2026. | AVIP 0202 O-1 |
| **D-12** | **Uyarı eşikleri:** Pilot verisiyle kalibre. PO 21.07.2026. | AVIP 0202 O-4 |
| **D-60** | **Benchmark gizlilik:** ≥5 kiracı eşiği. TL+PY 21.07.2026. | AVIP 0204 O-1 |
| **D-11** | **İlk değer süre hedefleri** (tekrar): GeoLens ile uyumlu. | AVIP 0202 O-1 |

---

## Kaynaklar

- 0201 User Personas — aktör seti, kanal hipotezleri, paket yapısı
- 0102 Rekabet Analizi — rakip boşlukları, fidelite kuralı
- 0101 Pazar Analizi — uygulama açığı, site denetim fırsatı
- 0004 Success Metrics — M1 North Star, M3, M4, M10, M11

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform kullanıcı yolculukları. P3 (ajans) ve P2 (KOBİ) odaklı. Yedi adımlı ortak omurga, altı aşamalı yolculuk çerçevesi, kanal mimarisi, sürtünme haritası. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-07 (self-serve), D-11 (ilk değer eşikleri), D-12 (uyarı eşikleri — O-2 kapandı), D-60 (benchmark gizlilik). Devralınan Kararlar eklendi. |
