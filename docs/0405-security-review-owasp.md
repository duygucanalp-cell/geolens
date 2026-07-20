# 0405 · Security Review & OWASP Checklist

| Alan | Değer |
|---|---|
| Doküman ID | 0405 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş doküman: 0310 §9 dönemsel halkasının çalışma aracı |
| İlişkili | 0310, 0404, 0403, 0308, 0306 (girdi); 0007 raporlama, 0205 kapı önerisi (çıktı) |

---

## 1. Amaç ve Kapsam

Bu doküman dönemsel güvenlik gözden geçirmesinin çalışma aracıdır: OWASP hizalı kontrol listesi, platforma özgü ek kontroller, bulgu ve yama kaydı formatı ile tur kadansı. Sürekli halka (CI taramaları, 0403) ve sürüm halkası (0404 güvenlik test sınıfları) burada yalnız referanstır; bu doküman üçüncü halkayı, insan yürütümlü dönemsel denetimi tanımlar. Kapsam dışı: tehdit modelinin kendisi (0310 §2), harici sızma testinin yürütümü (tedarik; §6 kapsam tanımı burada), olay müdahalesi (0311 runbook'ları).

## 2. Gözden Geçirme Modeli

Kadans üç seviyedir: her dilim kapanışında hafif tur (yeni açılan yüzeylerin fark analizi + ilgili kontrol satırları), pilot öncesi tam tur (§6) ve sonrasında dönemsel tam tur [K] (öneri çeyreklik; 0401 O-3 doküman gözden geçirme kadansıyla hizalanır, O-2). Yürütüm TL sorumluluğundadır; bulgular §5 formatıyla kaydedilir, özet PO'ya raporlanır ve 0007 haftalık ritmine düşer. Turun kendisi ayrıcalıklı eylemdir: yapıldığı, kapsamı ve sonucu denetim izine işlenir; yapılmamış tur da görünür borçtur.

## 3. OWASP Hizalı Kontrol Listesi (Top 10 kategorileri → platform mekanizması → doğrulama kaynağı)

| Kategori | AVIP mekanizması | Doğrulama |
|---|---|---|
| A01 Erişim kontrolü kırıkları | RBAC matrisi + RLS + ayrımsız NOT_FOUND + IDOR ilkesi (kimlik bilinirliği hak doğurmaz) | 0404 §4-5 paketleri; tur: yeni uçların matris kapsaması |
| A02 Kriptografik hatalar | TLS, S3 SSE, zarf anahtarları (KMS), bellek-sert parola karması, imzalı token'lar | 0310 §3/6/8; tur: yapılandırma ve anahtar envanteri denetimi |
| A03 Enjeksiyon | sqlc parametrik sorgular (dinamik SQL yok), tek noktadan girdi şema doğrulaması, renderer izole süreç | Kod taraması + tur: yeni sorgu yollarının sqlc dışına çıkmadığı |
| A04 Güvensiz tasarım | Değişmez seti (I1-I11), tehdit modeli, güvenli varsayılan davranışlar (kota kapısı, devre kesici, varsayılan ret) | Doküman seti; tur: yeni özelliklerin değişmez etkisi analizi |
| A05 Güvenlik yapılandırma hataları | Ortam eşitliği, açılışta config şema doğrulaması, kapalı iç servisler, en az yetkili hizmet hesapları | 0402 §3/6; tur: ortam farkı ve port taraması |
| A06 Zafiyetli bileşenler | Bağımlılık ve imaj taramaları, bağımlılık envanteri, güncelleme ritmi | 0403 kapı 6 + main; tur: açık zafiyet yaş raporu |
| A07 Kimlik doğrulama hataları | Parola politikası + ihlal listesi, hız sınırlı giriş, oturum sertleştirme, tek kullanımlık sıfırlama | 0404 oturum/CSRF akış testleri; tur: politika uygunluğu |
| A08 Yazılım ve veri bütünlüğü | Değiştirilemez imaj referansı (+ imzalama adayı), denetim zinciri, content_hash, sözleşme senkron kapısı | 0403 §4-5; zincir doğrulama rutini (0311) |
| A09 Loglama ve izleme eksikleri | Denetim izi (yalnız ekleme), güvenlik alarm seti, log hijyeni ve log erişiminin izlenmesi | 0311 envanteri; tur: alarm-runbook eşleşme kontrolü |
| A10 SSRF | Site denetim bileşeni tek sunucu-taraflı getirme yüzeyidir: URL şema/host doğrulama, özel ve iç IP aralıklarının reddi, yönlendirme sınırı, kısa zaman aşımı, çıkış noktası kısıtı | 0404 birim/entegrasyon eki; tur: yüzey envanterinde yeni getirme yolu kontrolü |

A10 satırı bir denetim yakalamasıdır: 0308 §8 ölçüm nezaketini tanımlar ancak SSRF korumalarını açık maddeleştirmez; koruma seti 0308 changelog notu olarak v1.1 kuyruğuna eklenir (O-4).

## 4. AVIP'e Özgü Ek Kontroller

| Kontrol | İçerik |
|---|---|
| Kiracı izolasyon bütünlüğü | Değişmez etiketli matrisin (0404 §4) kapsam güncelliği; yeni tablo/uç/kuyruk yolunun matrise girişi |
| Yalnız-ekleme ve iz bütünlüğü | Trigger varlığı, zincir doğrulama sonuçları, kök karma kayıtları |
| KVKK silme akışı | Anonimleştirme ve kripto-silme yollarının prova kaydı; zarf anahtar erişim politikası |
| Token ve webhook hijyeni | Derin bağlantı kapsam/ömür uygunluğu, webhook imza doğrulaması, sır rotasyon kayıtları |
| Telemetri hijyeni | Prompt/ham içerik/kişisel veri sızıntısı taraması (log örneklemi denetimi) |
| Ayrıcalıklı eylem denetimi | DLQ oynatma, prod erişimi, factor_snapshot değişiklikleri denetim izinde eksiksiz mi |
| Dosya yükleme zinciri | N3 adımlarının (tip, boyut, karma, AV kancası) uçtan uca çalışırlığı |

## 5. Bulgu ve Yama Kaydı

Tüm bulgular tek listede tutulur (depoda docs/security/findings) ve şu alanları taşır: kimlik, tarih, kaynak (tur / CI taraması / sızma testi / kaçak), önem (kritik, yüksek, orta, düşük), kontrol referansı (§3-4 satırı), açıklama ve etkilenen yüzey, yama iş kalemi bağlantısı (izlenebilirlik kimliğiyle; 0401 §3), durum ve kapanış kanıtı. Kapanış kuralı 0404 kaçak kuralıyla aynıdır: bulgu, yeniden oluşumunu yakalayacak test veya tarama referansı olmadan kapanamaz. Önem-SLA eşlemesi sınıf düzeyinde tanımlıdır ve süreler [K] işaretlidir (O-1): kritik derhal (yayın dondurma yetkisiyle), yüksek günler sınıfı, orta iterasyon içi, düşük planlı birikim. Açık kritik/yüksek bulgu sayısı 0007 haftalık özetinin sabit satırıdır.

## 6. Pilot Öncesi Tam Tur

Pilot çıkışından önce tam tur yürütülür: §3-4 listelerinin tamamı, log örneklem denetimi, anahtar ve yüzey envanterlerinin güncellenmesi ve harici sızma testi (kapsam: dış yüzeyler + kiracı izolasyon senaryoları; tedarik ve zamanlama O-3). Çıkış şartı önerisi: açık kritik ve yüksek bulgu sıfır, orta bulgular planlı. Bu şartın pilot çıkış kapısına (0205 §8) yedinci kriter olarak eklenmesi 0310 O-4 ile birleşik PO kararıdır; kabul edilirse 0205 v1.1 changelog notuna işlenir. Tur raporu PO'ya sunulur ve karar defterine kaydedilir.

## 7. AVIP için Çıkarımlar

1. Üç halka tamamlandı: sürekli (0403), sürüm bazlı (0404), dönemsel (bu doküman); güvenlik doğrulaması artık uçtan uca tanımlı ve kayıt disiplinli.
2. v1.1 kuyruğuna iki ekleme: 0308 SSRF koruma maddeleri (O-4) ve 0205 pilot kapısına güvenlik kriteri adayı (§6; PO kararına bağlı).
3. 0404 ile kapanış kanıtı bağı kuruldu: bulgu-test eşlemesi güvenlik regresyonunu yapısal kılar.
4. 0007 raporlama satırı sabitlendi: açık kritik/yüksek bulgu sayısı ve tur durumu haftalık özettedir.
5. Faz 4'te tek doküman kaldı: 0406 Release & Versioning; ardından v1.1 birleşik düzeltme turu penceresi açılır (kuyruk: 0104/0105 çapraz referanslar, 0204 sayım + FR-F7 + FR-D4 notu, 0004/0005 aday listeleri, 0308 SSRF, olası 0205 kapı kriteri).

## 8. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Önem-SLA sürelerinin sayısallaştırılması [K] | Sınıf çerçevesi §5'te; TL. |
| O-2 | Dönemsel tam tur kadansının teyidi | Öneri çeyreklik; 0401 O-3 ile hizalı; PO. |
| O-3 | Harici sızma testinin kapsamı ve tedarik zamanlaması | 0310 O-4 ile birlikte; PO + TL. |
| O-4 | SSRF koruma setinin 0308 v1.1'e işlenmesi | Changelog notu; v1.1 turunda; TL. |

---

## Kaynaklar

- 0310 Security & Multi-Tenancy · üç halkalı çerçeve, tehdit modeli, mekanizma envanteri
- 0404 Test Strategy · doğrulama paketleri ve kaçak/kapanış kuralı
- 0403 CI/CD Pipeline · sürekli halka taramaları (referans)
- 0308 §8 / 0306 · site denetim yüzeyi (A10 yakalaması), token ve webhook sözleşmeleri
- OWASP Top 10 (2021) · kategori çerçevesi

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: üç seviyeli tur kadansı, Top 10 hizalı kontrol tablosu (A10 SSRF yakalaması ve 0308 v1.1 notu dahil), yedi platforma özgü ek kontrol, bulgu-yama kayıt formatı ve kapanış kanıtı kuralı, pilot öncesi tam tur ve kapı kriteri önerisi. |
