# 0309 · Measurement & Scoring Engine

| Alan | Değer |
|---|---|
| Doküman ID | 0309 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş çekirdek doküman: prompt yürütme, örnekleme (n, temperature), skor metodolojisi, calculation_run determinizmi |
| İlişkili | 0308, 0307, 0303, 0302 (girdi); 0310, 0311, 0404 (çıktı); M6, M7, M11, M12, K3, NG8, NG10 |

---

## 1. Amaç ve Kapsam

Bu doküman ölçüm ve hesap çekirdeğinin tasarım sözleşmesini sabitler: örnekleme planı, skor metodolojisi, calculation_run determinizmi, güven aralığı ve fidelite kuralları, anlamlılık ve öneri üretimi. Temel kavramsal ayrım baştan konur: ölçüm doğası gereği stokastiktir (motor yanıtları değişkendir), hesap ise deterministiktir (aynı ham girdi seti her zaman aynı skoru üretir). Kapsam dışı: bağdaştırıcı çağrı mekaniği (0308), kuyruk ve deneme (0307), arayüz sunumu, kural kütüphanesi içerik üretimi (O-4; içerik çalışması ayrı yürür).

## 2. Ölçüm Yürütme ve Örnekleme

Bir ölçüm işi, panel versiyonundaki prompt × motor matrisini örnekleme planıyla çarpar: her hücre için n tekrar [K], istek parametreleri sabitlenmiş biçimde (temperature ve benzeri üretim ayarları yapılandırmada dondurulur ve factor_snapshot'a yazılır) yürütülür. Tekrarlar pencere içine yayılır (0307 jitter'ıyla uyumlu) ve motor eşzamanlılık sınırlarına tabidir. Örnekleme maliyet çarpanıdır: n değeri K1 panel maliyet hesabının doğrudan girdisidir ve paket haklarıyla sınırlanabilir. Arama-yapılmadı bayrağı taşıyan yanıtlar (0308) görünürlük örneklemine dahil edilmez; hücrede bayraklı oran eşiği aşarsa [K] hücre yetersiz-örneklem işaretlenir ve GA buna göre genişler. Ham yanıtların tamamı, örnekleme kararından bağımsız olarak arşivlenir (I5).

## 3. Determinizm ve calculation_run

Hesap saf fonksiyondur: girdisi sıralı ham yanıt kimlik kümesi ve parametre setidir, dış durum okumaz. Her koşu calculation_run kaydı üretir: input_set_hash (sıralı kimlikler + parametrelerin karması), factor_snapshot (bileşen ağırlıkları, eşikler, örnekleme sabitleri), algo_version ve template_version (0302 §5 kural 4: panel ekseninden bağımsız). Yeniden üretim prosedürü (N7): aynı calculation_run girdileriyle hesap yeniden koşulur, üretilen skorlar ve input_set_hash birebir eşleşmek zorundadır; eşleşmeme hata değil olay sayılır ve determinizm alarmı üretir (0311). Ölçümün stokastikliği bu determinizmi bozmaz: değişkenlik ham yanıt kümesindedir ve güven aralığıyla dürüstçe raporlanır; hesap katmanı asla rastgelelik içermez.

## 4. Skor Modeli ve Bileşenler

| Bileşen | Tanım | Kaynak |
|---|---|---|
| Varlık payı | Marka adının (eş anlamlılar dahil) yanıtlarda geçme oranı; tekrarlar üzerinden | Ham içerik |
| Konum ağırlığı | Geçişin yanıt içindeki konumuna göre azalan ağırlık (öne çıkma sinyali) | Ham içerik |
| Kaynak payı | Markaya ait alan adlarının alıntılar içindeki payı | citations[] (FR-D2) |
| Rakip bağlamı | Aynı hücrede izlenen rakip markalara göre normalize edilmiş görünürlük | Marka seti (0302) |

Bileşen ağırlıkları factor_snapshot'ta taşınır; motor bazlı alt skorlar (FR-D1) ve ağırlıklı birleşik skor üretilir. Ölçek kesinleşmiştir: 0-100, numeric(5,2) (0303 O-4 kapanışı). Skor şablonu (template_version) bileşen setini tanımlar; bileşen ekleme algoritma sürümü değişikliği değil şablon sürümü değişikliğidir ve trend dipnotuyla işaretlenir (§9). Duygu/bağlam analizi V1 kapsamı dışıdır; bileşen mimarisi genişlemeye açıktır (0206 platform ufku).

## 5. Güven Aralığı ve Fidelite

GA, hücre örnekleminin varyansından hesaplanır ve birleşik skora bileşen belirsizlikleri taşınarak yayılır; küçük veya yetersiz örneklem geniş aralık üretir. M12 serttir: GA'sız veya fidelite etiketsiz skor hiçbir yüzeye çıkamaz (I3; 0303 CHECK zinciri bunu yapısal kılar). Fidelite eşlemesi mekaniktir: tier_label → fidelite etiketi birebir (direct, official_proxy, directional); eşleme tablo değil kural olduğundan bağdaştırıcı yeni kademe icat edemez. Birleşik skorda kademe sunumu iki katmanlıdır: motor kırılımı kendi etiketlerini gösterir, birleşik özet en muhafazakâr (en düşük) kademeyi taşır ve bileşim notu içerir. Tazelik damgası (freshness_at) hücredeki en yeni ham yanıt zamanıdır (K3).

## 6. Anlamlılık ve Uyarı Kuralları

Uyarı yalnız anlamlı değişimden üretilir (I8). Anlamlılık üç koşulun birleşimidir: (1) karşılaştırılan iki pencerenin güven aralıkları ayrışır, (2) mutlak fark asgari eşiği aşar [K], (3) hücre örneklemleri asgari boyutu sağlar [K]. Koşullar factor_snapshot parametresidir ve calculation_run ile birlikte denetlenebilir. Anlamlı tetik uyarı kaydı üretir ve digest akışına girer (0307 §7); kural sınıfı bazında anında iletim kullanıcı seçimidir. M11 geri bildirimi (yerinde / yanlış alarm) eşik kalibrasyonunun veri kaynağıdır; eşik değişimi yeni algo_version değil parametre güncellemesidir ve snapshot'ta izlenir. Benchmark kıyas uyarıları HT2'ye kadar kapsam dışıdır.

## 7. Kısmi Sonuç Yayın Kuralı (0307 §6 devrinin kapanışı)

Bir pencere kısmi tamamlandığında karar iki eşikle verilir: kapsam eşiği (tamamlanan motor sayısı / panel motor sayısı [K]) ve hücre yeterliliği (§2 örneklem kuralı). Kapsam eşiği sağlanıyorsa skor partial etiketiyle yayınlanır: skor kartı eksik motorları açıkça listeler, birleşik skor yalnız tamamlanan motorlardan hesaplanır ve kapsam notu taşır; eksik motor sonradan tamamlanırsa aynı pencere için yeni calculation_run üretilir ve yayın güncellenir (eski koşu arşivde kalır, I2). Eşik sağlanmıyorsa yayın bekletilir ve 0307 yeniden deneme penceresi işler; pencere kapanışında hâlâ yetersizse pencere yayınsız kapanır ve M10'a eksik yazılır. Kullanıcı iletişimi her durumda dürüst kapsam diliyle yapılır; sessiz boşluk bırakılmaz.

## 8. Öneri Motoru ve NG10 Filtresi

MVP öneri motoru kural tabanlıdır (FR-E1 daraltması): kural = koşul deseni (skor/bileşen/bulgu kombinasyonu) → öneri şablonu. Her şablon kanıt derecesi taşır (deneysel, korelasyonel, denenebilir; 0101 çerçevesi) ve iddia dili NG8/İ4 uyumludur: garanti ve kesinlik ifadesi yoktur, denenebilir öneriler beklenen gözlem cümlesi içerir. Üretim hattı iki kapıdan geçer: NG10 uygunluk filtresi (motor politikalarına aykırı taktik kalıcılaştırılamaz; I7, policy_checked_at kanıtı) ve tekilleştirme (aynı koşul deseninden açık öneri varken kopya üretilmez). Kullanıcı işaretleri (uygulandı / reddedildi) M4 telemetrisine yazılır; etki takibi HT1 penceresinde bu işaretlerin üzerine kurulur. Kural kütüphanesinin ilk içerik seti ayrı çalışmadır (O-4) ve yayına alınması PO onayı ister.

## 9. Trend, Tazelik ve Versiyon İşaretleri

Trend noktaları pencere bazlı üretilir ve panel versiyonuna bağlıdır; versiyon sınırı seride görünür işarettir ve seriler birleştirilmez (0302 §5). İki versiyon ekseni ayrı işaretlenir: panel değişimi (ne soruldu) sınır işareti, algoritma/şablon değişimi (nasıl hesaplandı) dipnot işareti üretir; kullanıcı bir trend kırılmasının kaynağını her zaman ayırt edebilir. Tazelik kuralı yüzeylerde tutarlıdır: pano, e-posta ve rapor aynı freshness_at değerini gösterir; bayat veri uyarısı K3 eşiğine bağlanır. Bu bölümdeki panel versiyon işareti gereksinimi 0204 v1.1 turunda FR-D4 notu olarak resmileşecektir (0302 çıkarım 6 teyidi).

## 10. AVIP için Çıkarımlar

1. 0404 birim test odakları netleşti: determinizm testi (aynı girdi → aynı hash ve skorlar), GA sınır durumları (küçük örneklem, bayraklı oran), tier→fidelite eşleme, anlamlılık koşul matrisi, partial kapsam eşiği, NG10 filtre kapısı. Hesap motoru test kapsamı 0403 CI kapısına bağlanır.
2. 0311'e devirler: determinizm alarmı, anlamlılık kalibrasyon panosu (M11 beslemesi), hesap süresi metrikleri.
3. 0310 bağı: factor_snapshot ve eşik değişiklikleri yapılandırma değişikliği olarak denetim izine düşer (kim, ne zaman, hangi parametre).
4. 0204 v1.1 girdileri kesinleşti: FR-D4 panel versiyon işareti notu ve K7 ölçek teyidi (0-100, numeric(5,2)).
5. Pilot kalibrasyon listesi konsolide [K]: n tekrar sayısı, bayraklı oran eşiği, asgari örneklem, mutlak fark eşiği, kapsam eşiği; tamamı factor_snapshot parametresidir ve pilot çıkış kapısı öncesi gözden geçirilir (0205 §8).

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Örnekleme başlangıç değerleri: n, temperature sabitleri, bayraklı oran eşiği [K] | Pilot öncesi TL önerisi, pilotta kalibrasyon. |
| O-2 | Partial kapsam eşiğinin başlangıcı [K] | PO + TL; dürüst kapsam dili ile birlikte. |
| O-3 | Anlamlılık eşik setinin başlangıcı [K] | TL; M11 geri bildirimiyle kalibre. |
| O-4 | Öneri kural kütüphanesinin ilk içerik seti | AN + PO; NG10 ve iddia dili denetimiyle; yayın PO onaylı. |

---

## Kaynaklar

- 0308 AI Connectors · ProbeResult alanları, arama-yapılmadı bayrağı, tier_label (girdi sözleşmesi)
- 0303 Database Design · calculation_runs/scores kolon sözleşmeleri, CHECK zincirleri (I2, I3)
- 0307 Background Jobs · partial devri, digest akışı, pencere ve M10 ilişkisi
- 0302 Domain Model §5 · iki versiyon ekseni ve panel versiyon kuralları
- 0004 Success Metrics · M6, M7, M11, M12, K3 tanımları; 0101 kanıt derecesi çerçevesi

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: stokastik ölçüm / deterministik hesap ayrımı, örnekleme planı (n, temperature, bayraklı oran), calculation_run ve yeniden üretim prosedürü, dört bileşenli skor modeli (0-100 ölçek kapanışı), GA ve fidelite kuralları (M12), üç koşullu anlamlılık, partial yayın kuralı, kural tabanlı öneri motoru (NG10 + iddia dili), trend versiyon işaretleri ve pilot kalibrasyon listesi. |
