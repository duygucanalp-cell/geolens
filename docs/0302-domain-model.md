# 0302 · Domain Model

| Alan | Değer |
|---|---|
| Doküman ID | 0302 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş doküman; 0301 ile 0303 (madde 19) arasındaki köprü |
| İlişkili | 0301, 0204, 0005 (girdi); 0303, 0305, 0306, 0309, 0310 (çıktı) |

---

## 1. Amaç ve Kapsam

Bu doküman sistemin alan modelini sabitler: bağlamlar, varlıklar, ilişkiler, değişmezler ve yaşam döngüleri. 0301 konteyner görünümünün iç dilini kurar; 0303 (veritabanı şeması), 0305 (modül sınırları), 0306 (API kaynak adları), 0309 (hesap sözleşmesi) ve 0310 (yetki alanları) buradan türetilir. Kapsam dışı: DDL ve migration'lar, API şekilleri, arayüz modelleri ve performans optimizasyonları. Model V1 ufkunu kapsar; hızlı takip varlıkları (benchmark, etki takibi) yer tutucu olarak işaretlenir.

## 2. Yöntem ve Adlandırma

Adlandırma iki katmanlıdır: iş dili Türkçedir ve 0005 sözlüğüyle hizalanır; kod ve şema adları İngilizcedir ve parantez içinde verilir (örnek: kiracı / tenant). Bağlamlar (bounded context) 0305 modül adaylarıyla birebir eşlenecek biçimde çizilmiştir. Her bağlam için toplam kökleri (aggregate root) işaretlenir: dış dünya bir bağlama yalnız kök üzerinden yazar. Alan kuralları iki biçimde ifade edilir: değişmezler (invariant; her zaman doğru kalması zorunlu kurallar, §6) ve durum makineleri (yaşam döngüsü geçişleri, §7). Kimlik stratejisi önerisi: tüm varlıklarda küresel benzersiz, sıralanabilir kimlik (ULID); dış yüzeylerde opak kimlik (0306). Sistem düzeyi varlıklar (şablon kütüphanesi gibi) dışında her kayıt kiracı bağlamı taşır.

## 3. Bağlam Haritası

| Bağlam (0305 modül adayı) | Sorumluluk | Tükettiği / beslediği |
|---|---|---|
| BC1 · Kimlik ve Kiracılık (identity) | Kiracı, çalışma alanı, kullanıcı, üyelik, rol, paket hakları, davet | Tüm bağlamlara kiracı bağlamı ve hak kararları verir |
| BC2 · Yapılandırma (configuration) | Marka, site, pazar, prompt seti, şablon kütüphanesi, motor kapsamı, izleme planı | BC3'e panel tanımı; BC1'den hak sınırları |
| BC3 · Ölçüm ve Hesap (measurement) | Ölçüm işi, ham yanıt, alıntı, calculation_run, skor, trend, site denetimi | BC2'den panel; BC4-BC5'e skor ve olaylar |
| BC4 · İçgörü ve Aksiyon (insight) | Öneri, öneri-etki (HT1), benchmark (HT2) | BC3'ten skor ve bulgular; M4 telemetrisi |
| BC5 · Bildirim ve Raporlama (delivery) | Uyarı kuralı, uyarı, kanal, özet, rapor | BC3-BC4'ten olaylar; dış kanallara çıkış |
| BC6 · Denetim ve Kota (governance) | Denetim izi, kullanım kayıtları, kota sayaçları | Tüm bağlamlardan yazma olayları; K1 kapısı |

## 4. Çekirdek Varlıklar ve İlişkiler (kök varlıklar koyu; sahiplik zinciri kiracıya iner)

### BC1 · Kimlik ve Kiracılık

| Varlık | Önemli alanlar | İlişkiler |
|---|---|---|
| **Kiracı (Tenant)** | ad, tür (standart/ajans), paket, durum | 1-N Çalışma Alanı, Üyelik |
| **Çalışma Alanı (Workspace)** | ad, durum (aktif/arşiv); her kiracıda en az bir varsayılan alan; ajans müşteri başına açar (FR-G1) | N-1 Kiracı; 1-N Marka, Plan |
| Kullanıcı (User) | e-posta, kimlik doğrulama profili | N-N Kiracı (Üyelik ile) |
| Üyelik (Membership) | rol (yönetici/üye), çalışma alanı erişim kapsamı (O-1) | Kullanıcı ↔ Kiracı |
| Paket Hakları (Entitlement) | hak anahtarları: motor seti, frekans tavanı, kota, white-label, koltuk | 1-1 Kiracı (yapılandırma) |
| Davet (Invitation) | e-posta, rol, süre | N-1 Kiracı |

### BC2 · Yapılandırma

| Varlık | Önemli alanlar | İlişkiler |
|---|---|---|
| **Marka (Brand)** | ad, tür (kendi/rakip), eş anlamlılar | N-1 Çalışma Alanı; 1-N Site |
| Site (Site) | alan adı, doğrulama durumu | N-1 Marka; 1-N Denetim Koşusu |
| Pazar (Market) | dil + bölge (TR öncelikli) | Panel bileşeni |
| **Prompt Seti (PromptSet)** | promptlar; etiket: markalı/kategori (0101 ç.4); kaynak şablon referansı | N-1 Çalışma Alanı; Panel bileşeni |
| Şablon Kütüphanesi (PromptTemplate) | sektör etiketi, dil; sistem düzeyi (kiracısız) | 1-N Prompt Seti (türetme) |
| Motor Kapsamı (EngineScope) | seçili bağdaştırıcılar; paket hakkı sınırında (FR-B5) | Panel bileşeni |
| İzleme Planı (MonitoringPlan) | frekans (haftalık/günlük), pencere | N-1 Çalışma Alanı; Zamanlayıcı girdisi |

### BC3 · Ölçüm ve Hesap

| Varlık | Önemli alanlar | İlişkiler |
|---|---|---|
| **Panel Versiyonu (PanelVersion)** | prompt seti içeriği + motor kapsamı + pazar anlık görüntüsü; versiyon no (§5) | 1-N Ölçüm İşi, Skor |
| **Ölçüm İşi (MeasurementJob)** | idempotent anahtar (alan+panel+pencere), durum, deneme sayacı | N-1 Panel Versiyonu; 1-N Ham Yanıt |
| Ham Yanıt (RawResponse) | S3 referansı, içerik karması, kademe etiketi, motor, zaman | N-1 Ölçüm İşi; 1-N Alıntı |
| Alıntı (Citation) | url, kaynak başlığı, konum (FR-D2 zorunlu meta) | N-1 Ham Yanıt |
| **Hesap Koşusu (CalculationRun)** | girdi kümesi karması, faktör anlık görüntüsü, şablon/algoritma versiyonu; değiştirilemez | N-1 Ölçüm İşi; 1-N Skor |
| Skor (Score) | değer, güven aralığı (alt/üst), fidelite etiketi, tazelik damgası, motor kırılımı | N-1 Hesap Koşusu, Panel Versiyonu |
| Trend Noktası (TrendPoint) | skor referansı, pencere | Seri: Marka × Panel Versiyonu |
| **Denetim Koşusu (SiteAuditRun)** | durum, süre | N-1 Site; 1-N Bulgu |
| Bulgu (AuditFinding) | kategori (bot izni/SSR/erişilebilirlik), önem, düzeltme önerisi bağı | N-1 Denetim Koşusu |

### BC4 · İçgörü ve Aksiyon

| Varlık | Önemli alanlar | İlişkiler |
|---|---|---|
| **Öneri (Recommendation)** | kanıt derecesi (deneysel/korelasyonel/denenebilir), gerekçe, durum; NG10 filtresi üretim önkoşulu | N-1 Çalışma Alanı; kaynak: Skor/Bulgu |
| Öneri Etkisi (RecommendationImpact) | işaretli karşılaştırma (HT1 yer tutucu) | 1-1 Öneri |
| Benchmark (Benchmark) | anonim küme istatistiği (HT2 yer tutucu; N13 eşiği) | Toplulaştırma çıktısı |

### BC5 · Bildirim ve Raporlama

| Varlık | Önemli alanlar | İlişkiler |
|---|---|---|
| Uyarı Kuralı (AlertRule) | eşik ayarı, kanal seçimi (FR-F2) | N-1 Çalışma Alanı |
| **Uyarı (Alert)** | tetik bağlamı, digest grubu, geri bildirim (yerinde/yanlış alarm) | N-1 Kural; kaynak: Skor değişimi |
| Kanal (NotificationChannel) | tür (e-posta/Slack/webhook), hedef, doğrulama | N-1 Çalışma Alanı |
| Özet (Digest) | haftalık içerik, derin bağlantılar (M1) | N-1 Çalışma Alanı |
| **Rapor (Report)** | şablon, marka ayarları, durum, S3 referansı, imzalı URL süresi | N-1 Çalışma Alanı; kaynak: Skorlar |

### BC6 · Denetim ve Kota

| Varlık | Önemli alanlar | İlişkiler |
|---|---|---|
| Denetim Kaydı (AuditLogEntry) | aktör, eylem, kaynak, özet, zaman; yalnız ekleme | Tüm yazma yolları |
| Kullanım Kaydı (UsageRecord) | sayaç türü (prompt/motor çağrısı/rapor), dönem | N-1 Kiracı; FR-H2 görünümü |

## 5. Panel ve Versiyonlama Modeli (ölçüm dürüstlüğünün alan temeli)

Panel; prompt seti içeriği, motor kapsamı ve pazarın birlikte dondurulmuş halidir. Bu üçlüden herhangi biri değiştiğinde yeni panel versiyonu oluşur; eski versiyon ve ona bağlı skorlar aynen korunur. Kurallar: (1) Skor daima üretildiği panel versiyonuna bağlanır; hangi soruların, hangi motorlarda, hangi pazarda sorulduğu skordan geriye doğru her zaman okunabilir. (2) Trend karşılaştırması birincil olarak aynı panel versiyonu içinde yapılır; versiyon sınırı zaman serisinde görünür bir işaretle gösterilir ve seriler dikişsizmiş gibi birleştirilmez (oynaklık dürüstlüğü; 0202 güven anı ilkesinin veri karşılığı). (3) Şablon kütüphanesinden türetilen setlerde kaynak şablon ve sürümü izlenir; kütüphane güncellemesi kiracı setini kendiliğinden değiştirmez, kiracıya öneri olarak düşer. (4) Hesap algoritması ve faktör setinin versiyonu panel versiyonundan bağımsız olarak calculation_run içinde saklanır; iki versiyon ekseni (ne soruldu, nasıl hesaplandı) birbirine karışmaz.

## 6. Değişmezler

| ID | Değişmez | Bağ |
|---|---|---|
| I1 | Sistem şablonları dışında her kayıt bir çalışma alanına, her çalışma alanı bir kiracıya bağlıdır; kiracısız veri yoktur. | NFR-N1, 0301 §5 |
| I2 | Skor, geçerli bir calculation_run olmadan var olamaz; calculation_run girdileri yazıldıktan sonra değiştirilemez. | M6-M7, NFR-N7 |
| I3 | Her skor fidelite etiketi ve güven aralığı taşır; bu alanlar boş bırakılamaz. | İ2, FR-C5/C6 |
| I4 | Panel içeriği değişimi yeni panel versiyonu üretir; skorlar üretildikleri versiyona bağlı kalır (§5). | Ölçüm dürüstlüğü |
| I5 | Ham yanıt arşivi silinemez ve üzerine yazılamaz; içerik karması bütünlüğü doğrular. | NFR-N11 |
| I6 | Denetim izi yalnız eklemelidir; hiçbir alan işlemi mevcut kaydı değiştiremez veya silemez. | NFR-N6, M14 |
| I7 | Motor politikalarına aykırı taktik içeren öneri kalıcılaştırılamaz; filtre üretim önkoşuludur. | NG10, FR-E2 |
| I8 | Uyarı yalnız anlamlılık kuralını geçen değişimden üretilebilir. | FR-F1, 0309 |
| I9 | Kota sayacı aşımında motor çağrısı alan düzeyinde engellenir; iş ertelenir. | K1, NFR-N14 |
| I10 | Arşivlenen çalışma alanında yeni ölçüm üretilmez; tarihçe okunur kalır. | FR-G3 hazırlığı |
| I11 | Rapor yalnız yayınlanmış (etiketli) skorlardan üretilir; etiketsiz veri hiçbir çıktı yüzeyine giremez. | İ2, FR-F4 |

## 7. Durum Makineleri

| Varlık | Geçişler | Not |
|---|---|---|
| Ölçüm İşi | kuyrukta → çalışıyor → tamamlandı \| kısmi \| başarısız; başarısız → kuyrukta (sınırlı deneme) | Kısmi sonuç etiketle yayınlanır veya bekletilir (0309 kuralı) |
| Rapor | kuyrukta → üretiliyor → hazır \| başarısız; başarısız → kuyrukta | Hazır: imzalı URL üretilir; denetim kaydı düşer |
| Öneri | açık → uygulandı \| reddedildi; uygulandı → etki izleniyor (HT1) | İşaretler M4 telemetrisine yazılır |
| Uyarı | üretildi → iletildi → geri bildirim: yerinde \| yanlış alarm | M11 beslemesi; digest grubuna katılabilir |
| Çalışma Alanı | aktif → arşiv; arşiv → aktif (geri alma) \| devredildi (HT1) | I10 uygulanır; devir denetim kaydıyla |

## 8. Sözlük Hizası

Model, 0005 sözlüğündeki yerleşik terimleri aynen kullanır (kiracı, fidelite etiketi, güven aralığı, calculation_run, panel, kademe, denetim izi). Aşağıdaki terimler bu dokümanla dile girmiştir ve 0005 v1.1 adayıdır:

| Aday terim | Kısa tanım |
|---|---|
| Çalışma alanı (workspace) | Kiracı altındaki izole çalışma birimi; ajans modelinde müşteri karşılığı. |
| Panel versiyonu | Prompt seti + motor kapsamı + pazarın dondurulmuş hali; skorların karşılaştırma birimi. |
| Bulgu (finding) | Site erişim denetiminin önem dereceli tekil çıktısı. |
| Digest | Aynı gün tetiklerinin birleştirildiği toplu bildirim. |
| İlk değer, aktivasyon | 0202 çıkarım 6'dan devreden yolculuk terimleri (tanım 0004 v1.1 ile). |

## 9. AVIP için Çıkarımlar

1. 0303 türetme kuralları: her tabloda kiracı kimliği (I1); calculation_run ve denetim izi tabloları yalnız ekleme (I2, I6); ham yanıt meta verisi ile S3 nesnesi karma alanıyla bağlanır (I5); panel versiyonu ayrı tablo, skorlar ona yabancı anahtarla bağlanır (I4).
2. 0305 modül sınırları bağlam haritasıyla birebir eşlenir; bağlamlar arası erişim yalnız kök varlık arayüzlerinden yapılır.
3. 0306 kaynak adları varlık adlarından türetilir; dış kimlikler opak, iç kimlikler ULID önerisiyle (O-4 kimlik kararı).
4. 0309 sözleşmesi netleşti: calculation_run alanları (girdi kümesi karması, faktör anlık görüntüsü, algoritma versiyonu) ve iki versiyon ekseninin ayrımı (§5 kural 4) hesap motorunun tasarım girdisidir.
5. 0310 yetki alanları: üyelik-çalışma alanı erişim modeli (O-1) rol tasarımının ilk sorusudur; ajans müşteri izolasyonu kiracı içi yetki sınırı olarak uygulanır.
6. Panel versiyon işareti (trend kırılım göstergesi) 0204'e küçük bir gereksinim eki gerektirebilir; v1.1 turunda FR-D4 notu olarak değerlendirilir.

## 10. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Üyelik erişim kapsamı: kiracı geneli mi, çalışma alanı bazlı mı | 0310 rol modeli; ajans koltuk politikasını etkiler; TL + PO. |
| O-2 | Panel versiyon geçişinde trend gösterim kuralının ayrıntısı | 0309 (istatistik) + arayüz; işaret zorunlu, birleştirme yasak (§5). |
| O-3 | Benchmark varlığının anonimleştirme modeli | NFR-N13; HT2 öncesi; 0204 O-2 ile birlikte. |
| O-4 | Kimlik stratejisinin kesinleştirilmesi (ULID önerisi) | 0303 ile; sıralanabilirlik ve indeks etkisi kıyası; TL. |

---

## Kaynaklar

- 0301 System Architecture · konteyner sorumlulukları, izolasyon modeli, ölçüm hattı (varlık türetme temeli)
- 0204 PRD · FR/NFR bağları (I1-I11 kaynakları), ürün ilkeleri
- 0005 Glossary · yerleşik terimler; v1.1 aday listesi (§8)
- 0203 Use Cases · durum makinelerinin senaryo karşılıkları (UC-06, UC-14, UC-16, UC-19, UC-24)
- 0101 GEO Landscape · markalı/kategori prompt ayrımı, bulgu kataloğu kökeni

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 6 bağlamlı harita, 28 çekirdek varlık, panel versiyonlama modeli (iki versiyon ekseni), 11 değişmez, 5 durum makinesi, sözlük hizası ve 0303/0305/0306/0309/0310 türetme kuralları. |
