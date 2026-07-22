# 0201 · Kullanıcı Personaları

| Alan | Değer |
|---|---|
| Doküman ID | 0201 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101, 0102, 0103, 0202, 0203, 0204, 0205 |

---

## 1. Amaç

Bu doküman GeoLens Platform'un kimin için inşa edildiğini tanımlar. Beş proto-persona, Faz 1 kanıt tabanından (pazar analizi, rekabet, SWOT) türetilmiştir. Her kartın hangi araştırma bulgusuna dayandığı belirtilmiştir.

**Proto-persona** — saha görüşmesiyle değil, araştırma verisiyle oluşturulmuş kurgusal kişilikler. Doğrulama planı (§10) ile test edilecek, doğrulanan ve yanlışlanan varsayımlar v1.1'e işlenecektir.

---

## 2. Segment Haritası

GeoLens beş farklı segmentte kullanıcıya hitap eder. Hepsi **tek platform**, tek kod tabanı üzerinde yaşar:

| Taraf | Segment | Pazar Durumu | Persona | MVP Öncelik |
|-------|---------|---------------|---------|-------------|
| **B2B** | Ajanslar | Kategoride çok-hesap yönetimi zayıf; TR'de hizmet söylemi kurulmuş. **En güçlü kama.** | **P3 — Mert** | 🟡 **Birincil** |
| **B2B** | KOBİ / Orta Segment | En geniş bakir alan. %92 planlıyor, %40.6 yapıyor. | **P2 — Elif** | 🟡 **Birincil** |
| **B2B** | Kurumsal | Satın alma süreci uzun, SOC 2 gerekli. Tasarım hedefi olarak korunur. | **P1 — Deniz** | 🔵 İkincil |
| **B2C** | Bağımsız Danışman | Self-serve kanıtlı model. Dönüşüm hunisi için kritik. | **P4 — Selin** | 🟢 Self-serve |
| **B2C** | İçerik Üreticisi | AI keşif kanalı büyüyor. Free kademesinin büyüme motoru. | **P5 — Kaan** | 🟢 Free |

**Platform ilkesi:** Beş persona ayrı ürün değil, aynı platformun farklı paket haklarıdır (entitlement). Fidelite etiketi, açıklanabilirlik ve güven öğeleri her pakette istisnasız bulunur — bunlar paket farkı değil, ürün kimliğidir.

---

## 3. Persona Kartları

### P1 · Deniz — Kurumsal Pazarlama Direktörü

| Alan | İçerik |
|------|--------|
| **Bağlam** | TR merkezli büyük marka. SEO, içerik ve PR ekiplerini yönetiyor. Yönetim kuruluna raporluyor. |
| **Hedef** | "AI bizi öneriyor mu?" sorusuna kanıtlı yanıt. Rakip kıyası. Yanlış bilgi riskini erken görmek. |
| **Ağrılar** | Anekdottan öte veri yok (0101 §1). Mevcut araçlara güven düşük (0102 §4-5). Satın almada SOC 2 ve veri işleme şartları. |
| **Karar kriterleri** | Metodoloji şeffaflığı, denetim izi, SSO/SAML, tarihçe derinliği. |
| **Kanal** | Haftalık e-posta yönetici özeti + ekip panosu. Kritik değişimde anlık uyarı. |
| **GeoLens eşleşmesi** | Denetim izi (S1), fidelite etiketi (S2), güven aralıklı skor (S3), rakip kıyası. |
| **Paket** | Enterprise |
| **MVP durumu** | 🔵 Tasarım hedefi. Aktif satış SOC 2 yolu ve tarihçe birikimi olgunlaşana kadar ertelendi. |

### P2 · Elif — KOBİ Pazarlama Yöneticisi

| Alan | İçerik |
|------|--------|
| **Bağlam** | E-ticaret ağırlıklı KOBİ. Küçük ekiple, kısıtlı bütçeyle çalışıyor. SEO'yu kısmen ajansa yaptırıyor. |
| **Hedef** | AI yanıtlarında görünür olmak. Nereden başlayacağını bilmek. İçerik bütçesinin karşılığını görmek. |
| **Ağrılar** | Kategori bilgisi sınırlı — %92 plan / %40.6 uygulama makası tam bu segmentte (0101 §6). Araç karmaşıklığı ve yabancı dil bariyeri. Zaman yok. |
| **Karar kriterleri** | Türkçe arayüz, hızlı kurulum, net aksiyon önerisi, erişilebilir kademe. |
| **Kanal** | Haftalık e-posta özeti. Panoya seyrek giriş. |
| **GeoLens eşleşmesi** | TR-öncelikli prompt kütüphanesi, kanıt dereceli öneriler, site erişim denetimi. |
| **Paket** | Pro (büyürse Business) |
| **MVP durumu** | 🟡 Birincil hedef. |

### P3 · Mert — Ajans SEO/GEO Direktörü — **Birincil Persona**

| Alan | İçerik |
|------|--------|
| **Bağlam** | Dijital pazarlama ajansı. Çok müşterili portföy. Müşterilerine yeni AIO/GEO hizmeti satmak istiyor. |
| **Hedef** | Müşteri başına görünürlük raporu üretmek. Hizmeti ölçeklenebilir ve markalı sunmak. Yeni gelir hattı açmak. |
| **Ağrılar** | Elle derlenen ekran görüntüsü raporları. Kategori liderinde dahi çok-hesap yönetimi zayıf (0102 §3.1). Rapor üretimi zaman yiyor. |
| **Karar kriterleri** | Çok müşterili çalışma alanı, **white-label rapor**, koltuk politikası, API/BI entegrasyonu, müşteri başına maliyet öngörülebilirliği. |
| **Kanal** | Pano yoğun kullanım + Slack uyarıları. Müşteriye giden zamanlanmış PDF/BI raporu. |
| **GeoLens eşleşmesi** | Çok kiracılı ajans modeli (S6), white-label, panel-tabanlı maliyet modeli (S5). |
| **Paket** | Business (ajans çalışma alanı) |
| **MVP durumu** | 🟡 **Birincil hedef.** En kısa satış döngüsü, en yüksek B2B2B çarpanı. |

### P4 · Selin — Bağımsız SEO/GEO Danışmanı

| Alan | İçerik |
|------|--------|
| **Bağlam** | Tek kişilik danışmanlık. Birkaç müşteri, dar araç bütçesi. Uzmanlığını yeni kategoriye taşımak istiyor. |
| **Hedef** | Müşterilerine AI görünürlüğü teşhisi sunmak. Kendini kategoride erken uzman konumlandırmak. |
| **Ağrılar** | Kurumsal araçlar pahalı ve satış-temaslı. Manuel prompt denemesi tekrarlanamaz. Metodolojisini müşteriye savunacak kanıt dili yok. |
| **Karar kriterleri** | Self-serve kayıt, düşük giriş kademesi, açıklanabilir skor, dışa aktarılabilir rapor. |
| **Kanal** | E-posta + mobil bildirim. Hafif, hızlı pano oturumları. |
| **GeoLens eşleşmesi** | Açıklanabilir skor (S1) danışmanın satış aracına dönüşür. Fidelite etiketi (S2) güven verir. |
| **Paket** | Free → Pro dönüşümü |
| **MVP durumu** | 🟢 Self-serve huni. Pilot döneminde davetli + self-serve birlikte. |

### P5 · Kaan — İçerik Üreticisi / Kişisel Marka

| Alan | İçerik |
|------|--------|
| **Bağlam** | Uzmanlık içeriği üreten yaratıcı (bülten, video, blog). Geliri görünürlüğüne bağlı. AI araçlarını yoğun kullanıyor. |
| **Hedef** | AI motorları onu kaynak olarak gösteriyor mu? Rakipleri mi öne çıkıyor? İçerik konularını buna göre seçmek. |
| **Ağrılar** | Keşif AI yanıtlarına kayıyor ama üretici tarafında ölçüm aracı kurumsal odaklı. Bütçe düşük. |
| **Karar kriterleri** | Ücretsiz başlangıç, tek isim/marka izleme, basit skor, paylaşılabilir sonuç. |
| **Kanal** | Mobil bildirim + e-posta. Hafif pano kullanımı. |
| **GeoLens eşleşmesi** | Kişisel marka izleme, konu bazlı görünürlük sinyali. Free kademesi büyüme ve topluluk motoru. |
| **Paket** | Free |
| **MVP durumu** | 🟢 Free kademesi. Topluluk ve ağızdan ağıza büyüme için kritik. |

---

## 4. Persona-İhtiyaç Matrisi

| Yetenek | P1 (Kurumsal) | P2 (KOBİ) | P3 (Ajans) | P4 (Danışman) | P5 (Üretici) |
|---------|:---:|:---:|:---:|:---:|:---:|
| Çok motorlu izleme | 🔴 Kritik | 🔴 Kritik | 🔴 Kritik | 🔴 Kritik | 🟢 Temel |
| Alıntı/kaynak analizi | 🔴 Kritik | 🟡 Orta | 🔴 Kritik | 🔴 Kritik | ⚪ Düşük |
| Kanıt dereceli öneriler | 🟡 Orta | 🔴 Kritik | 🔴 Kritik | 🔴 Kritik | 🟡 Orta |
| Rakip kıyası | 🔴 Kritik | 🟡 Orta | 🔴 Kritik | 🟡 Orta | 🟡 Orta |
| Trend ve uyarılar | 🔴 Kritik | 🟡 Orta | 🔴 Kritik | 🟡 Orta | 🟡 Orta |
| White-label / dışa aktarım | 🟡 Orta | ⚪ Düşük | 🔴 **Kritik** | 🔴 Kritik | ⚪ Düşük |
| SSO, denetim izi | 🔴 **Kritik** | ⚪ Düşük | 🟡 Orta | ⚪ Düşük | ⚪ Düşük |
| API / BI entegrasyonu | 🔴 Kritik | ⚪ Düşük | 🔴 Kritik | ⚪ Düşük | ⚪ Düşük |
| TR dil/prompt setleri | 🟡 Orta | 🔴 **Kritik** | 🔴 **Kritik** | 🔴 Kritik | 🔴 Kritik |

> Matris, 0204 (PRD) gereksinim önceliklendirmesinin ve 0205 (MVP) kesitinin girdisidir. "Kritik" hücreler MVP adayı yetenekleri işaret eder; nihai karar 0205'te verilir.

---

## 5. Paket Yapısı

| Paket | Birincil Persona | Ayırt Edici Haklar | Ölçüm Frekansı |
|-------|------------------|-------------------|:-------------:|
| **Free** | P5, P4 (deneme) | Tek marka, dar prompt kotası, temel skor, fidelite etiketi dahil | Haftalık |
| **Pro** | P4, P2 | Çekirdek motor seti, haftalık izleme, öneriler, PDF dışa aktarım | Haftalık |
| **Business** | P3, P2 (büyüyen) | Çok müşterili çalışma alanı, white-label rapor, API/BI, ekip koltukları | Günlük |
| **Enterprise** | P1 | SSO/SAML, denetim izi, genişletilmiş tarihçe, sözleşmesel destek | Günlük |

**İki kural:**
1. Paket hakları **yapılandırmadır**, kod dalı değil (tek platform ilkesi)
2. Güven öğeleri (fidelite, açıklanabilirlik, izolasyon) hiçbir pakette kısıtlanmaz

---

## 6. Segment Önceliği

> **Karar (22.07.2026):** V1 ticari odağı **P3 (ajans) + P2 (KOBİ)** ikilisidir.

**Gerekçe:**
- **P3 (ajans):** En kısa satış döngüsü, B2B2B çarpanı — 1 ajans müşterisi onlarca markaya ulaşır (0101 §8). Kategorideki en büyük boşluk: ajans/çok-hesap yönetimi lider dahil zayıf (0102 §3.1, §7). White-label rapor ajansın doğrudan faturalandırabildiği çıktıdır.
- **P2 (KOBİ):** En geniş bakir alan — %92 planlıyor, %40.6 uyguluyor (0101 §6). Düşük satın alma bariyeri, self-serve uygunluk.

**Ertelenen:**
- **P1 (kurumsal):** Tasarım hedefi olarak korunur (mimari kurumsal-hazır kurulur). Aktif satış, SOC 2 ve tarihçe birikimi olgunlaşana kadar ertelenir.

---

## 7. Kapsam Dışı Profiller

| Profil | Neden Kapsam Dışı |
|--------|-------------------|
| **Garanti arayan alıcı** | "Bizi ChatGPT'de 1 numara yapın" beklentisi. Olasılıksal ölçüm satılır, garanti satılmaz. |
| **Manipülasyon talep eden** | Motor politikalarına aykırı taktik isteyenler. Öneri motoru bu taktikleri üretmez. |
| **Tek seferlik denetim isteyen** | GeoLens sürekli izleme platformudur. Tek seferlik teşhis ihtiyacı ajans kanalına (P3) yönlendirilir. |
| **Kazıma verisi talep eden** | Kademe 3 yüzeylerin arayüz kazıması. Fidelite ilkesi pazarlıksızdır. |

---

## 8. Doğrulama Planı

| Hipotez | Doğrulama Yöntemi |
|---------|-------------------|
| Kart varsayımları (hedef, ağrı, kriter) | Segment başına en az 5 yarı yapılandırılmış görüşme. P3 ve P2 öncelikli. |
| Bildirim kanalı tercihleri | Görüşme + erken pilotta kanal etkileşim ölçümü. |
| Ajans önceliği ve white-label ihtiyacı | TR ajans görüşmeleri (5 soruluk kılavuz: iş akışı, faturalandırma, araç eksikleri, KOBİ farkındalığı, karar verici haritası). |
| TR pencere varsayımı (12-18 ay) | Alıcı olgunluğu soruları. |
| Free-Pro dönüşümü (P4/P5) | Bekleme listesi + açılış deneyi. |

**Tamamlanma kriteri:** P2 ve P3 kartları görüşme verisiyle güncellenmeden 0204 (PRD) Approved durumuna geçmez.

### Görüşme Aday Listesi (P3 — Ajans)

| Öncelik | Ajans | Odak |
|:-------:|-------|------|
| 🔴 1 | **Sheltron** | Predictive SEO, AI görünürlük denetim süreci |
| 🔴 2 | **Cremicro** | Çok dilli GEO, cross-border müşteri yönetimi |
| 🔴 3 | **Seobaz** | "Ölçülebilir GEO", müşteri başına maliyet modeli |
| 🔴 4 | **Webtures** | Agentic Web Optimization, white-label ihtiyacı |
| 🟡 5 | **Zeo Agency** | Veri odaklı GEO, BI/API entegrasyon ihtiyacı |
| 🟡 6 | **Mobitek** | Büyük katalog yönetimi, e-ticaret müşteri farkındalığı |
| 🟡 7 | **Aora Digital** | Ankara perspektifi, orta ölçekli müşteri profili |
| 🟡 8 | **Digipeak** | TR+EN paralel operasyon, çok kanallı yaklaşım |

---

## 9. GeoLens İçin Çıkarımlar

1. **0202 (User Journey)** birincil yolculukları P3 ve P2 için çizer. P4 self-serve yolculuğu ayrı akıştır.
2. **0204 (PRD)** gereksinim önceliklendirmesi §4 matrisini temel alır.
3. **0205 (MVP)** paket iskeletini §5'ten alır; masa bahisleri (0102 §7) ile persona kritikleri kesiştirilir.
4. **Specification bağlantısı:** GAVF standardı, özellikle danışman (P4) ve ajans (P3) için müşteriye anlatılabilir bir metodoloji dili sağlar. "GAVF uyumlu rapor" bir satış aracıdır.
5. **Kurumsal hazırlık:** P1 gereksinimleri (SSO, denetim izi) ilk günden mimariye konur; satışı beklenmez.

---

## 10. Açık Sorular

| ID | Soru | Not |
|----|------|-----|
| O-1 | Bildirim kanalı önceliği (e-posta, Slack, push) | Ajans görüşmelerinde test edilecek. |
| O-2 | Free kademe prompt kotası ne olmalı? | Pilot deneyiyle belirlenecek. |

---

## Kaynaklar

- 0101 Pazar Analizi — uygulama açığı, benimseme verileri
- 0102 Rekabet Analizi — segment boşlukları, rakip profilleri
- 0103 SWOT — stratejik sonuçlar, kama stratejisi

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform personaları. Beş proto-persona (P1-P5), ihtiyaç matrisi, paket yapısı, segment önceliği kararı (P3+P2 odağı), doğrulama planı, ajans görüşme aday listesi. |
