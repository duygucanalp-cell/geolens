# 0002 · Problem Statement

| Alan | Değer |
|---|---|
| Doküman ID | 0002 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 12 Temmuz 2026 |
| Karşıladığı madde | 3 · Problem dokümanı |
| İlişkili | 0000, 0001, 0101–0105, 0201, 0309 |

---

## 1. Amaç ve Kapsam

Bu doküman, AVIP'in çözmeyi hedeflediği problemi tanımlar: problemin kendisini, kimleri nasıl etkilediğini, mevcut çözümlerin neden yetersiz kaldığını ve doğrulanması gereken hipotezleri. Metodoloji gereği bu doküman çözüm tasarımı içermez; çözüm 0204 (PRD) ve mimari dokümanlarda tanımlanır. Pazar kanıtları ve sayısal veriler Faz 1 dokümanlarında (0101–0105) toplanır; bu dokümanda doğrulanmamış istatistik kullanılmaz.

## 2. Problem Bildirimi

> Kurumlar, satın alma ve tercih kararlarını giderek daha fazla şekillendiren AI yanıt motorlarında nasıl temsil edildiklerini ölçemiyor, bu temsilin nedenlerini anlayamıyor ve onu iyileştirmek için sistematik bir araçtan yoksun.

Problem üç alt boşluktan oluşur:

| Kod | Boşluk | Tanım |
|---|---|---|
| P1 | Ölçüm boşluğu | AI yanıtlarındaki marka varlığının (mention, öneri, alıntı, bağlam) standart bir metriği ve sürekli izleme aracı yok. LLM yanıtları olasılıksaldır; tek bir sorgunun anlık görüntüsü yanıltıcıdır. |
| P2 | Atıf boşluğu | AI yanıtlarını hangi kaynakların ve hangi içeriğin etkilediği bilinmiyor. Geleneksel analitik, AI kaynaklı etkiyi ve trafiği ayrıştıramıyor. |
| P3 | Aksiyon boşluğu | Görünürlüğü artırmak için hangi içerik veya teknik değişikliğin işe yaradığına dair kapalı bir geri besleme döngüsü yok; ekipler körlemesine deniyor. |

## 3. Bağlam: Davranış Değişimi

Kullanıcı davranışı, sorulara yanıt aramanın birincil yüzeyini değiştiriyor: geleneksel arama sonuç sayfasının bağlantı listesi yerine, AI motorlarının tek sentezlenmiş yanıtı. Bu yüzeyde kullanıcı çoğu zaman kaynak listesi görmez; motorun önerdiği veya alıntıladığı az sayıda marka, kararın tamamını şekillendirir.

Bu değişimin iki yapısal sonucu vardır. Birincisi, görünürlük ikili hale gelir: yanıtın içinde olan kazanır, olmayan hiç var olmamış gibidir. İkincisi, görünürlüğün mekanizması değişir: sıralama sinyalleri yerine modelin eğitim verisi, gerçek zamanlı arama katmanı ve alıntı seçim davranışı belirleyicidir. Bu mekanizmalar için kurumların elinde ne görünürlük panosu ne de müdahale kılavuzu vardır. Değişimin hızı ve ölçeğine dair sayısal kanıt 0102'de derlenecektir.

## 4. Etkilenen Aktörler ve Acı Noktaları

| Aktör | Acı noktası | Bugünkü geçici çözüm |
|---|---|---|
| Pazarlama lideri (CMO) | Marka bütçesinin etkisini AI kanalında gösteremiyor; yönetim kuruluna "AI bizi öneriyor mu?" sorusuna yanıt veremiyor. | Yok; anekdot ve tek seferlik denemeler. |
| SEO ve içerik ekibi | SEO metrikleri iyileşirken AI yanıtlarındaki varlık bilinmiyor; hangi içeriğin AI tarafından alıntılandığı görülemiyor. | Elle prompt deneme; tekrarlanabilir değil. |
| Marka ve PR ekibi | AI motorları marka hakkında eski, eksik veya yanlış bilgi veriyor olabilir; fark edilmesi tesadüfe kalmış. | Şikâyet gelince manuel kontrol. |
| Ajanslar ve danışmanlar | Müşteriye AI görünürlüğü hizmeti satmak istiyor; ölçüm ve raporlama altyapısı yok. | Elle derlenen ekran görüntüsü raporları. |
| Ürün ve büyüme ekibi | AI kaynaklı trafik ve dönüşüm ayrıştırılamıyor; kanal yatırım kararı veriye dayanmıyor. | Referrer bazlı kısmi tahmin. |
| Üst yönetim | Rakibin AI yanıtlarında öne geçmesi sessiz bir pazar kaybı; görünür olana kadar geç kalınıyor. | Yok. |

## 5. Mevcut Çözümlerin Yetersizliği

| Mevcut yaklaşım | Neden yetersiz | Not |
|---|---|---|
| Geleneksel SEO platformları | SERP'i ölçer, AI yanıtının içini ölçmez; sıralama sinyalleri AI alıntı davranışıyla birebir örtüşmez. | AI modülleri eklemeye başlayanlar 0103'te incelenecek. |
| Manuel prompt kontrolü | Ölçeklenmez; örnekleme yok, tarihsel seri yok, motorlar arası kıyas yok, tekrarlanabilirlik yok. | Bugünün en yaygın pratiği. |
| Web analitiği | Yalnızca tıklama ile gelen AI trafiğini kısmen görür; yanıt içi görünürlüğü ve tıklamasız etkiyi hiç görmez. | P2'nin yalnızca küçük bir dilimi. |
| Sosyal dinleme araçları | Sosyal platformları tarar; AI yanıt yüzeyini taramaz. | Kategori farkı. |
| Yeni nokta çözümler (GEO araçları) | Parçalı kapsam, standartlaşmamış metrikler, sınırlı motor kapsaması ve açıklanabilirlik eksikleri gözleniyor. | Sistematik değerlendirme 0103'te. |

## 6. Eylemsizliğin Sonuçları

1. Sessiz pazar kaybı: AI önerilerinde yer almamak fark edilmeden müşteri kaybettirir; sinyal, gelir düşüşü olarak geç gelir.
2. Yanlış yatırım: yalnızca klasik SEO'ya yapılan yatırım, AI görünürlüğüne otomatik olarak çevrilmez; bütçe yanlış hedefe akabilir.
3. İtibar riski: AI motorları kurum hakkında eski veya hatalı bilgi üretebilir; ölçüm yoksa düzeltme talebi de yoktur.
4. Rekabet körlüğü: rakiplerin AI yüzeyindeki konumu izlenemediği için kıyas ve tepki mümkün olmaz.

## 7. Problem Hipotezleri

Aşağıdaki hipotezler yanlışlanabilir biçimde kurulmuştur; Faz 1 ve erken pilot ölçümlerle test edilecektir.

| ID | Hipotez |
|---|---|
| H1 | Hedef segmentteki pazarlama ekipleri AI yanıtlarındaki görünürlüklerini düzenli olarak ölçmüyor; ölçenler manuel ve tekrarlanamaz yöntemler kullanıyor. |
| H2 | Aynı prompt için marka görünürlüğü AI motorları arasında anlamlı farklılık gösteriyor; tek motor izlemek yeterli değil. |
| H3 | AI yanıtları örneklemler ve zaman içinde değişkenlik gösteriyor; tek sorguya dayalı ölçüm güvenilir değil, istatistiksel örnekleme gerekli. |
| H4 | AI yanıtlarında alıntılanan kaynaklar tespit edilebilir örüntüler izliyor ve içerik stratejisiyle etkilenebilir durumda. |
| H5 | Problem, hedef segment tarafından kaynak ayrılacak kadar acil algılanıyor; kategoriye yönelik talep sinyalleri mevcut. |

## 8. Doğrulama Yaklaşımı

| ID | Yöntem | Kaynak doküman | Durum |
|---|---|---|---|
| H1 | Persona görüşmeleri ve mevcut pratik taraması | 0201, 0103 | Beklemede |
| H2 | Çok motorlu pilot ölçüm (aynı prompt seti, karşılaştırmalı) | 0102, 0309 pilotu | Beklemede |
| H3 | Örnekleme pilotu: n tekrar, zaman serisi, varyans analizi | 0309 pilotu | Beklemede |
| H4 | Alıntı kaynak analizi: yanıtlardaki kaynakların sınıflandırması | 0101 | Beklemede |
| H5 | Kategori talep sinyalleri: rakip pazarın varlığı ve büyümesi, görüşme bulguları | 0103, 0105, 0201 | Beklemede |

Hipotez sonuçları bu dokümanın sonraki versiyonuna işlenir; yanlışlanan hipotezler ürün kapsamını (0205) doğrudan etkiler.

## 9. Kapsam Dışı ve Açık Sorular

Kapsam dışı: çözüm tasarımı (0204), pazar boyutlandırma ve fırsat analizi (0105), rakip değerlendirmesi (0103).

| ID | Soru | Not |
|---|---|---|
| O-1 | Hedef segment önceliği: kurumsal (enterprise) mı, KOBİ mi, ajanslar mı? | Turkcell bağlamı kurumsala işaret ediyor; karar 0201'de personalarla netleşecek. |
| O-2 | Coğrafi odak: ilk fazda TR öncelikli mi, küresel mi? | 0105 fırsat analizinde karara bağlanacak. |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: problem bildirimi (P1–P3), aktör ve acı noktası haritası, mevcut çözüm analizi, H1–H5 hipotezleri ve doğrulama planı. |
