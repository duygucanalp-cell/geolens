# 0406 · Release & Versioning

| Alan | Değer |
|---|---|
| Doküman ID | 0406 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.4 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş doküman: sürümleme ve yayın politikası (Faz 4 kapanışı) |
| İlişkili | 0401, 0403, 0302 §5, 0306, 0309, 0205, 0007 (girdi); yayın operasyonu (çıktı) |

---

## 1. Amaç ve Kapsam

Bu doküman yazılım sürümleme ve yayın politikasını sabitler: sürüm uzaylarının ayrımı, şema, yayın treni, sürüm notu disiplini ve geri alma kuralları. Kapsam dışı: dağıtım mekaniği (0403; burada yalnız politika), doküman seti sürümlemesi (0007), paket ve fiyat duyuruları (set genel kuralı). Faz 4'ün ve v1.0 doküman setinin kapanış dokümanıdır.

## 2. Sürüm Uzayları ve Ayrımı

Üç bağımsız versiyon uzayı vardır ve birbirine karıştırılmaz:

| Uzay | Kapsam | Sahibi |
|---|---|---|
| Platform yazılım sürümü | Bu dokümanın konusu: dağıtılan imaj setinin sürümü (§3) | Yayın treni (§4) |
| API ana sürümü | /v1 sözleşme uzayı; genişleme serbest, kırılım yeni ana sürüm (0306 §2); platform sürümünden bağımsız yaşar | 0306 kuralları; /v2 kararı Tip 1 |
| Ölçüm versiyonları | Panel versiyonu (kiracı bazlı, ne soruldu) ve algo/template sürümleri (nasıl hesaplandı); yazılım sürümüyle ilişkisiz, append-only (0302 §5, 0309 §9) | Ürün; trend işaretleri |

Bu ayrım kullanıcı iletişiminin dürüstlük temelidir: bir yazılım sürümü algo veya şablon sürümünü değiştiriyorsa sürüm notu bunu ayrı başlıkta ve trend etkisiyle duyurmak zorundadır (§5).

## 3. Sürüm Şeması

Şema SemVer'dir: MAJOR.MINOR.PATCH. Pilot dönemi 0.x uzayında yürür (0.MINOR.PATCH); 1.0.0 etiketi ticari genel açılış anına ayrılmıştır ve pilot çıkış kapısının geçilmesine bağlıdır (0205 §8; O-1 teyidi). MAJOR: API ana sürüm kırılımı veya eşdeğer ürün dönümü (Tip 1 karar); MINOR: özellik yayını, bayrak açılışları ve algo/template sürüm değişimleri dahil; PATCH: düzeltme ve hotfix. Etiketleme main üzerinde git tag ile yapılır ve 0403 main hattı imaj setini bu etiketle bağlar; etiketlenmemiş imaj production'a terfi edemez.

## 4. Yayın Treni ve Kadans

Pilot dönemi kadansı haftalık trendir ve 0401 ritmiyle hizalıdır: hafta kapanış demosundan çıkan main durumu staging'de doğrulanır, ertesi iş günü terfi penceresinde production'a alınır. Tren kuralları: tren kimseyi beklemez, treni kaçıran özellik (veya kapalı bayrağı) bir sonrakine kalır; hotfix trenden bağımsız PATCH olarak çıkar (0403 hotfix yolu); kapı değerlendirme haftası gibi dondurma pencerelerinde yalnız PATCH çıkar. Migration içeren yayınlarda genişlet-daralt kuralının tren karşılığı uygulanır: genişlet fazı N treninde, daralt fazı en erken N+1 treninde gider; aynı trende genişlet ve daralt yasaktır. Böylece her üretim sürümü, bir önceki sürümün şemasıyla çift yönlü uyumlu kalır (§6 garantisinin kaynağı).

## 5. Sürüm Notları ve Duyuru

İki katman vardır. İç sürüm notu her sürümde zorunludur: değişiklik listesi izlenebilirlik kimlikleriyle (FR/UC/I), migration ve bayrak durum değişimleri, algo/template sürüm hareketleri ve bilinen sorunlar. Kullanıcıya dönük not yalnız görünür değişikliklerde yazılır: Türkçe, dürüst iddia diliyle (İ4; abartısız, garanti ifadesiz) ve algo veya şablon sürümü değiştiyse trend etkisinin açıklaması zorunludur (0309 §9 dipnot işaretiyle tutarlı: kullanıcı kırılmanın kaynağını üründe de duyuruda da görür). Duyuru kanalı ürün içi bildirimdir; davranış değiştiren büyük değişimlerde e-posta eklenir. Not şablonları depoda yaşar (O-3).

## 6. Geri Alma ve Uyumluluk Politikası

Öncelik sırası: (1) Bayrakla geri alma: davranış sorunlarında ilk tercih bayrağı kapatmaktır; dağıtım gerekmez. (2) İmaj geri terfisi: bir önceki sürüm her zaman terfi edilebilir tutulur; §4 tren kuralı sayesinde N sürümü N-1 şemasıyla, N-1 kodu N şemasıyla çalışır (çift yönlü pencere bir sürümdür, daha eskiye dönüş taahhüt edilmez). (3) Veri: geri migration yoktur, ileri-düzeltme uygulanır (0303 §6). API uzayında /v1 içinde kırıcı değişiklik yasaktır; kırıcı ihtiyaç /v2 kararıdır (Tip 1). Ölçüm versiyonları geri alınmaz: hatalı algo/şablon sürümü yeni sürümle düzeltilir, etkilenen pencereler için yeni calculation_run koşulur ve eski koşular arşivde kalır (I2); kullanıcıya düzeltme, versiyon dipnotuyla açıklanır. Geri alma olayları sürüm kaydına ve 0403 hat telemetrisine işlenir.

## 7. Sürüm Yaşam Döngüsü Kayıtları

Her sürümün kaydı depodaki CHANGELOG'a düşer: tarih, etiket ve imaj özeti, migration listesi, bayrak hareketleri, algo/template sürümleri, geri alma olayları ve bilinen sorunlar; kayıt 0007 haftalık özetine bağlanır. Desteklenen sürüm modeli tektir: SaaS işletiminde production'da tek sürüm yaşar, çoklu sürüm bakımı yoktur (self-host V1 kapsamı dışıdır; kurumsal kapının adanmış örnek istisnası aynı sürüm setini izler, 0304 §5). Sürüm kaydı, kapasite ve kalite gözden geçirmelerinin (0311, 0403 §7) referans eksenidir.

## 8. AVIP için Çıkarımlar (Faz 4 ve set kapanışı)

1. Faz 4 tamamlandı: 0401-0406 uygulamaya iniş katmanını sabitledi; süreç, ortam, kapılar, test, güvenlik denetimi ve yayın politikası uçtan uca bağlı.
2. v1.0 doküman seti bu teslimatla tamamlandı: Faz 0 (0000-0007), Faz 1 (0101-0105), Faz 2 (0201-0206), Faz 3 (0301-0311), Faz 4 (0401-0406); 36 doküman, tüm ADR'ler Kabul, D-02 ve D-03 kapalı.
3. v1.1 birleşik düzeltme turu penceresi resmen açık. Konsolide kuyruk: 0104/0105 çapraz referans düzeltmeleri; 0204 sayım (30/15) + FR-F7 (CSV) + FR-D4 panel işaret notu + K7 ölçek teyidi; 0004 metrik adayları; 0005 terim adayları; 0308 SSRF koruma maddeleri; 0205 kapı kriteri adayı (PO kararına bağlı). Tur, sözleşme gereği tek geçişte yapılır.
4. PO'da bekleyen program kararları: 0002 O-1 (P1 segment aktif satış), 0006 O-3 (isim finali: Mentiq/Vizora/Visanta), 1.0.0 anının teyidi (O-1), pilot kapısına güvenlik kriteri (0405 §6).
5. Uygulama başlangıcının ön koşulları hazır: dilim 1 (0401 §7) bu setin üzerinde açılır; kalibrasyon gündemi ([K] konsolidasyonu: 0309 §10 + 0311 §10) pilot planına devredilir.

## 9. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~1.0.0 anının tanımının teyidi (kapı geçişi = genel açılış)~~ | ✅ **KAPANDI**: 1.0.0 = ticari genel açılış (GA). Pilot çıkış kapısı (0205 §8) geçilince. Pilot dönemi 0.x uzayında. PO teyidi alındı. 0007 D-82. |
| ~~O-2~~ | ~~Tren gününün ve terfi penceresinin sabitlenmesi~~ | ✅ **KAPANDI**: Cuma (tren — staging doğrulama) → Pazartesi (terfi — production). Haftasonu doğrulama ve hata ayıklama penceresi. Pilot dönemi geçerlidir. 0007 D-77. |
| O-3 | Sürüm notu şablonlarının hazırlanması | **AN+PO eylem planı (21.07.2026):** Pilot öncesi AN hazırlar (iç + kullanıcı şablonları), PO onaylar. Depoda docs/release-templates/ altında yaşar. v1.1 düzeltme turuyla birlikte tamamlanır. 0007 D-91. |
| ~~O-4~~ | ~~Dondurma pencereleri takvimi~~ | ~~Pilot planı ve kapı haftalarıyla; PO + TL.~~ |
| **✅ O-4 (KAPANDI)** | **Kapı değerlendirme haftaları (pilot çıkış kapısı öncesi + kapı haftası) ve yılbaşı tatili (2 hafta). Bu haftalarda yalnız PATCH çıkar.** | **PO+TL kararı (21.07.2026). 0007 D-57.** |

---

## Kaynaklar

- 0403 CI/CD Pipeline · imaj etiketleme, terfi mekaniği, hotfix yolu (politikanın zoru)
- 0302 §5 / 0309 §9 · ölçüm versiyon eksenleri ve trend işaretleri (uzay ayrımının kaynağı)
- 0306 §2 · API sürümleme ve genişletme kuralları
- 0303 §6 · genişlet-daralt ve ileri-düzeltme (tren ve geri alma kurallarının temeli)
- 0205 §8 / 0007 · pilot kapısı (1.0.0 bağı), Tip 1/2 karar süreci ve kayıt ritmi

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: üç sürüm uzayı ayrımı, SemVer + 0.x pilot dönemi (1.0.0 kapıya bağlı), haftalık tren ve genişlet-daralt tren kuralı, iki katmanlı sürüm notu (algo değişiminde trend etkisi zorunluluğu), bayrak-imaj-ileri-düzeltme öncelikli geri alma politikası, tek sürüm işletim modeli. Faz 4 ve v1.0 doküman seti kapanışı. |
| 1.1 | 21.07.2026 | O-4 kapandı: dondurma pencereleri (kapı haftaları + yılbaşı). 0007 D-57. |
| 1.2 | 21.07.2026 | O-2 kapandı: tren günü Cuma → Pazartesi terfi. 0007 D-77. |
| 1.3 | 21.07.2026 | O-1 kapandı: 1.0.0 anı = ticari genel açılış (GA) — pilot çıkış kapısı sonrası. 0007 D-82. |
| 1.4 | 21.07.2026 | O-3 eylem planı eklendi: AN pilot öncesi sürüm notu şablonlarını hazırlayacak. 0007 D-91. |
