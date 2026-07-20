# 0307 · Background Jobs & Scheduling

| Alan | Değer |
|---|---|
| Doküman ID | 0307 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 23 · Background Jobs & Scheduling |
| İlişkili | 0301 §6, 0303, 0304 (ADR-005), 0305 (girdi); 0308, 0309, 0311, 0402 (çıktı); M8, M10, M11, K1 |

---

## 1. Amaç ve Kapsam

Bu doküman eşzamansız işleme sistemini sözleşme düzeyinde sabitler: iş sınıfları, zamanlayıcı, outbox dağıtıcısı, tüketici grupları, yeniden deneme ve kapanış politikaları ile M8/M10 ölçüm noktaları. 0301 §6 hattının 1-3 ve 8-9 adımlarının ayrıntısıdır. Kapsam dışı: bağdaştırıcı içi çağrı mantığı (0308), skor ve anlamlılık algoritmaları (0309), kapasite boyutlandırma ve alarm eşik değerleri (0311, 0402).

## 2. İş Sınıfları ve Kuyruk Eşlemesi

| İş sınıfı | Kuyruk | Üretici | Tüketici profili | Not / SLA sınıfı |
|---|---|---|---|---|
| measure.panel | q:measure | Zamanlayıcı (plan taraması) | worker:measure | M10 pencere takibi; K1 kapısı |
| measure.manual | q:measure | API (FR-C1; Idempotency-Key) | worker:measure | Aynı hat; kullanıcı tetikli |
| audit.site | q:audit | API (UC-04) | worker:measure (öncelikli) | Saniyeler hedefi; ölçümden ayrı akış (aşamalı ilk değer) |
| report.render | q:report | API (FR-F4) | worker:report | Chromium render; izole süreç |
| notify.alert | q:notify | Kural motoru olayı (outbox) | worker:notify | Digest grubuna katılım (§7) |
| notify.digest.flush | q:notify | Zamanlayıcı (günlük) | worker:notify | Gün sonu digest kapanışı |
| digest.weekly | q:notify | Zamanlayıcı (haftalık) | worker:notify | UC-18; derin bağlantı üretimi |
| report.scheduled | q:report | Zamanlayıcı | worker:report | HT1 yer tutucu (FR-F5); kanca hazır |

Site denetimi eşzamansız kalır ancak ayrı hafif kuyrukta önceliklidir; arayüz kısa aralıklı durum sorgusuyla saniyeler içinde sonucu gösterir. Outbox dağıtım döngüsü kuyruk işi değil süreç içi görevdir (§4).

## 3. Zamanlayıcı Tasarımı

Tek etkin örnek kuralı Redis kilidiyle sağlanır: kilit SET NX ve süreyle alınır, periyodik yenilenir; yenileme kaçırılırsa süreç kendini pasifleştirir ve üretimden çekilir. Tarama döngüsü izleme planlarını okur, pencere hesabını UTC üzerinden yapar (kiracı görünümü TR'ye çevrilir, 0303 K5) ve her pencere için idempotent iş üretir: measurement_jobs kaydı ile outbox olayı aynı veritabanı işleminde yazılır; idempotency_key çift üretimi engeller (0303 §4). Pencere kaydı açıldığı anda M10 takibi başlar. Kesinti telafisi: yeniden başlayan zamanlayıcı kaçırılmış en yakın pencereleri sınırlı derinlikte telafi eder [K]; daha eskiler atlanır ve M10'a gecikme olarak işlenir; telafi işleri normal işlerle aynı yoldan gider. Kiracılar arası üretim küçük rastgele kaydırmayla (jitter) yayılır; tepe yığılması ve motor kotası baskısı önlenir.

## 4. Outbox Dağıtıcısı

Dağıtıcı, API veya zamanlayıcı süreci içinde tekil görev olarak koşar (Redis kilidiyle tek etkin örnek). Döngü: event_outbox tablosundan pending kayıtları kilitli okur (SKIP LOCKED), toplu halde ilgili Streams kuyruğuna ekler, kayıtları dispatched olarak işaretler. Teslim garantisi en-az-bir-kezdir; bu nedenle tüm tüketiciler idempotent tasarlanır (§5). Aynı varlık için olay sırası garanti edilmez; tüketiciler sırasızlığı tolere eder (durum geçişleri koşullu yazılır). Aktarımı defalarca başarısız olan kayıt dead işaretlenir ve alarma bağlanır (0311); dispatched kayıtları kısa saklama sonrası temizlenir [K] (0303 K6 istisnası).

## 5. Tüketici Grupları ve Devralma

Her kuyruğun tek tüketici grubu vardır (cg:measure, cg:report, cg:notify); işçiler gruba tüketici adıyla katılır ve XREADGROUP ile iş alır. Onay (XACK) yalnız iş sonucu kalıcılaştıktan sonra verilir. Çöken işçinin sahiplenilmiş ama onaylanmamış mesajları, iş türüne göre tanımlı asgari boşta kalma süresi [K] sonrasında XAUTOCLAIM ile başka işçiye devredilir; bu, görünürlük zaman aşımının karşılığıdır. İdempotens veri tarafında zorlanır: measurement_jobs durum geçişleri koşullu güncellemedir (yalnız beklenen önceki durumdan), rapor ve bildirim işleri sonuç anahtarıyla tekrar yazımı yutar. Teslim sayısı eşiğini aşan zehirli mesaj q:dead akışına taşınır ve alarm üretir; inceleme ve yeniden oynatma prosedürü 0311'de, yetkisi 0310'da tanımlanır.

## 6. Yeniden Deneme ve Kota Kapısı

İki deneme katmanı ayrışır. Çağrı katmanı: motor isteği başına kısa, sınırlı yeniden deneme bağdaştırıcı içindedir (0308) ve M8 sayaçlarına yazar. İş katmanı: başarısız iş failed durumuna düşer ve attempt artışıyla yeniden kuyruklanır; bekleme üstel geri çekilme + jitter ile büyür, deneme tavanı [K] aşılınca iş kalıcı başarısız kabul edilip alarma bağlanır. Kısmi tamamlanma (partial) iş katmanında son durumdur; etiketli yayın veya bekletme kararı 0309 kuralına aittir. Kota kapısı çalıştırmanın önündedir: işçi, yürütme öncesi kiracı ve platform sayaçlarını kontrol eder (K1; 0303 §7 ön kontrol + usage_records gerçek kaynak); aşımda iş ertelenir, deneme sayılmaz ve kiracıya bilgilendirme olayı üretilir (FR-H2 yüzeyi).

## 7. Digest ve Haftalık Özet İşleri

Uyarı digest kuralı: gün içindeki anlamlı değişim tetikleri (0309) uyarı kaydı üretir ve açık digest grubuna eklenir; notify.digest.flush kiracının yerel gün sonunda [K] grubu tek bildirimde kapatır. Varsayılan davranış digest'tir; kullanıcı kanal ayarlarından belirli kural sınıfları için anında iletimi seçebilir (FR-F2; varsayılanın kendisi O-3 kararıdır). Haftalık özet (digest.weekly) kiracı bazlı üretilir: hafta anahtarıyla idempotenttir, içerik skor özetleri ve imzalı derin bağlantı token'larından oluşur (0306 §8), gönderim penceresi kiracı ayarıdır [K]. Özet üretimi ile e-posta teslimi ayrı adımlardır; teslim hatası özet içeriğini yeniden üretmeden yeniden dener.

## 8. Kapanış, Dağıtım ve Eşzamanlılık

Zarif kapanış: SIGTERM alan işçi yeni mesaj almayı durdurur, süren işleri kapanış süresi içinde bitirir; onaylanamayanlar devralma mekanizmasıyla (§5) başka işçiye geçer. Sürüm geçişi güvenlidir: eski ve yeni işçiler aynı tüketici grubunda birlikte çalışabilir, idempotens çift işlemeyi zararsız kılar. Eşzamanlılık iki düzeyde sınırlanır: işçi içi paralellik havuzu (profil başına yapılandırma) ve motor bazlı küresel eşzamanlılık sınırı (Redis sayaçlı; 0308 hız politikasıyla koordine). Zamanlayıcı ve dağıtıcı tekilliği kilitle korunur; kilit kaybı durumunda üretim durur, tüketim etkilenmez.

## 9. Gözlemlenebilirlik: M8 ve M10

| Sinyal | Tanım ve kullanım |
|---|---|
| Kuyruk derinliği ve en yaşlı bekleyen yaşı | Kuyruk başına; birikme alarmlarının temel girdisi (0311) |
| İşlem süresi histogramı | İş sınıfı etiketli; kapasite ve SLA analizi |
| Motor çağrı hata oranı | Bağdaştırıcı etiketli sayaçlar; M8 doğrudan buradan hesaplanır |
| Pencere kapanış oranı | window_end anına kadar completed olan pencere payı; M10 hesabının kaynağı |
| Devralma ve DLQ sayaçları | XAUTOCLAIM olayları ve q:dead girişleri; sağlıksız işçi ve zehirli mesaj tespiti |
| Korelasyon zinciri | job_id tüm loglarda; request_id → job_id → calculation_run_id (0301 §6 adım 9) |

## 10. AVIP için Çıkarımlar

1. 0308 sözleşme kancaları: bağdaştırıcı içi deneme sınıfları, çağrı bütçesi ve motor bazlı eşzamanlılık sınırının kaynağı bu dokümandır; M8 sayaç adları burada sabitlenir.
2. 0309'a iki karar devri: partial yayın kuralı ve digest ile anlamlılık ilişkisinin parametreleri; §6-7 kancaları hazır.
3. 0311'e devirler: alarm eşikleri, DLQ inceleme ve yeniden oynatma prosedürü, kapasite göstergeleri; §9 sinyal seti telemetri planının çekirdeğidir.
4. 0402'ye devir: işçi profillerinin replika sayıları ve Chromium render kapasitesi.
5. FR-F5 (zamanlanmış rapor) kancası hazırdır: report.scheduled sınıfı tanımlı, HT1'de yalnız üretici tarafı açılır.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Telafi penceresi derinliği; digest ve haftalık özet gönderim saatleri [K] | Pilot verisiyle; PO + TL. |
| O-2 | İş türü bazlı devralma boşta kalma süreleri ve deneme tavanları [K] | Pilotta kalibre; TL. |
| O-3 | Anında iletim sınıfının varsayılanı (digest muafiyeti) | M11 dengesi; FR-F2 ayarıyla; PO. |
| O-4 | DLQ yeniden oynatma yetkisi ve denetim kaydı biçimi | 0310 + 0311; TL. |

---

## Kaynaklar

- 0301 System Architecture §6 · uçtan uca hat (bu dokümanın adım ayrıntısı)
- 0303 Database Design · measurement_jobs, event_outbox, Redis anahtar modeli, kısmi indeksler
- 0304 Technology Selection · ADR-005 (Streams + tüketici grupları) ve dönüş yolu
- 0305 Services & Modules · cmd/scheduler ve worker profilleri, platform/queue sorumluluğu
- 0004 Success Metrics · M8, M10, M11 tanımları; K1 koruması

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 8 iş sınıfı ve kuyruk eşlemesi, kilitli tek örnek zamanlayıcı (telafi ve jitter politikalı), outbox dağıtıcısı, tüketici grupları ve XAUTOCLAIM devralma, iki katmanlı yeniden deneme + kota kapısı, digest/haftalık özet işleri, zarif kapanış ve eşzamanlılık sınırları, M8/M10 sinyal seti. |
