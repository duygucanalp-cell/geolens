# 0203 · Use Cases

| Alan | Değer |
|---|---|
| Doküman ID | 0203 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 14 · Use Cases |
| İlişkili | 0201, 0202 (girdi); 0204, 0205 (çıktı); 0307, 0309, 0310 |

---

## 1. Amaç ve Kapsam

Bu doküman, 0202 yolculuk adımlarını doğrulanabilir kullanım senaryolarına ayrıştırır ve 0204 (PRD) gereksinim setinin son girdisini tamamlar. Her senaryo "kim, neyi, hangi akışla başarır" sorusunu yanıtlar; ekran tasarımı, veri modeli ve uç nokta tanımı kapsam dışıdır (0204 ve mimari dokümanlar). Envanter V1 ufkunu kapsar; platform ufku senaryoları (tahmine dayalı öngörü, otomatik iyileştirme) 0003 §5 gereği 0206'da değerlendirilir ve buraya alınmaz.

## 2. Yöntem ve Senaryo Formatı

Senaryolar UC-XX numarasıyla anılır ve üç öncelik sınıfına ayrılır: Çekirdek (MVP adayı; yolculukların ortak omurgası ve birincil personaların kritik ihtiyaçları), Genişletilmiş (V1 sonrası hızlı takip; bağımlılığı veya kararı bekleyenler), Platform (0206). Öncelik işareti adaylıktır, karar değildir; kesin MVP kesiti 0205'te verilir (§7). İki senaryo tipi vardır: kullanıcı tetiklemeli ve sistem tetiklemeli (aktörü zamanlayıcı veya kural motoru olan akışlar). Üç kesişen kural tüm senaryolara uygulanır ve tek tek tekrarlanmaz: her senaryo kiracı bağlamında çalışır ve izolasyonu ihlal edemez (M12); skor gösteren her senaryo fidelite etiketini istisnasız taşır (0102 §4); yazma işlemi içeren her senaryo denetim izine kayıt düşer (M14).

## 3. Aktör Envanteri

| Aktör | Tip | Tanım |
|---|---|---|
| P1-P5 personaları | İnsan | 0201 kartları; senaryolarda birincil aktör olarak anılır. |
| Kiracı yöneticisi / üye | İnsan (rol) | Kiracı içi kaba rol ayrımı; yönetici yapılandırma ve davet yetkisi taşır. Granüler rol modeli 0310'da (O-3). |
| Rapor alıcısı | İnsan (pasif) | Ajans müşterisi veya yönetici; ürüne girmeden white-label rapor ve özet tüketir. |
| Zamanlayıcı | Sistem | Zamanlanmış ölçüm ve rapor işlerini kuyruğa yazan bileşen (0307). |
| Ölçüm ve kural motoru | Sistem | Motor çağrılarını yürüten, skorları hesaplayan ve anlamlı değişim kuralını işleten bileşenler (0308, 0309). |
| Bildirim servisi | Sistem | E-posta, Slack/webhook ve mobil bildirim dağıtımı (D-02 yüzeyleri). |

## 4. Senaryo Envanteri

### A · Hesap ve kurulum

| ID | Senaryo | Birincil aktör | Öncelik | Bağ |
|---|---|---|---|---|
| UC-01 | Kayıt ve kiracı oluşturma | P2, P3, P4, P5 | Çekirdek | 0202 adım 1 |
| UC-02 | Marka, rakip ve pazar tanımı | Tümü | Çekirdek | 0202 adım 2 |
| UC-03 | Prompt seti kurulumu (şablon kütüphanesi) | Tümü | Çekirdek | 0202 adım 3 |
| UC-04 | Site erişim denetimi | P2, P3, P4 | Çekirdek | 0101 çıkarım 6 |

### B · Ölçüm ve skor

| ID | Senaryo | Birincil aktör | Öncelik | Bağ |
|---|---|---|---|---|
| UC-05 | Manuel ölçüm tetikleme | Tümü | Çekirdek | G1 |
| UC-06 | Zamanlanmış ölçüm yürütme | Zamanlayıcı | Çekirdek | G6, M10 |
| UC-07 | Skor inceleme ve açıklama katmanı | Tümü | Çekirdek | G2, M6-M7 |
| UC-08 | Motor bazlı kırılım görüntüleme | Tümü | Çekirdek | H2 |

### C · Analiz

| ID | Senaryo | Birincil aktör | Öncelik | Bağ |
|---|---|---|---|---|
| UC-09 | Alıntı ve kaynak analizi | P1, P3, P4 | Çekirdek | G3, M9 |
| UC-10 | Rakip kıyası | P1, P3 | Çekirdek | G5 |
| UC-11 | Trend ve zaman serisi inceleme | Tümü | Çekirdek | G6 |
| UC-12 | Benchmark bağlamı görüntüleme | P2 | Genişletilmiş | 0202 O-3 |

### D · Aksiyon döngüsü

| ID | Senaryo | Birincil aktör | Öncelik | Bağ |
|---|---|---|---|---|
| UC-13 | Öneri listesi inceleme (kanıt dereceli) | Tümü | Çekirdek | G4, NG10 |
| UC-14 | Öneri işaretleme (uygulandı / reddedildi) | P2, P3, P4 | Çekirdek | M4 |
| UC-15 | Öneri-etki takibi (işaretli karşılaştırma) | P2, P3 | Genişletilmiş | 0202 §9 |

### E · Bildirim ve raporlama

| ID | Senaryo | Birincil aktör | Öncelik | Bağ |
|---|---|---|---|---|
| UC-16 | Anlamlı değişim uyarısı alma ve geri bildirim | Kural motoru → P3, P1 | Çekirdek | G6, M11 |
| UC-17 | Uyarı eşik ve kanal ayarı | P3, P1 | Çekirdek | 0202 §8 |
| UC-18 | Haftalık e-posta özeti üretimi ve dağıtımı | Zamanlayıcı → P2 | Çekirdek | M1, M3 |
| UC-19 | White-label PDF rapor üretimi | P3 | Çekirdek | S6 |
| UC-20 | Zamanlanmış rapor dağıtımı | Zamanlayıcı | Genişletilmiş | M10 |
| UC-21 | API / BI erişimi (okuma) | P1, P3 | Genişletilmiş | 0103 §7 |

### F · Ajans operasyonları

| ID | Senaryo | Birincil aktör | Öncelik | Bağ |
|---|---|---|---|---|
| UC-22 | Müşteri çalışma alanı ekleme | P3 | Çekirdek | S6 |
| UC-23 | Çok müşteri panoraması | P3 | Çekirdek | 0202 §4 |
| UC-24 | Müşteri arşivleme ve devir | P3 | Genişletilmiş | 0202 §9 |

### G · Yönetim ve kurumsal

| ID | Senaryo | Birincil aktör | Öncelik | Bağ |
|---|---|---|---|---|
| UC-25 | Üye davet ve rol atama | Kiracı yöneticisi | Çekirdek | G7 |
| UC-26 | Paket yükseltme (self-serve) | P4, P2 | Genişletilmiş | 0202 O-2 |
| UC-27 | Denetim izi görüntüleme ve dışa aktarım | P1, kiracı yöneticisi | Genişletilmiş | M14 |
| UC-28 | SSO ile oturum açma | P1 | Genişletilmiş | G7, 0201 §6 |

M14 ayrımı: denetim izine kayıt düşme Çekirdek mimari yükümlülüktür (sert kural, tüm yazma senaryolarında); UC-27 yalnızca kaydın kullanıcıya görünümü ve dışa aktarımıdır.

## 5. Çekirdek Senaryo Detayları (temsili altı kart; kalan detaylar 0204'te gereksinime iner)

### UC-04 · Site erişim denetimi

| Alan | İçerik |
|---|---|
| Aktör / tetik | P2, P3, P4; kurulum sırasında otomatik önerilir veya panodan manuel tetiklenir. |
| Ön koşul | Alan adı tanımlı (UC-02). |
| Ana akış | 1. Sistem robots.txt ve bot izinlerini okur (GPTBot, OAI-SearchBot, ClaudeBot, PerplexityBot vb.). 2. SSR ve temel erişilebilirlik sinyallerini kontrol eder. 3. Bulguları önem sırasıyla listeler (örnek: kazara arama botu engeli). 4. Her bulgu düzeltme önerisiyle sunulur. |
| İstisnalar | Alan adına ulaşılamıyor: hata nedeni ve yeniden deneme; sonuç kısmiyse kısmilik etiketi. |
| Başarı / bağ | Saniyeler içinde somut bulgu (aşamalı ilk değer, 0202 §3); motor API'si gerektirmez. |

### UC-06 · Zamanlanmış ölçüm yürütme

| Alan | İçerik |
|---|---|
| Aktör / tetik | Zamanlayıcı; kiracının izleme planına göre (0307). |
| Ön koşul | Aktif prompt seti ve motor kapsamı; kota uygun (K1). |
| Ana akış | 1. İş kuyruğa yazılır. 2. Motor bağdaştırıcıları örnekleme planına göre çağrılır (0309). 3. Yanıtlar arşivlenir, skorlar deterministik hesap katmanında üretilir (calculation_run). 4. Sonuç panoya ve trend serisine işlenir; tazelik damgası güncellenir (K3). |
| İstisnalar | Motor çağrı hatası: sınırlı yeniden deneme, motor bazında M8 kaydı; kota eşiği: iş ertelenir ve kiracıya bilgi düşer; kısmi tamamlanma: skor kısmilik etiketiyle yayınlanır veya yayın ertelenir (kural 0309'da). |
| Başarı / bağ | Planlanan pencerede tamamlanma (M10 ≥ %99 [K]); insan müdahalesi gerekmez. |

### UC-07 · Skor inceleme ve açıklama katmanı

| Alan | İçerik |
|---|---|
| Aktör / tetik | Tüm personalar; pano veya e-posta derin bağlantısından. |
| Ön koşul | En az bir tamamlanmış ölçüm. |
| Ana akış | 1. Skor; fidelite etiketi, güven aralığı ve tazelik damgasıyla birlikte görüntülenir. 2. "Nasıl hesaplandı" katmanı açılır: örneklem büyüklüğü, motor kapsamı, şablon versiyonu. 3. İsteyen kullanıcı calculation_run detayına iner: girdiler ve faktör anlık görüntüsü. 4. Aynı girdilerle yeniden hesap birebir aynı skoru verir. |
| İstisnalar | Bayat skor: K3 uyarısı ve öncelikli yeniden ölçüm önerisi; kısmi motor kapsaması etikette belirtilir. |
| Başarı / bağ | Güven anı (0202 §2); M6-M7 sert kurallarının kullanıcıya görünen yüzü; S1-S3 üçlüsü. |

### UC-14 · Öneri işaretleme ve döngü

| Alan | İçerik |
|---|---|
| Aktör / tetik | P2, P3, P4; öneri listesinden (UC-13). |
| Ön koşul | Üretilmiş öneri seti; her öneri kanıt derecesi taşır (deneysel / korelasyonel / denenebilir; 0101 çıkarım 5). |
| Ana akış | 1. Kullanıcı öneriyi inceler; gerekçe ve kanıt derecesini görür. 2. "Uyguladım" veya "reddettim" işaretler; isteğe bağlı not düşer. 3. İşaret M4 telemetrisine yazılır. 4. Uygulanan öneri, sonraki ölçümde etki takibi için işaretlenir (UC-15 köprüsü). |
| İstisnalar | Motor politikasına aykırı taktik önerisi üretilmez (NG10 filtresi üretim aşamasında çalışır; kullanıcı böyle bir öneri göremez). |
| Başarı / bağ | Geri besleme döngüsü kapanır (M4 ≥ %40 [K]); aksiyon boşluğu (P3 boşluğu, 0002) ürün içinde ölçülür. |

### UC-16 · Anlamlı değişim uyarısı

| Alan | İçerik |
|---|---|
| Aktör / tetik | Kural motoru; yeni ölçüm sonucu eşik kuralını tetiklediğinde. |
| Ön koşul | Trend serisi mevcut; kanal yapılandırılmış (UC-17). |
| Ana akış | 1. Değişim, istatistiksel anlamlılık kuralından geçer (güven aralığı dışına çıkış, 0309). 2. Uyarı; bağlam (önce/sonra, olası kaynak kırılımı) ve panoya derin bağlantıyla kanala düşer. 3. Kullanıcı uyarıyı "yerinde" veya "yanlış alarm" olarak işaretleyebilir (M11 beslemesi). 4. Aynı gün birden çok tetik tek digest'te birleştirilir. |
| İstisnalar | Kanal iletim hatası: yeniden deneme ve pano içi bildirime düşme. |
| Başarı / bağ | Yanlış alarm oranı M11 ≤ %20 [K]; uyarı yorgunluğuna karşı tasarım (0202 §8-9). |

### UC-19 · White-label PDF rapor üretimi

| Alan | İçerik |
|---|---|
| Aktör / tetik | P3; müşteri çalışma alanından manuel veya zamanlanmış (UC-20). |
| Ön koşul | Ajans marka ayarları (logo, renk) ve rapor şablonu seçili. |
| Ana akış | 1. Rapor işi eşzamansız kuyruğa yazılır; PDF üretimi sunucu tarafında çalışır (istemci tarafında render yok). 2. Rapor; skorlar, trend, kaynak analizi ve metodoloji sayfasını (fidelite dili) içerir. 3. Hazır olduğunda indirme bağlantısı ve isteğe bağlı e-posta dağıtımı. 4. Üretim denetim izine işlenir. |
| İstisnalar | Üretim hatası: iş yeniden kuyruklanır, kullanıcı bilgilendirilir; büyük rapor bölünür. |
| Başarı / bağ | Müşteriye sunulabilir rapor dakikalar içinde; ajans değer zinciri (S6) ürünleşir. |

## 6. Persona-Senaryo Kapsaması

| Persona | Birincil senaryo seti |
|---|---|
| P1 Kurumsal | UC-07, 09, 10, 11, 16, 17, 21, 25, 27, 28 (pilot davet modelinde; 0202 §7) |
| P2 KOBİ | UC-01, 02, 03, 04, 05, 07, 11, 12, 13, 14, 18, 26 |
| P3 Ajans | UC-01, 02, 03, 04, 07, 09, 10, 11, 13, 14, 16, 17, 19, 20, 22, 23, 24, 25 |
| P4 Danışman | UC-01, 02, 03, 04, 05, 07, 09, 13, 14, 26 |
| P5 Üretici | UC-01, 02, 03, 05, 07, 11, 18 (hafif kesit; Free kapsamı 0205'te) |

Tutarlılık kontrolü: kapsama, 0201 §5 ihtiyaç matrisiyle birebir hizalıdır; matriste "Kritik" işaretli her persona-yetenek kesişimi en az bir Çekirdek senaryoyla karşılanmıştır. P3 en geniş seti kullanır; birincil ticari odak kararının (0201 §7) senaryo düzeyindeki yansımasıdır.

## 7. Öncelik Kesiti ve 0205 Girdisi

Dağılım: 19 Çekirdek, 9 Genişletilmiş. Çekirdek kesit üç kümeden oluşur: ortak omurga (UC-01 - UC-08), aksiyon ve güven döngüsü (UC-09 - UC-14 çekirdekleri, UC-16 - UC-18), ajans katmanı (UC-19, UC-22, UC-23, UC-25). Genişletilmiş sınıfın gerekçeleri tekildir: UC-12 gizlilik çözümüne (0202 O-3), UC-26 ödeme politikasına (O-2), UC-27 - UC-28 kurumsal satış zamanlamasına (0201 §7), UC-15, UC-20, UC-21, UC-24 ise çekirdek döngü doğrulandıktan sonraki hızlı takibe bağlıdır.

> **0205 karar kuralı:** Çekirdek işareti MVP adaylığıdır, kararı değildir. 0205, bu kesiti masa bahisleri (0103 §7), panel maliyet korumaları (K1) ve motor kapsam kararı (D-03 dahil) ile kesiştirerek nihai MVP sınırını çizer; Çekirdek bir senaryonun MVP dışına alınması gerekçesiyle changelog'a işlenir.

## 8. AVIP için Çıkarımlar

1. 0204 türetme kuralı: her Çekirdek senaryo en az bir gereksinime iner; gereksinimler UC numarasına geri bağlanır (çift yönlü izlenebilirlik).
2. Sistem senaryoları (UC-06, UC-16, UC-18, UC-20) 0307 zamanlayıcı-kuyruk tasarımının işlevsel sözleşmesidir; M8 ve M10 ölçüm noktaları bu akışların içine yerleşir.
3. UC-07 açıklama katmanı, M6-M7 sert kurallarının arayüz sözleşmesini tanımlar: calculation_run detayı kullanıcıya açılmadan "açıklanabilirlik" iddiası eksik kalır.
4. NG10 filtresi öneri üretim hattının içindedir (UC-14 istisnası): uyumsuz taktik kullanıcıya hiç gösterilmez; bu, filtreyi arayüz değil motor gereksinimi yapar (0309).
5. UC-19 rapor üretimi eşzamansız sunucu işi olarak sabitlendi; istemci tarafında PDF üretimi yapılmaz (0304/0307 tasarım girdisi).
6. UC-04 bulgu kataloğu (bot izinleri, SSR, erişilebilirlik) 0308'in motor-dışı denetim bileşenine gereksinim listesi verir.

## 9. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | UC-12 (benchmark) V1 kapsamına girer mi | 0202 O-3 gizlilik çözümüne bağlı; 0205'te kesinleşir. |
| O-2 | UC-26 self-serve ödeme akışının sürüm hedefi | 0201 O-2 ve 0202 O-2 ile birleşik PO kararı. |
| O-3 | Rol modeli granülaritesi: yönetici/üye yeterli mi, izleyici rolü gerekli mi | 0310 kimlik-yetkilendirme tasarımında; UC-25 detayını etkiler. |
| O-4 | UC-21 API kapsamı: okuma-yalnız mı başlar | Öneri okuma-yalnız; 0204 gereksinimi ve 0304 ADR'siyle. |

---

## Kaynaklar

- 0202 User Journey · ortak omurga adımları, kanal mimarisi, sürtünme haritası (senaryo tohumları)
- 0201 User Personas · aktör seti, ihtiyaç matrisi (§6 tutarlılık kontrolü), segment önceliği
- 0004 Success Metrics · M4, M8, M10, M11, M12, M14 bağları; sert kural ayrımı
- 0101 GEO Landscape · site denetimi kapsamı, kanıt derecesi çerçevesi
- 0103 Competitor Analysis · masa bahisleri (0205 kesişim kuralı), ajans katmanı gerekçesi

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 6 aktör tipi, 7 grupta 28 senaryo (19 Çekirdek / 9 Genişletilmiş), altı detay kartı, persona kapsama kontrolü, 0205 karar kuralı ve 0204 türetme kuralı. |
