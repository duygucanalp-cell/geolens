# 0205 · MVP Scope

| Alan | Değer |
|---|---|
| Doküman ID | 0205 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.4 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 18 Temmuz 2026 |
| Karşıladığı madde | 16 · MVP Scope |
| İlişkili | 0204 (girdi), 0103 §7, 0004 (M2, K1), 0201 §7; 0206, 0301-0311; D-03 |

---

## 1. Amaç ve Kapsam

Bu doküman V1 MVP sınırını çizer: 0204'ün Çekirdek gereksinim setini masa bahisleri (0103 §7), panel maliyet korumaları (K1) ve motor kapsam kararıyla (D-03) kesiştirir; içeride kalanları, daraltılmış biçimde girenleri ve bilinçli açıkları gerekçeleriyle kaydeder. Sayım düzeltmesi: 0204 Çekirdek seti 31 işlevsel ve 15 işlevsel olmayan gereksinimden oluşur (0204 v1.1'de düzeltildi; FR-F7 eklendi). Kapsam dışı: sürüm takvimi ve efor tahmini (planlama Faz 4 süreçleriyle), hızlı takip pencerelerinin detaylandırılması (0206).

## 2. Kesit Yöntemi ve MVP Tanımı

MVP tanımı: M2 pilotunu (0004) çalıştırabilecek, sert kuralların (M6, M7, M12, M14) tamamını ilk günden sağlayan, davetli kiracılarla gerçek ölçüm ve raporlama döngüsünü uçtan uca döndüren üründür. Kesit dört kuralla verilir: (1) Çekirdek gereksinimler varsayılan olarak içeridedir; dışarı alma yazılı gerekçe ve changelog kaydı ister (0203 karar kuralı). (2) Daraltılmış giriş meşrudur: gereksinim MVP'de vardır ancak kapsamı bilinçli olarak küçültülmüştür; genişleme hızlı takibe yazılır. (3) Masa bahisleriyle çelişen her açık, karşı önlem veya pencere ile birlikte kaydedilir (§5-6). (4) Kesit, K1 korumaları ve D-03 kararına duyarlıdır; D-03 verilmeden motor bağdaştırıcı sıralaması kesinleşmez.

> **Kesit özeti:** 31 Çekirdek FR'nin 26'sı tam, 5'i daraltılmış biçimde MVP'dedir; sıfır Çekirdek gereksinim dışarıda bırakılmıştır. 15 Çekirdek NFR'nin tamamı tam kapsamla içeridedir; güven ve izolasyon nitelikleri daraltmaya konu edilemez. Sekiz Genişletilmiş gereksinim ve iki masa bahisi kalemi bilinçli açık olarak pencerelere tarihlenmiştir. Ticari açılış önerisi: self-serve kayıt teknik olarak MVP'de açıktır; genel (public) açılış pilot çıkış kapısından sonra yapılır, pilot döneminde paket atamaları arka ofisten yönetilir (O-2 kararına öneri; PO).

## 3. Motor ve Ölçüm Kapsamı

| Boyut | MVP kararı | Dayanak |
|---|---|---|
| Çekirdek motorlar | ChatGPT (resmî vekil: Responses web araması), Gemini (grounding vekili), Perplexity (doğrudan Sonar) | 0102 §7 önerisi; TR kullanım ağırlığı |
| Google yüzeyi | ✅ **D-03 kararlaştırıldı:** Gemini grounding vekili, official_proxy etiketiyle MVP'de (b); lisanslı üçüncü taraf (c) PY hukuki incelemesi paralel başladı | D-03 kararı verildi (21.07.2026); §9 O-1 kapandı |
| İkinci halka | Claude ve Grok MVP dışı; hızlı takip 1 adayı | 0102 §7; panel maliyeti |
| Fidelite etiketi | Üç kademe etiketi tüm skorlarda; kapsam etikete yansır (FR-B5, FR-C5) | İ2; 0102 §4 |
| Ölçüm frekansı | Taban haftalık; pilot kiracıları ve üst paket günlük (nitel kademe) | Masa bahisi ↔ K1 dengesi |
| Panel ve örnekleme | Kiracı başına prompt kotası paket hakkıyla; örnekleme tekrarı ve pencere 0309'da kalibre [K] | K1; FR-C3 |
| Maliyet korumaları | Kota, hız sınırı ve bütçe tavanı MVP'de zorunlu (NFR-N14, FR-H2) | K1 sert koruması |

## 4. Kapsam Kesiti: İçeride ve Daraltılmış

Tam kapsamla içeride (25 FR): FR-A1, A2, A3, A5; FR-B1, B2, B3, B5; FR-C1 - C7; FR-D1, D2, D4; FR-E2, E3; FR-F1, F3, F4; FR-G1; FR-H2. Tüm Çekirdek NFR'ler (N1 - N12, N14 - N16) tam kapsamla içeridedir ve daraltılamaz statüdedir.

| ID | Daraltılmış giriş | MVP biçimi ve genişleme notu |
|---|---|---|
| FR-B4 | Site erişim denetimi | Çekirdek bulgu kataloğu (bot izinleri, SSR, temel erişilebilirlik); katalog genişlemesi hızlı takip 1. |
| FR-D3 | Rakip kıyası | Tanımlı rakip setiyle temel kıyas görünümü; segment ve konu bazlı derin kıyas hızlı takip 1. |
| FR-E1 | Öneri üretimi | Kural tabanlı başlangıç kütüphanesi + kanıt derecesi etiketi; kapsam genişlemesi öneri-etki verisiyle (FR-E4 sonrası) büyür. |
| FR-F2 | Uyarı ayarları | Varsayılan eşikler + kanal seçimi; eşik ince ayarı ve kural editörü hızlı takip 1. |
| FR-G2 | Çok müşteri panoraması | Liste temelli panorama (müşteri, skor, değişim, uyarı sayısı); karşılaştırmalı grafik panoraması hızlı takip 1. |

## 5. Bilinçli Açıklar ve Hızlı Takip

| Açık | Masa bahisi mi | Gerekçe / karşı önlem | Pencere |
|---|---|---|---|
| BI / API entegrasyonu (FR-F6) | Evet | Sözleşme tasarımı ADR ister (0204 O-3); karşı önlem: temel CSV dışa aktarımı MVP'ye alınır (§6, 0204 v1.1 önerisi) | Hızlı takip 1 |
| Zamanlanmış rapor dağıtımı (FR-F5) | Kısmi | Manuel üretim (FR-F4) MVP'de; zamanlama katmanı 0307 üzerinde küçük ek | Hızlı takip 1 |
| Öneri-etki takibi (FR-E4) | Hayır | M4 işaretleri MVP'den birikir; karşılaştırma görünümü veriyle anlamlanır | Hızlı takip 1 |
| Müşteri arşivleme/devir (FR-G3) | Hayır | Pilot ölçeğinde ihtiyaç düşük; ajans büyüyünce kritik | Hızlı takip 1 |
| Benchmark bağlamı (FR-D5, NFR-N13) | Hayır | Gizlilik yöntemi kararı (0204 O-2) + yeterli kiracı tabanı ister | Hızlı takip 2 |
| Self-serve ödeme (FR-A6) | Hayır | Pilot döneminde arka ofis ataması; ticari açılışla birlikte | Hızlı takip 2 |
| Denetim izi görünümü (FR-H1) | Hayır | Kayıt (NFR-N6) MVP'de tam; görünüm/dışa aktarım kurumsal pencereyle | Hızlı takip 2 |
| SSO/SAML (FR-A4) | Kurumsal | P1 aktif satışı ertelendi (0201 §7); pilot P2/P3 odaklı | Kurumsal kapı |
| SOC 2 sertifikasyonu | Evet (kurumsal) | Kontrol yolu 0310 ile MVP'den başlar; sertifika süreci kurumsal kapıya bağlanır (W5 planı) | Kurumsal kapı |
| 12+ ay tarihçe gösterimi | Evet | Birikim NFR-N11 ile ilk günden tam; gösterim penceresi zamanla doğal dolar, veri silinmez | Yapısal (birikiyor) |

## 6. Kesit Riskleri ve Karşı Önlemler

| Risk | Etki | Karşı önlem |
|---|---|---|
| BI eksikliği ajans satışını zorlar | P3 değer önerisi zayıflar (masa bahisi açığı) | CSV dışa aktarımı 0204 v1.1 ile FR-F7 olarak eklendi (Çekirdek; tam kapsam); white-label PDF zaten tam |
| Haftalık taban frekans "günlük izleme" beklentisinin altında kalır | Rakip kıyasında algı açığı | Pilot ve üst pakette günlük; frekans paket hakkı olarak şeffaf iletilir; K1 gerçekleşme verisiyle taban gözden geçirilir |
| D-03 gecikirse bağdaştırıcı sırası belirsiz kalır | Faz 3 kritik yolu (0308) bloklanır | O-1 bloklayıcı işaretlendi; karar 0205 onayıyla birlikte istenir |
| Kural tabanlı öneri kütüphanesi (FR-E1 daraltılmış) sığ algılanır | G4 değer algısı düşer | Kanıt derecesi etiketi dürüstlüğü korur; kütüphane TR-öncelikli sektör şablonlarıyla derinleştirilir; M4 verisiyle genişletme önceliklenir |
| Pilot kapısı gecikirse ticari açılış kayar | TR penceresi (0105) daralır | Çıkış kriterleri nicel ve önceden tanımlı (§7); kapı incelemesi 0007 kadansına bağlanır |

## 7. Pilot Tanımı ve Çıkış Kapısı

Pilot, M2 çerçevesinde (0004) davetli kiracılarla yürütülür; profil 0201 §7 önerisiyle uyumlu olarak P3 (ajans) ve P2 (KOBİ) ağırlıklıdır, P4 self-serve akışını test edecek sınırlı davet eklenir. Pilotun iki işlevi vardır: değer hipotezlerinin erken sinyali (M1, M3, M4) ve [K] işaretli eşiklerin kalibrasyonu (M10, M11, N10, örnekleme parametreleri).

| # | Genel açılış (çıkış kapısı) kriterleri |
|---|---|
| 1 | Sert kural ihlali sıfır: M6, M7, M12, M14 pilot boyunca istisnasız sağlandı. |
| 2 | Kalibre edilen M10 ve M11 hedefleri ardışık son iki pilot haftasında karşılandı. |
| 3 | P2 ve P3 persona kartları saha verisiyle doğrulandı; 0201 §9 kapısı kapandı ve 0204 Approved durumuna geçti. |
| 4 | K1 maliyet gerçekleşmesi panel modeli öngörüsüyle uyumlu; kota ve tavan mekanizmaları devrede doğrulandı. |
| 5 | D-03 kararı verildi ve motor kapsamı üretimde karara uygun çalışıyor. |
| 6 | Pilot kiracılarından en az bir P3 ve bir P2 referans-adayı vaka sinyali alındı (M3/M4 destekli). |
| 7 | Güvenlik kapanışı: 0405 pilot öncesi tam turu tamamlandı, açık kritik ve yüksek bulgu sıfır. Sızma testi Dilim 4 (Sertleştirme) kapsamında yapılır (0310 D-47, 0405 §6). |

## 8. AVIP için Çıkarımlar

1. Faz 3 kritik yolu netleşti: 0303 (veri modeli; N1/N6/N7 temel katman), 0307 (kuyruk ve zamanlayıcı), 0308 (üç çekirdek motor bağdaştırıcısı; D-03'e duyarlı), 0309 (örnekleme ve anlamlılık; [K] kalibrasyon çerçevesi).
2. 0204 v1.1 değişiklikleri uygulandı: FR-F7 (temel CSV dışa aktarımı, Çekirdek) eklendi ve çıkarım 1 sayım düzeltmesi (uygulandı; sonuç 31/15).
3. 0206 hızlı takip pencerelerinin tohum listesi §5 tablosudur; sıralama M4 ve pilot geri bildirimiyle yeniden önceliklenir.
4. Frekans kademesi paket hakkı olarak 0201 §6 iskeletine işlenecek (Business/Enterprise günlük; Free/Pro haftalık taban; nitel).
5. Pilot çıkış kapısı 0007 karar sürecine bağlandı; kapı incelemesi Tip 1 karardır ve kayıt altına alınır.

## 9. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~D-03 Google yüzeyi kararı (bloklayıcı)~~ | ~~PO + TL; 0205 onayıyla birlikte istenir; 0308 sıralamasını açar.~~ |
| ✅ O-1 | D-03 Google yüzeyi kararı | **KAPANDI** (21.07.2026): (b) kabul — Gemini grounding vekili, official_proxy etiketiyle; (c) PY incelemesi başladı. 0102 v1.1, 0308 §2, 0007 karar kaydına işlendi. |
| ✅ O-2 | FR-F7 (CSV dışa aktarımı) önerisinin 0204 v1.1'e kabulü | **KAPANDI** (v1.1): FR-F7 0204'e eklendi. |
| ~~O-3~~ | ~~Pilot kiracı sayısı ve davet karması (P3/P2/P4 oranı)~~ | ~~M2 çerçevesinde PO + AN; görüşme havuzuyla (0201 O-3) birlikte planlanır.~~ |
| ✅ O-3 | Pilot kiracı sayısı ve davet karması (P3/P2/P4 oranı) | **KAPANDI** (21.07.2026): 6-8 kiracı — 3 ajans (P3) + 2-3 KOBİ (P2) + 1-2 self-serve (P4). Pilot 8 hafta. AN görüşme havuzuyla planlayacak. |
| ~~O-4~~ | ~~Frekans kademelerinin paket haklarına kesin eşlemesi~~ | ~~Nitel; 0201 §6 iskeletiyle PO onayı.~~ |
| ✅ O-4 | Frekans kademelerinin paket haklarına kesin eşlemesi | **KAPANDI** (21.07.2026): Free/Pro haftalık, Business/Enterprise günlük. 0205 §3'e işlendi. |

---

## Kaynaklar

- 0204 PRD · Çekirdek/Genişletilmiş etiketleri, ürün ilkeleri, izlenebilirlik matrisi
- 0103 Competitor Analysis §7 · masa bahisleri listesi (§5 karşılaştırma tabanı)
- 0004 Success Metrics · M2 pilot çerçevesi, K1/K3 korumaları, sert kurallar
- 0102 AI Search Landscape · motor kademe modeli, çekirdek motor önerisi, D-03 çerçevesi
- 0201 §7 · segment önceliği önerisi (pilot profili dayanağı)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: kesit yöntemi, motor/ölçüm kapsamı, 25 tam + 5 daraltılmış FR kesiti (sıfır Çekirdek dışarıda), 10 kalemlik bilinçli açık tablosu, 5 kesit riski, 6 kriterli pilot çıkış kapısı; 0204 sayım düzeltme notu (30/15) ve FR-F7 önerisi. |
| 1.1 | 18.07.2026 | FR-F7 kabulüyle kesit güncellendi (31 Çekirdek FR: 26 tam + 5 daraltılmış); O-2 kapandı; pilot çıkış kapısına 7. kriter eklendi (güvenlik kapanışı; 0405 §6 + 0310 O-4; PO onayı bu revizyon onayıyla). |
| 1.2 | 21.07.2026 | D-03 karara bağlandı: (b) Gemini grounding vekili; O-1 bloklayıcı açık soru kapandı; Google yüzeyi satırı güncellendi; Faz 3 kritik yolundaki son blokaj kalktı. |
| 1.3 | 21.07.2026 | O-3 pilot kiracı profili karara bağlandı: 6-8 kiracı (3 P3 + 2-3 P2 + 1-2 P4). |
| 1.4 | 21.07.2026 | O-4 frekans kademeleri karara bağlandı: Free/Pro haftalık, Business/Enterprise günlük. |
