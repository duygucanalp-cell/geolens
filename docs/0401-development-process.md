# 0401 · Development Process

| Alan | Değer |
|---|---|
| Doküman ID | 0401 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.4 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 25 · Development Process (git akışı, branching, code review, DoD) |
| İlişkili | 0305, 0306, 0303, 0007, 0205 (girdi); 0403, 0404, 0406 (çıktı) |

---

## 1. Amaç ve Kapsam

Bu doküman geliştirme sürecinin işleyiş sözleşmesini sabitler: dallanma modeli, iş kalemi yaşam döngüsü, gözden geçirme disiplini, bitmişlik tanımı, doküman-kod senkronu ve uygulama dilim planı. Faz 4'ün açılış dokümanıdır; süreç kuralları burada, teknik zorlamaları 0403'te (CI kapıları), test ayrıntısı 0404'te yaşar. Kapsam dışı: kadro ve rol atamaları (set genel kuralı), iş takip aracının seçimi (O-1), sürüm numaralandırma ve yayın treni (0406).

## 2. Akış Modeli ve Dallanma

Model trunk-based'dir: main her an dağıtılabilir durumdadır; iş kısa ömürlü dallarda yapılır ve küçük PR'larla main'e döner. Uzun süren işler bayrak arkasında parça parça birleşir (feature flag; yarım özellik main'de kapalı bayrağıyla yaşayabilir, yarım dal haftalarca açık kalamaz). Dal adlandırma iş kalemi kimliğiyle başlar; dallar birleşince silinir. Yayın dalı yoktur; sürüm etiketleme ve yayın treni 0406'da tanımlanır. Acil düzeltme aynı akıştan gider: hotfix dalı → hızlandırılmış gözden geçirme (§4 istisnası) → main → yayın.

## 3. İş Kalemi Yaşam Döngüsü

İş kalemleri doküman setinden türetilir ve izlenebilirlik kimliği taşır: her kalem ilgili FR/NFR, UC, değişmez (I) veya doküman bölümüne bağlanır; kaynağı olmayan iş kalemi açılmaz (kapsam sızmasının süreç freni). Durum akışı beş adımdır: hazır → geliştirmede → gözden geçirmede → doğrulamada → bitti; doğrulama adımı DoD kontrolüdür (§5). Tahminleme hafiftir (küçük/orta/büyük); büyük kalem bölünmeden geliştirmeye giremez. Devam eden iş sınırlıdır: kişi başına aynı anda tek geliştirmede kalem normu; tıkanıklıkta yeni iş açmak yerine gözden geçirme ve doğrulama kuyruğu eritilir.

## 4. Kod Gözden Geçirme ve PR Disiplini

Her değişiklik PR ile gelir; kendi kendine birleştirme yasaktır ve en az bir onay gerekir; CODEOWNERS yönlendirmesi bağlam sahibini işaretler (0305 §8). PR küçüklüğü normdur: tek amaç, tek kalem; büyük PR gözden geçirilemez ve bölünmesi istenir. PR şablonu dört alanı zorunlu kılar: amaç ve kapsam, izlenebilirlik kimlikleri, test kanıtı (koşulan paketler), etki etiketi (migration, sözleşme, güvenlik, telemetri). Migration ve sözleşme etiketli PR'lar ilgili kural setine referans verir (0303 §6, 0306 §2) ve 0403 kapılarından geçmeden birleşemez. Hotfix istisnası: tek onay + sonradan tam gözden geçirme kaydı; istisnanın sınırları O-4'te netleştirilir. Gözden geçirme üslubu yazılıdır ve gerekçelidir; onay, DoD sorumluluğunu paylaşmaktır.

## 5. Definition of Done

| # | Kriter |
|---|---|
| 1 | Kod tamam ve tüm testler yeşil; hesap motoru veya izolasyon yüzeyine dokunan değişikliklerde ilgili birim ve negatif test paketleri zorunlu koşulur (0404). |
| 2 | Lint, biçim ve import kuralları temiz (0305 D1-D7; depguard). |
| 3 | Sözleşme ve şema drift kontrolleri geçti (OpenAPI senkronu, migration baştan uygulanabilirliği). |
| 4 | Dokümantasyon etkisi işlendi: tasarım sözleşmesi değiştiyse ilgili dokümana changelog notu veya v1.1 kuyruğuna kayıt (§6). |
| 5 | Gözlemlenebilirlik eklendi: yeni uç veya iş sınıfı metrik ve log sözleşmesiyle geldi (0311 kataloğu güncel). |
| 6 | Güvenlik maddeleri işaretli: girdi doğrulama, yetki denetimi, sır hijyeni (0310 §9 sürekli halka). |
| 7 | Feature flag durumu net: bayrak adı, varsayılan durumu ve kaldırma koşulu kayıtlı. |
| 8 | Denetim izi etkisi değerlendirildi: ayrıcalıklı yeni eylem varsa audit yazımı eklendi (N6). |

## 6. Doküman-Kod Senkronu

Bu doküman seti canlı sözleşmedir; kod onu sessizce eskitemez. Kural: bir değişiklik herhangi bir dokümandaki tasarım sözleşmesini değiştiriyorsa, PR ya ilgili dokümanın changelog güncellemesini içerir ya da v1.1 kuyruğuna kayıt düşer; hangisi olacağına değişiklik tipi karar verir. Tip 1 değişiklik PO onayı ister ve 0007 karar defterine işlenir; Tip 2 changelog + haftalık özetle yürür (0007 süreci). Yeni mimari karar ADR olarak docs/adr altına yazılır ve 0304 changelog'una bağlanır. Drift iki yerde yakalanır: teknik kapılar (0403: OpenAPI, şema, tip senkronu) ve dönemsel doküman gözden geçirme kadansı (O-3); gözden geçirmede kod-doküman farkları listelenir ve kapatılır.

## 7. Walking Skeleton ve Dilim Planı

| Dilim | Kapsam | Çıkış kanıtı |
|---|---|---|
| 1 · İskelet | platform (db, httpmw, telemetry) + identity (kayıt/oturum) + config (marka + panel asgari) + measure (tek bağdaştırıcı) + governance (denetim yazıcısı, kota iskeleti); uçtan uca: tek prompt ölçümü → calculation_run → etiketli skor | Canlı ortamda uçtan uca demo; korelasyon zinciri logda izlenir |
| 2 · Ölçüm tam | Kalan iki bağdaştırıcı, örnekleme ve GA tam (0309), pano temel görünümleri, site denetimi (UC-04) | Üç motorlu panel skoru + saniyeler içinde denetim bulgusu |
| 3 · Değer halkası | delivery (uyarı, digest, haftalık özet, rapor) + insight (kural tabanlı öneri + NG10) | Derin bağlantılı e-posta özeti + PDF rapor + öneri akışı |
| 4 · Sertleştirme | 0310 paketlerinin tamamı (zincir, kripto-silme altyapısı, rotasyon), 0311 alarm seti, kalibrasyon provası | Pilot çıkış kapısı ön kontrol listesi (0205 §8) yeşil |

Dilim 1 bağdaştırıcı seçimi O-2 kararıdır (öneri: Perplexity; direct kademe ve en yalın alıntı modeli iskelet riskini düşürür). Her dilim kapanışı demo + doküman senkron kontrolü içerir; dilim atlanarak ilerlenmez.

## 8. Çalışma Ritmi ve İletişim

Ritim haftalıktır: hafta açılışında kısa planlama (dilim hedefi ve kalem seçimi), hafta kapanışında demo ve gözden geçirme; karar defteri (0007) haftalık işlenir ve Tip 2 özetleri burada duyurulur. İletişim asenkron-önceliklidir: tartışmalar yazılı iz bırakır, toplantı yalnız senkron karar gerektiğinde yapılır ve sonucu yazıya döner. Engeller günlük görünür kılınır; bir kalem iki günden uzun engelli kalırsa eskalasyon (TL) zorunludur. Bu ritim pilot dönemine de taşınır ve 0205 kapı gözden geçirmeleri aynı kadansa oturur.

## 9. AVIP için Çıkarımlar

1. 0403 bu sürecin teknik zorlamasıdır: dal koruması, zorunlu kontroller ve etiket bazlı kapılar §4-5 kurallarını otomatikleştirir.
2. 0404, DoD'nin 1 numaralı kriterini ayrıntılandırır: zorunlu paketler, negatif test setleri ve kapsam eşikleri orada tanımlanır.
3. 0406 yayın treni §2 akışının üzerine kurulur: etiketleme, sürüm notları ve geri alma prosedürü.
4. 0007 defterine süreç bağı netleşti: karar işleme ritmi haftalık; ADR ekleme yolu tanımlı.
5. v1.1 düzeltme turu hatırlatması: birleşik pencere Faz 4 doküman setinin kapanışıdır; tur yapılana dek 0104/0105/0204 düzeltmeleri ve aday listeleri kuyrukta bekler (0206 O-4).

## 10. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~İş takip aracının seçimi~~ | ~~Süreçten bağımsız; izlenebilirlik kimliği alanı şart; TL.~~ |
| ✅ O-1 | İş takip aracı seçimi | **KAPANDI** (21.07.2026): GitHub Projects/Issues. Depo ile aynı platform. İzlenebilirlik kimlik alanı eklenecek. |
| ~~O-2~~ | ~~Dilim 1 bağdaştırıcısının teyidi~~ | ~~Öneri Perplexity (§7 notu); TL.~~ |
| **✅ O-2 (KAPANDI)** | **Perplexity (Sonar API). Direct kademe, en yalın alıntı modeli — iskelet riskini düşürür.** | **TL kararı (21.07.2026). 0007 D-42.** |
| ~~O-3~~ | ~~Doküman gözden geçirme kadansının başlangıcı~~ | ~~Öneri çeyreklik; ilk tur pilot öncesi; PO.~~ |
| **✅ O-3 (KAPANDI)** | **İlk tur pilot öncesi — Faz 4 başlamadan tüm doküman seti gözden geçirilecek (v1.1 düzeltme turu). Çeyreklik döngü pilot sonrası devreye girer.** | **PO kararı (21.07.2026). 0007 D-48.** |
| ~~O-4~~ | ~~Hotfix istisnasının sınırları~~ | ~~Hangi etiketlerde istisna geçersiz (migration, güvenlik); TL.~~ |
| ✅ O-4 | Hotfix istisnasının sınırları | **KAPANDI** (21.07.2026): migration ve güvenlik etiketli PR'lar hotfix yolundan geçemez, tam PR süreci zorunlu. Hotfix yolu: tek onay + sonradan tam gözden geçirme. |

---

## Kaynaklar

- 0305 Services & Modules · CODEOWNERS, iskelet dilimi, Tip 2 iskelet kuralı
- 0303 §6 / 0306 §2 · migration ve sözleşme değişiklik kuralları (PR etiketlerinin kaynağı)
- 0007 Governance · Tip 1/Tip 2 süreci ve karar defteri ritmi
- 0205 MVP Scope §8 · pilot çıkış kapısı (dilim 4 hedefi)
- 0310 §9 / 0311 · DoD güvenlik ve gözlemlenebilirlik maddelerinin dayanakları

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: trunk-based akış ve bayrak kuralı, izlenebilirlik kimlikli iş kalemi döngüsü, dört alanlı PR şablonu ve hotfix istisnası, 8 maddelik DoD, doküman-kod senkron kuralı, dört dilimlik walking skeleton planı, haftalık ritim ve eskalasyon kuralı. |
| 1.1 | 21.07.2026 | O-1 kapandı: GitHub Projects/Issues seçildi. İzlenebilirlik kimlik alanı şart. |
| 1.2 | 21.07.2026 | O-4 kapandı: hotfix migration+güvenlik etiketlerinde geçersiz. |
| 1.3 | 21.07.2026 | O-2 kapandı: Dilim 1 bağdaştırıcısı Perplexity (Sonar API). 0007 D-42. |
| 1.4 | 21.07.2026 | O-3 kapandı: doküman gözden geçirme ilk tur pilot öncesi. 0007 D-48. |