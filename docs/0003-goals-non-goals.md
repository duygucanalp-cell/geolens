# 0003 · Goals & Non-Goals

| Alan | Değer |
|---|---|
| Doküman ID | 0003 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 12 Temmuz 2026 |
| Karşıladığı madde | 4 · Hedefler, 5 · Hedef olmayanlar |
| İlişkili | 0001, 0002, 0004, 0205, 0206 |

---

## 1. Amaç ve Kapsam

Bu doküman ürünün neyi başarmayı hedeflediğini ve neyi bilinçli olarak yapmayacağını tek yerde sabitler. Hedefler sonuç odaklıdır; sayısal eşikler ve ölçüm yöntemi 0004'te (Success Metrics), MVP'ye giren özellik kesiti ise 0205'te tanımlanır. Hedef olmayanlar bölümü, 0001 Vision v0.1'deki "Out of Scope" listesinin gerekçelendirilmiş ve genişletilmiş halidir.

## 2. Hedef Çerçevesi

Her hedef üç kurala uyar: bir problem boşluğuna (P1, P2, P3) veya kurumsal gereksinime bağlanır; 0004'te en az bir metrikle ölçülebilir kılınır; ürün felsefesindeki altı ilkeyle (0001 §5) çelişmez. Ufuk ayrımı şöyledir: V1 hedefleri MVP ile doğrulanır, platform hedefleri MVP sonrası olgunlaşır (0206).

## 3. Hedefler (Goals)

| ID | Hedef | Bağ | Ufuk |
|---|---|---|---|
| G1 | Markaların AI yanıtlarındaki görünürlüğünü çok motorlu, tekrarlanabilir ve istatistiksel olarak güvenilir biçimde ölçmek. | P1, H2, H3 | V1 |
| G2 | Her skoru, onu üreten veriye kadar izlenebilir kılmak: calculation_run_id, faktör anlık görüntüsü ve şablon versiyonu ile tam denetlenebilirlik. | P1, İlke 1-2 | V1 |
| G3 | AI yanıtlarını etkileyen kaynakları görünür kılmak: alıntı analizi ve kaynak sınıflandırması. | P2, H4 | V1 |
| G4 | Görünürlüğü artıracak önceliklendirilmiş, veriye dayalı öneriler sunmak. | P3 | V1 |
| G5 | Rakip karşılaştırması ve kategori kıyası sağlamak: aynı prompt setinde markalar arası görünürlük farkı. | P1, P2 | V1 |
| G6 | Zaman serisi izleme ve anlamlı değişim uyarıları sağlamak: trend, kırılma, alarm. | P1 | V1 |
| G7 | Çok kiracılı, güvenli ve denetlenebilir kurumsal altyapı sunmak: tenant izolasyonu, RBAC, denetim izi. | Kurumsal (0310) | V1 |
| G8 | Pilot müşterilerle değer hipotezini doğrulamak: ürünün karar süreçlerinde düzenli kullanıldığını göstermek. | H5 | V1 |
| G9 | AI görünürlüğü ölçümünde kategori referansı olmak: metriklerin sektörde ortak dil haline gelmesi. | Vizyon | Platform |

Sayısal eşikler bilinçli olarak burada değil 0004'tedir; hedef metni stabil kalır, eşikler öğrendikçe revize edilir.

## 4. Hedef Olmayanlar (Non-Goals)

Aşağıdakiler kategori sınırıdır; ürünün hiçbir fazında kapsam değildir.

| ID | Hedef olmayan | Gerekçe |
|---|---|---|
| NG1 | İçerik yönetim sistemi (CMS) olmak | Mevcut CMS'lerle entegrasyon hedeflenir; ikamesi değil, üzerinde çalışan zekâ katmanıyız. |
| NG2 | Web sitesi barındırma | Altyapı hizmeti kategori dışıdır. |
| NG3 | Reklam (paid) yönetimi | Ücretli kanal ayrı disiplindir; organik AI görünürlüğüne odaklanılır. |
| NG4 | Sosyal medya planlama ve sosyal dinleme | Farklı yüzey, farklı kategori; mevcut araçlarla rekabet edilmez. |
| NG5 | Tam kapsamlı klasik SEO denetimi | SEO araçları tamamlanır, ikame edilmez; çakışan dar alanlar 0103 sonrası netleşir. |
| NG6 | Backlink satın alma veya link ağı yönetimi | Etik ve kalite riski; şeffaflık ilkesiyle çelişir. |
| NG7 | AI modeli eğitme veya fine-tuning hizmeti | Motorların içine müdahale değil, yüzeyinin ölçümü ve yorumu yapılır. |
| NG8 | Görünürlük veya sıralama garantisi vermek | AI motor çıktıları deterministik değildir; garanti taahhüdü teknik olarak temelsiz, hukuki olarak risklidir. Ürün olasılıksal ölçüm ve iyileştirme sunar, garanti değil. |
| NG9 | Motor kullanım şartlarını ihlal eden veri toplama | Resmî API'ler ve izinli yöntemler esastır (0000 R-04, 0308). Yetkisiz scraping ürünün sürdürülebilirliğini ve müşterilerini riske atar. |
| NG10 | Manipülatif (black-hat) GEO teknikleri önermek | Açıklanabilirlik ve şeffaflık ilkeleriyle çelişir; motor politikalarına aykırı taktikler öneri motoruna giremez. |

## 5. V1 Dışı, Yol Haritası Adayları

Aşağıdakiler kategori sınırı değildir; bilinçli olarak V1 kapsamı dışında tutulur ve 0206'da değerlendirilir.

| Aday | Neden V1 dışı |
|---|---|
| AI uyumlu içerik üretimi (generation) | V1 öneri sunar, içerik üretmez; üretim kalitesi ve sorumluluğu ayrı bir problem alanıdır. |
| Otomatik iyileştirme (auto-fix) | Önce ölçüm ve öneri döngüsünün güveni kurulmalı; otomasyon bunun üzerine gelir. |
| Tahmine dayalı görünürlük öngörüsü | Anlamlı tahmin için yeterli tarihsel seri birikmeli (G6 çıktısı). |
| Genel amaçlı marka itibar süiti | Odak korunur; AI yüzeyi dışına genişleme ancak kategori liderliği sonrası düşünülür. |

## 6. İzlenebilirlik: Hedef, Problem, Madde

| Problem boşluğu | Hedefler | Metrik kaynağı | Kapsam kaynağı |
|---|---|---|---|
| P1 Ölçüm | G1, G2, G5, G6 | 0004 | 0205 |
| P2 Atıf | G3, G5 | 0004 | 0205 |
| P3 Aksiyon | G4 | 0004 | 0205 |
| Kurumsal gereksinim | G7 | 0004, 0310 | 0205 |
| Değer doğrulama | G8 | 0004 | 0201, 0205 |

Madde izlenebilirliği: bu doküman 25 maddelik listenin 4 ve 5 numaralı maddelerini karşılar (0000 §5).

## 7. Değişiklik Yönetimi ve Açık Sorular

Hedef ekleme, çıkarma veya ufuk değişikliği 0007'de tanımlanan karar süreciyle yapılır; her değişiklik bu dokümanın changelog'una ve 0000 izlenebilirlik matrisine işlenir. Yanlışlanan bir hipotez (0002 §7) ilgili hedefin gözden geçirilmesini tetikler.

| ID | Soru | Not |
|---|---|---|
| O-1 | G8 pilot doğrulaması için hedef müşteri profili ve sayısı | 0201 persona kararıyla birlikte 0004'te eşiklenecek. |
| O-2 | MVP motor kapsamı: hangi AI motorları V1'e girer? | 0102 bulguları ve 0308 maliyet analiziyle 0205'te kesinleşecek. |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: G1–G9 hedefleri, NG1–NG10 gerekçeli hedef olmayanlar, V1 dışı yol haritası adayları, problem-hedef izlenebilirliği. Vision v0.1 Out of Scope listesi buraya taşındı ve genişletildi (NG8–NG10 eklendi). |
