# 0007 · Governance & Ways of Working

| Alan | Değer |
|---|---|
| Doküman ID | 0007 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 12 Temmuz 2026 |
| Karşıladığı madde | 11 · Ürün üretim, yönetim, yönetişim |
| İlişkili | 0000, 0003, 0004, 0005; tüm ADR'ler |

---

## 1. Amaç ve Kapsam

Bu doküman, ürünün nasıl üretildiğini ve yönetildiğini tanımlar: roller ve sorumluluk dağılımı, karar alma yolları, doküman yaşam döngüsü, mimari karar kaydı pratiği, çalışma kadansları ve faz geçiş kapıları. Kişi adları ve kadro planlaması kapsam dışıdır; tanımlar rol bazlıdır.

## 2. Roller ve RACI

Beş rol tanımlıdır. Bir kişi birden fazla rolü üstlenebilir; roller kişiye değil sorumluluğa bağlıdır.

| Rol | Sorumluluk alanı |
|---|---|
| **Ürün Sahibi (PO)** | Kapsam, öncelik, hedef ve eşik onayları; faz kapısı kararı; nihai onay mercii. |
| **Teknik Lider (TL)** | Mimari bütünlük, ADR sahipliği, teknik kalite standartları, sürüm onayı. |
| **Analist (AN)** | Doküman üretimi, izlenebilirlik, araştırma yürütme, açık soru takibi. |
| **Geliştirici (DEV)** | Uygulama, test, teknik dokümantasyon katkısı. |
| **Paydaş (PY)** | Ortaklık bağlamındaki gözden geçirmeler ve dış bağımlılıklar (hukuk, marka vekili dahil). |

| Aktivite | PO | TL | AN | DEV | PY |
|---|---|---|---|---|---|
| Doküman üretimi | A | C | R | I | I |
| Doküman onayı (Approved) | A | C | R | I | C |
| Kapsam değişikliği (0003, 0205) | A | C | R | I | C |
| Mimari karar (ADR) | A | R | C | C | I |
| Metrik eşik değişikliği (0004) | A | C | R | I | I |
| Sürüm çıkışı | C | A | I | R | I |
| İsim ve marka kararı (0006) | A | I | R | I | C |

R: yürütür, A: hesap verir (tek kişi), C: danışılır, I: bilgilendirilir.

## 3. Karar Süreci ve Karar Kaydı

Kararlar iki tipe ayrılır. Tip 1 (geri döndürülmesi pahalı: kapsam, mimari, isim, sert kural): yazılı öneri hazırlanır, iki iş günlük itiraz penceresi işletilir, A rolü onaylar, karar kaydı düşülür. Tip 2 (geri döndürülebilir: günlük uygulama kararları): R rolü karar verir ve kaydeder; onay beklemez. Anlaşmazlıkta ilke "karşı çık ve bağlan"dır (disagree and commit); çözülmeyen anlaşmazlık PO'ya taşınır.

Karar kaydının yeri karar tipine göre sabittir: ürün kararları ilgili dokümanın changelog'una, mimari kararlar ADR'ye, bekleyen konular açık soru (O-x) tablolarına işlenir. Örnek kayıt:

| ID | Karar | Tarih / Onay | Gerekçe |
|---|---|---|---|
| D-01 | Repo doküman dili: Türkçe | 12.07.2026 · PO | Ekip varsayılanı; yönetici çıktılarında dil ihtiyaca göre belirlenir. İngilizce öneriye itiraz gelmedi, TR ile devam edildi. |
| D-02 | Frontend platformu | Bekliyor | 0103 bulguları sonrası ADR-002 ile karara bağlanacak. |

## 4. Doküman Yaşam Döngüsü

Durum akışı: Draft, Review, Approved. Approved'a geçiş PO onayıyla olur ve künyeye işlenir. Versiyonlama: 0.x taslak evresi, 1.0 ilk onaylı yayın; içerik değişikliği minor (1.1), yapı veya karar değişikliği major (2.0) artırır. Her versiyonda changelog kaydı zorunludur.

Çalışma sözleşmesi (0000 §6 ile birlikte): mesaj başına tek doküman teslim edilir; revizyon talepleri biriktirilir ve tek geçişte topluca uygulanır; her doküman karşıladığı maddeyi künyesinde belirtir. Review mekanizması GitHub PR üzerinden yürütülür: doküman PR olarak açılır, C rollerinin yorumu PR'da toplanır, PO onayı merge ile Approved'a çevirir.

## 5. ADR Pratiği

Mimari kararlar docs/03-architecture/adr/ altında ADR-001'den başlayarak numaralanır. Zorunlu ADR tetikleyicileri: teknoloji seçimi, veri modeli kırılımları, motor entegrasyon yöntemi, kimlik ve yetkilendirme tasarımı, kuyruk ve zamanlama altyapısı. ADR formatı beş bölümdür: Bağlam, Karar, Alternatifler, Sonuçlar, Durum (Önerildi, Kabul, Reddedildi, Yerini aldı). Kabul edilen ADR değiştirilmez; yeni karar eskisinin yerini alır ve çapraz referanslanır. Rezerve numaralar: ADR-001 teknoloji yığını (0304 ile), ADR-002 frontend platformu (D-02).

## 6. Kadanslar ve Faz Kapıları

| Ritüel | Sıklık | İçerik |
|---|---|---|
| Ürün senkronu | Haftalık | İlerleme, engeller, açık soru durumu; 30 dakikayı aşmaz. |
| Metrik gözden geçirme | Pilotta 2 haftada bir, sonra aylık | 0004 §7 kalibrasyon ritmi; [K] eşiklerinin kontrolü. |
| Research tazeleme | 3 ayda bir | 0101–0105 dokümanlarının güncellik kontrolü (0000 R-05); pazar hızla değişiyor. |
| Faz kapısı | Faz sonunda | Kapanış kriterleri kontrolü ve sonraki faza geçiş kararı (PO). |

### Faz kapısı kriterleri

1. Fazın tüm dokümanları Approved durumda.
2. Açık soruların her biri bir sahibe ve hedef dokümana atanmış.
3. Sonraki fazın giriş bağımlılıkları hazır (örnek: Faz 1 için araştırma soruları netleşmiş).

## 7. Değişiklik ve Risk Yönetimi

Risk kaydı 0000 §8'de yaşar; yeni risk AN tarafından eklenir, PO önceliklendirir. Kapsam değişikliği akışı: etki analizi (hangi dokümanlar etkilenir), ilgili doküman revizyonları, 0000 izlenebilirlik matrisinin güncellenmesi. Yanlışlanan hipotez (0002 §7) otomatik değişiklik tetikleyicisidir. Guardrail ihlali (0004 §5) ilgili çalışmayı duraklatır ve PO gündemine taşınır.

## 8. Faz 0 Kapanış Durumu

Bu doküman Faz 0'ın son teslimatıdır. Kapanış tablosu:

| ID | Doküman | Versiyon | Durum |
|---|---|---|---|
| 0000 | Master Plan | 1.0 | Onaylandı |
| 0001 | Vision | 0.2 | Onaylandı |
| 0002 | Problem Statement | 1.0 | Onaylandı |
| 0003 | Goals & Non-Goals | 1.0 | Onaylandı |
| 0004 | Success Metrics | 1.0 | Onaylandı |
| 0005 | Glossary | 1.0 | Onaylandı |
| 0006 | Brand & Domain | 1.0 | Onaylandı; isim doğrulama protokolü yürütülüyor |
| 0007 | Governance | 1.0 | Bu doküman; onayla kapanır |

Onaylar 12.07.2026 tarihli teslimat onaylarıdır; künyelerdeki Review ibareleri ilk toplu revizyonda Approved olarak güncellenecektir (tek geçiş kuralı).

### Konsolide bekleyen kararlar

| Kaynak | Konu | Sahip | Karar yeri |
|---|---|---|---|
| 0006 O-3 | İsim finalistleri (öneri: Mentiq, Vizora, Visanta) ve doğrulama turu | PO | 0006 v1.1 |
| 0006 O-1, O-2 | Marka sahipliği ve tescil kapsamı | PO + PY | 0006 v1.1 |
| D-02 | Frontend platformu | TL | ADR-002 (0103 sonrası) |
| 0002 O-1 | Hedef segment önceliği | PO | 0201 |
| 0003 O-2 | MVP motor kapsamı | PO + TL | 0205 |
| 0004 O-2 | Örnekleme büyüklüğü n | TL | 0309 pilotu |

## 9. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | PY rolünün ortaklık tarafındaki muhatap tanımı | Ortaklık çerçevesi netleştiğinde rol eşlemesi yapılacak; kişi adı dokümana girmez. |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: roller ve RACI, iki tipli karar süreci, D-01 karar kaydı, doküman yaşam döngüsü, ADR pratiği, kadanslar, faz kapısı kriterleri, Faz 0 kapanış tablosu ve konsolide bekleyen kararlar. |
