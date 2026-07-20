# 0310 · Security & Multi-Tenancy

| Alan | Değer |
|---|---|
| Doküman ID | 0310 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş çekirdek doküman: RBAC, IDOR önleme, izolasyon test stratejisi |
| İlişkili | 0301 §5, 0303, 0306, 0307, 0308, 0309 (girdi); 0311, 0402, 0403, 0404 (çıktı); N1-N6, N12; kapanan: 0203 O-3, 0302 O-1, 0303 O-3, 0306 O-2, 0307 O-4 |

---

## 1. Amaç ve Kapsam

Bu doküman güvenlik ve çok kiracılılık mimarisini sabitler ve önceki dokümanlardan devredilen güvenlik açıklarının tasarım kapanışını yapar: rol modeli, izolasyon doğrulama stratejisi, oturum ve token kriptografisi, veri koruma ve silme uzlaşımı, iz bütünlüğü ve sır yönetimi. Kapsam dışı: SOC 2 program yönetimi ve kanıt toplama (kurumsal kapı; W5 borcu), altyapı ağ topolojisi ve güvenlik grupları (0402), olay müdahale runbook'ları (0311).

## 2. Tehdit Modeli ve İlkeler

| Tehdit | Birincil savunma |
|---|---|
| Kiracılar arası veri sızıntısı | Beş katmanlı izolasyon (0301 §5) + negatif test kapısı (§5) |
| Yetki yükseltme (kiracı içi) | RBAC matrisi + sunucu tarafı zorlaması (§4); istemci asla otorite değildir |
| Hesap ele geçirme | Parola politikası, hız sınırlı giriş, oturum sertleştirme (§3) |
| Motor anahtarlarının sızması | Kasa yönetimi, log hijyeni, rotasyon (§8; 0308 §9) |
| Denetim izi tahrifi | Yalnız-ekleme + karma zinciri + nesne kilidi (§7) |
| Derin bağlantı ve webhook kötüye kullanımı | İmzalı token kapsam sınırı, HMAC + zaman damgası (§3, §8) |
| Otomasyon kötüye kullanımı (kota/maliyet saldırısı) | Hız sınırı + kota kapısı + bütçe tavanı (K1; 0307-0308) |

İlkeler: en az yetki, varsayılan ret, katmanlı savunma, tam denetlenebilirlik (her ayrıcalıklı eylem iz bırakır) ve gizliliğe dayalı güvenlik yok (kimlik bilinirliği erişim hakkı doğurmaz, §5).

## 3. Kimlik Doğrulama ve Oturum

Parola politikası: asgari uzunluk + bilinen ihlal listesi kontrolü; karma modern bellek-sert algoritmayla saklanır. Giriş uçları hız sınırlı ve hesap bazlı artan geciktirmelidir. Oturum sunucu tarafında kayıtlıdır; tarayıcıya yalnız httpOnly, Secure, SameSite=Lax çerezle opak oturum kimliği verilir; mutlak ve kayan süre birlikte uygulanır [K]; parola değişiminde diğer oturumlar düşürülür. CSRF koruması durum değiştiren tüm isteklerde zorunludur (senkronizasyon token deseni; 0306 §3). Parola sıfırlama tek kullanımlık kısa ömürlü token iledir. Derin bağlantı token'ları (0306 O-2 kapanış önerisi): sunucu imzalı, tek kiracı + tek hedef kapsamlı, kısa ömürlü [K]; tek kullanım yerine sınırlı çoklu kullanım önerilir (çok cihaz açılışı M1 dönüşümünü korur), oturum gerektirir ve yetkiyi asla tek başına vermez (token yalnız hedefe yönlendirir, erişim kararını RBAC verir). SSO (FR-A4) kurumsal kapı yer tutucusudur; MFA zamanlaması O-1'dedir.

## 4. Rol Modeli ve RBAC (0203 O-3 ve 0302 O-1 kapanışı)

V1 rol seti iki roldür: Yönetici ve Üye. Erişim kapsamı kararı: üyelik kiracı düzeyindedir ve isteğe bağlı çalışma alanı erişim listesi taşır; liste boşsa üye tüm alanlara erişir, doluysa yalnız listelenen alanlara erişir. Bu model standart kiracıda sıfır ek yük, ajans kiracısında müşteri bazlı personel kısıtı sağlar (koltuk politikasıyla uyumlu). Rol paket hakkından ayrıdır: rol kim ne yapabilir sorusudur, entitlement kiracı neye sahip sorusudur; zincir sırası RBAC → entitlement (0305 §5) korunur.

| Eylem grubu | Yönetici | Üye |
|---|---|---|
| Okuma: skor, trend, alıntı, bulgu, öneri, rapor | Evet | Evet (erişim listesi dahilinde) |
| Ölçüm tetikleme, öneri işaretleme, uyarı geri bildirimi | Evet | Evet (erişim listesi dahilinde) |
| Yapılandırma: marka, prompt seti, eşik, kanal, rapor şablonu | Evet | Evet (erişim listesi dahilinde) |
| Çalışma alanı açma/arşivleme | Evet | Hayır |
| Üye/davet yönetimi, erişim listeleri, paket ve kullanım görünümü | Evet | Hayır |
| Kanal sırları, webhook yönetimi, KVKK silme talebi başlatma | Evet | Hayır |

Genişletilmiş rol ihtiyacı (salt-okur müşteri erişimi gibi) HT1 adayıdır ve rol setine ekleme Tip 2 karardır. Tüm ayrıcalıklı eylemler (son satırlar) denetim izine düşer.

## 5. İzolasyon ve IDOR Önleme

İzolasyon iddiası test stratejisiyle birlikte tanımlıdır; mekanizma ve doğrulaması ayrılmaz:

| Katman (0301 §5) | Doğrulama (0404 sınıfları; CI kapısı 0403) |
|---|---|
| Uygulama sözleşmesi (kiracı bağlam zorunluluğu) | Depo katmanı testleri: bağlamsız sorgu derlenemez/çalışamaz; lint kuralı |
| RLS (SET LOCAL app.tenant_id) | Gerçek PG üzerinde negatif testler: A kiracısı oturumuyla B verisi okuma/yazma/listeleme girişimleri sıfır satır döner |
| API davranışı | Sözleşme testi: kiracı dışı kimlikle istek ayrımsız NOT_FOUND (0306 §6); hata gövdesi varlık sızdırmaz |
| Kuyruk yükü doğrulaması | İşçi testi: yük içi kiracı kimliği ile iş kaydı uyuşmazsa iş reddedilir ve alarm üretir |
| S3 önek ve imzalı erişim | Politika testi: çapraz kiracı önekine imzalı URL üretilemez; anahtar şeması denetimi |

IDOR ilkesi: nesne kimliğinin bilinmesi hiçbir katmanda erişim hakkı doğurmaz; ULID tahmin-zorluğu bir savunma sayılmaz. RLS oturum disiplini: havuzdan alınan her bağlantıda işlem başında bağlam kurulur, işlem sonunda sıfırlanır; bağlamsız yol yalnız kimlik doğrulama ve sistem tablolarındadır ve ayrıca listelenir.

## 6. Veri Koruma ve KVKK Uzlaşımı (0303 O-3 kapanışı)

Şifreleme: aktarımda TLS; durağanda veritabanı disk şifrelemesi ve S3 sunucu tarafı şifreleme (N5). Kişisel veri envanteri dardır: kullanıcı kimlik bilgileri (e-posta, ad), davetler ve denetim izindeki aktör referansları; müşteri verisi (promptlar, markalar, ham yanıtlar) ayrı sınıftır. Denetim özetlerinde hassas alanlar yazım anında maskelenir (0303). Silme uzlaşımı iki mekanizmayla çözülür ve yalnız-ekleme değişmezleri ihlal edilmez: (1) Kişi silme talebi: kullanıcı kaydı anonimleştirilir (kimlik alanları geri döndürülemez biçimde değiştirilir); denetim izi bozulmaz çünkü aktör referansı opak kimliktir ve kişisel alan taşımaz. (2) Kiracı verisi silme: ham arşiv için kiracı başına zarf anahtarı altyapısı V1'de kurulur; S3 nesneleri bu anahtarla şifrelenir, meşru silme talebinde anahtar imha edilir (kripto-silme) ve veri kalıcı erişilemez hale gelir; nesne kilidi ve arşiv bütünlüğü fiziksel olarak korunur. Saklama süreleri politikası 0204 O-4 kararıyla birlikte yürürlüğe girer (O-3 bağlantısı).

## 7. İz Bütünlüğü ve Nesne Kilidi

Denetim zinciri doldurma (0303 devri): her kayıt için entry_hash, önceki kaydın karması ile kanonik kayıt içeriğinin birleşiminden hesaplanır; zincir kiracı bazlıdır ve governance yazıcısı tek kapı olduğundan (0305 D5) sıra tutarlıdır. Doğrulama rutini periyodik iştir (0311): zincir baştan taranır, kök karma günlük olarak ayrı kayda alınır; kopukluk kritik alarm üretir. S3 nesne kilidi kararı: ham arşiv kovasında governance modu + saklama süresi [K] uygulanır; compliance modu kurumsal kapı değerlendirmesine bırakılır (operasyonel geri dönüş esnekliği gerekçesiyle). content_hash çapraz doğrulaması (0303) nesne ve meta bütünlüğünü ilişkilendirir; üç mekanizma birlikte M14 iddiasını taşır: iz değiştirilemez, silinme fark edilir, içerik doğrulanabilir.

## 8. Sırlar ve Anahtar Rotasyonu

| Sır sınıfı | Kullanım | Rotasyon |
|---|---|---|
| Sağlayıcı API anahtarları | Motor bağdaştırıcıları (0308 §9) | Kadanslı [K]; sağlayıcı panelinden çift anahtar penceresi |
| Veritabanı ve Redis kimlikleri | Platform bağlantıları | Dağıtım penceresinde; kasadan |
| Oturum ve derin bağlantı imza anahtarları | §3 token'ları | Çift anahtar kabul penceresiyle (yeni imzalar, eski doğrular) [K] |
| Webhook imza sırları | Kanal bazlı HMAC (0306 §8) | Kanal yönetiminden kiracı tetikli + platform kadansı |
| Kiracı zarf anahtarları | Ham arşiv şifreleme (§6) | Rotasyon yok; yalnız imha (kripto-silme) ve yedek koruması |

Tüm sırlar ortam/kasa kaynaklıdır (N4); koda, loga ve hata mesajına giremez. DLQ yeniden oynatma yetkisi (0307 O-4 kapanışı): yalnız platform operasyon rolüne aittir (kiracı rolü değil), her oynatma gerekçe alanıyla denetim izine düşer ve oynatılan mesaj kimlikleri kaydedilir.

## 9. Güvenlik Doğrulama Çerçevesi

Doğrulama üç halkada işler. Sürekli (CI, 0403): bağımlılık ve imaj taraması, sır sızıntı taraması, lint import kuralları, §5 negatif test paketi, sözleşme sızıntı testleri. Sürüm bazlı (0404 test sınıfları): RBAC matrisi testleri (her eylem grubu × rol), dosya yükleme zinciri (N3: tip beyaz listesi, boyut sınırı, içerik karması, virüs tarama kancası), hata sözlüğü bilgi sızıntısı denetimi, oturum ve CSRF akış testleri, izolasyon regresyonu. Dönemsel: OWASP hizalı kontrol listesi gözden geçirmesi (erişim kontrolü, kimlik doğrulama, girdi doğrulama, kriptografi, loglama başlıkları) ve pilot çıkışı öncesi harici sızma testi önerisi (O-4; kapı kriterlerine eklenmesi PO kararı). Bulgular tek kayıtta izlenir ve kapanışları denetim izine bağlanır.

## 10. AVIP için Çıkarımlar

1. Beş devir kapandı: rol modeli (0203 O-3 + 0302 O-1), KVKK uzlaşımı (0303 O-3), derin bağlantı token tasarımı (0306 O-2), DLQ yetkisi (0307 O-4); 0007 defterine işlenir.
2. 0311'e devirler: zincir doğrulama rutini ve kök karma saklama, rotasyon runbook'ları, güvenlik alarm seti (izolasyon reddi, zincir kopukluğu, bütçe/kota anomalisi).
3. 0403/0404 girdileri hazır: §5 doğrulama tablosu ve §9 test sınıfları doğrudan test planına iner; izolasyon paketi CI'da zorunlu kapıdır.
4. 0402'ye devirler: kasa/KMS sınıfı seçimi, zarf anahtar altyapısının sağlayıcı karşılığı, TLS sonlandırma ve sertifika yönetimi.
5. Kurumsal kapı devri netleşti: SSO, MFA politikası, compliance modu değerlendirmesi ve SOC 2 kanıt programı (W5) tek pakette kapı penceresine bağlandı.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | MFA zamanlaması (Yönetici rolü için HT1 adayı) | PO + TL; kurumsal kapı öncesi değer sinyali. |
| O-2 | Oturum ve derin bağlantı sürelerinin başlangıç değerleri [K] | TL; M1 dönüşümü ile güvenlik dengesi; pilotta kalibre. |
| O-3 | Nesne kilidi saklama süresi; saklama politikası bütünü | 0204 O-4 ile birlikte; PY + TL. |
| O-4 | Harici sızma testinin pilot çıkış kapısına eklenmesi | 0205 §8 kriter seti revizyonu; PO. |

---

## Kaynaklar

- 0301 System Architecture §5 · beş katmanlı izolasyon (doğrulama tablosunun mekanizma tarafı)
- 0303 Database Design · K3/K4 korumaları, audit_log zincir kolonları, O-3 gerilimi (girdi)
- 0306 API Design · oturum/CSRF devri, NOT_FOUND kuralı, derin bağlantı ve webhook sözleşmeleri
- 0307/0308/0309 · DLQ yetkisi devri, anahtar yönetimi, factor_snapshot denetim bağı
- 0204 PRD · N1-N6, N12 gereksinimleri; 0203 O-3 ve 0302 O-1 açıkları (kapanan)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: yedi maddelik tehdit envanteri, oturum/CSRF ve derin bağlantı token tasarımı, iki rollü RBAC + isteğe bağlı çalışma alanı erişim listesi (O-3/O-1 kapanışı), katman-doğrulama eşli izolasyon stratejisi, anonimleştirme + zarf anahtarlı kripto-silme (KVKK uzlaşımı), karma zinciri ve governance modlu nesne kilidi, beş sınıflı sır rotasyonu, üç halkalı doğrulama çerçevesi. |
