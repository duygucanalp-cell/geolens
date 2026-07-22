# 0000 · Master Plan

| Alan | Değer |
|---|---|
| Doküman ID | 0000 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.2 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| Karşıladığı madde | 0 · Kök doküman; tüm maddelerin atası |
| İlişkili | 0001–0007, 0101–0105, 0201–0206, 0301–0311, 0401–0406 |

---

## 1. Amaç ve Kapsam

Bu doküman AVIP projesinin kök dokümanıdır: 25 maddelik gereksinim izlenebilirlik matrisini, doküman tanımını tamamlama ölçütlerini (DoD), risk kaydını ve Sprint 0 başlangıç planını tek çatı altında toplar. Tüm faz dokümanları bu belgeye referansla bağlanır. Değişiklikler bu dokümanın changelog'una ve ilgili izlenebilirlik satırlarına işlenir.

## 2. Proje Kapsamı

AVIP, kurumların AI yanıt motorlarında (ChatGPT, Gemini, Perplexity ve benzeri) nasıl temsil edildiklerini ölçmelerini, anlamalarını ve iyileştirmelerini sağlayan bir AI Görünürlük Platformudur. Proje beş fazdan oluşur:

| Faz | Kapsam | Dokümanlar |
|---|---|---|
| Faz 0 · Foundation | Vizyon, problem, hedef, metrik, sözlük, marka, yönetişim | 0000–0007 |
| Faz 1 · Research | Pazar araştırması, rakip analizi, SWOT, fırsat | 0101–0105 |
| Faz 2 · Product | Persona, yolculuk, senaryo, PRD, MVP, yol haritası | 0201–0206 |
| Faz 3 · Architecture | Mimari, veritabanı, teknoloji, modül, API, iş kuyruğu, bağdaştırıcı, hesaplama, güvenlik, gözlemlenebilirlik | 0301–0311 |
| Faz 4 · Development | Geliştirme süreci, ortamlar, CI/CD, test, güvenlik incelemesi, yayın | 0401–0406 |

## 3. Çalışma Sözleşmesi

Her mesaj başına tek doküman teslim edilir; doküman tam DoD'ye ulaştığında sonraki fazın dokümanına geçilir. Faz geçiş kapıları 0007 §5'te tanımlıdır. Bu sözleşme 0000 §6 (DoD) ile birlikte uygulanır.

## 4. Metodoloji

Proje aşağıdaki ilkelerle yönetilir:

- **Kanıt tabanlı ilerleme:** Her kararın dayandığı kanıt doküman içinde açıkça belirtilir; doğrulanmamış istatistik kullanılmaz.
- **Değişiklik yönetimi:** Tüm değişiklikler 0007 süreciyle yapılır; etkilenen dokümanlar güncellenir ve changelog'a işlenir.
- **İzlenebilirlik:** Her dokümanın karşıladığı maddeler künyede belirtilir; matris bu dokümanda sabitlenir.
- **Reddedilenlerin korunması:** Yanlışlanan kararlar çıkartılmaz, "Reddedildi" statüsüyle korunur ve gerekçesi saklanır.

## 5. 25 Madde İzlenebilirlik Matrisi

| Madde | Tanım | Karşılayan Doküman(lar) | Durum |
|---|---|---|---|
| 1 | Domain / Marka hazırlığı | 0006 | Tamamlandı |
| 2 | Vizyon | 0001 | Tamamlandı |
| 3 | Problem dokümanı | 0002 | Tamamlandı |
| 4 | Hedefler | 0003 | Tamamlandı |
| 5 | Hedef olmayanlar | 0003 | Tamamlandı |
| 6 | Başarı metrikleri | 0004 | Tamamlandı |
| 7 | Market search | 0101, 0102, 0103, 0105 | Tamamlandı |
| 8 | Rakiplerde mobil | 0103 | Tamamlandı |
| 9 | SWOT analizi | 0104 | Tamamlandı |
| 10 | Rakip analizleri | 0103 | Tamamlandı |
| 11 | Ürün üretim, yönetim, yönetişim | 0007 | Tamamlandı |
| 12 | İşlevsel gereksinimler | 0204 | Tamamlandı |
| 13 | User Journey | 0202 | Tamamlandı |
| 14 | Use Cases | 0203 | Tamamlandı |
| 15 | İşlevsel olmayan gereksinimler | 0204 | Tamamlandı |
| 16 | MVP Scope | 0205 | Tamamlandı |
| 17 | Post-MVP Roadmap | 0206 | Tamamlandı |
| 18 | System Architecture | 0301 | Tamamlandı |
| 19 | Database Design | 0303 | Tamamlandı |
| 20 | Technology Selection | 0304 | Tamamlandı |
| 21 | Services & Modules | 0305 | Tamamlandı |
| 22 | API Design | 0306 | Tamamlandı |
| 23 | Background Jobs & Scheduling | 0307 | Tamamlandı |
| 24 | AI Connectors | 0308 | Tamamlandı |
| 25 | Development Process | 0401 | Tamamlandı |

### Türetilmiş Dokümanlar (25 madde dışı)

Aşağıdaki dokümanlar 25 maddelik listeye doğrudan karşılık gelmez ancak bir üst dokümandan türetilmiştir:

| Doküman | Türetildiği | Karşıladığı madde |
|---|---|---|
| 0302 Domain Model | 0301–0303 köprüsü | Türetilmiş |
| 0309 Measurement & Scoring Engine | 0301 hesaplama hattı | Türetilmiş |
| 0310 Security & Multi-Tenancy | 0301 §5 izolasyon | Türetilmiş |
| 0311 Observability & Operations | 0301–0310 kapanış | Türetilmiş |
| 0402 Environments & Docker | 0401 süreç kuralları | Türetilmiş |
| 0403 CI/CD Pipeline | 0401 otomasyon | Türetilmiş |
| 0404 Test Strategy | 0403 kapı tanımları | Türetilmiş |
| 0405 Security Review & OWASP | 0310 §9 araçlığı | Türetilmiş |
| 0406 Release & Versioning | Faz 4 kapanışı | Türetilmiş |

## 6. Doküman Tanımını Tamamlama Ölçütleri (DoD)

Bir doküman aşağıdaki ölçütlerin tamamını karşıladığında "tamamlanmış" sayılır:

| # | Ölçüt | Doğrulama |
|---|---|---|
| D1 | Künye tablosu mevcut ve dolu (ID, Proje, Versiyon, Durum, Sahip, Tarih, Karşıladığı madde, İlişkili) | Kontrol |
| D2 | Amaç ve Kapsam bölümü tanımlı | Kontrol |
| D3 | İçerik, ilgili bölümleri kapsıyor (her bölüm en az bir paragraf) | Gözden geçirme |
| D4 | Açık Sorular tablosu mevcut (en az bir satır veya "Açık soru yok" notu) | Kontrol |
| D5 | Changelog tablosu mevcut ve ilk satır yazılı | Kontrol |
| D6 | İlişkili alanındaki referanslar doğru ve güncel | Çapraz kontrol |
| D7 | Dil tutarlılığı (Türkçe); terimler 0005 sözlüğüyle hizalı | Gözden geçirme |
| D8 | Durum "Review" veya "Approved" statüsünde (Approved için PO onayı gerekli, 0007 §5) | Onay akışı |

## 7. Sprint 0 Backlog

Sprint 0, Faz 0 dokümanlarının tamamlanmasına ve Faz 1'e geçiş kapısının açılmasına yöneliktir.

| Öncelik | İş kalemi | Durum | Sorumlu |
|---|---|---|---|
| Yüksek | 0001 Vision yazımı ve onayı | Tamamlandı | Product |
| Yüksek | 0002 Problem Statement yazımı | Tamamlandı | Product |
| Yüksek | 0003 Goals & Non-Goals yazımı | Tamamlandı | Product |
| Yüksek | 0004 Success Metrics yazımı | Tamamlandı | Product |
| Yüksek | 0005 Glossary yazımı | Tamamlandı | Product |
| Yüksek | 0006 Brand & Domain yazımı ve R-01 kapanışı | Tamamlandı | Product |
| Yüksek | 0007 Governance yazımı | Tamamlandı | Product |
| Yüksek | 0000 Master Plan yazımı | Tamamlandı | Product |
| Yüksek | 0301 System Architecture yazımı | Tamamlandı | Engineering |

## 8. Risk Kaydı

Riskler öncelik sırasına göre listelenir; her riskin sahibi ve durumu izlenir.

| ID | Risk | Olasılık | Etki | Öncelik | Sahip | Durum |
|---|---|---|---|---|---|---|
| R-01 | Alan adı ve marka tescil edilemez veya çakışma yaşanır | Orta | Yüksek | Yüksek | Product | Çözüldü (0006 onayıyla) |
| R-02 | V1'de desteklenen motor sayısı kısıtlı kalır ve değer önerisi zayıflar | Orta | Yüksek | Yüksek | Engineering | Açık (0102, 0308 takibinde) |
| R-03 | Müşteri verisi KVKK kapsamında beklenmedik sınıflandırmaya tabi olur | Düşük | Yüksek | Orta | Hukuk/PY | Açık (0310 değerlendirmesinde) |
| R-04 | AI motorları API erişim şartlarını veya fiyatlandırmasını değiştirir | Yüksek | Orta | Yüksek | Engineering | Açık (0308 dayanıklılık mekanizmaları hazır; 0007 kadansında izlenir) |
| R-05 | Pazar araştırması verileri hızla eskir; güncelliğini yitirir | Yüksek | Düşük | Orta | Product | Açık (0101–0105 üç aylık tazeleme döngüsü 0007 §6 R-05) |
| R-06 | Ekip kapasitesi (W4) mimari karmaşıklığı karşılayamaz | Orta | Yüksek | Yüksek | Engineering | Açık (0305 modül sınırları, 0401 süreç disipliniyle yönetilir). §10 ekip yapısı ile netleşti: Dilim 1-2'de 4 kişi (TL+CEO + 2 backend + analist), Dilim 3-4'te 5 kişi, pilot açılışta 6 kişi. |
| R-07 | Pilot kiracılar North Star metriğinde (WAT%) eşiği karşılayamaz | Orta | Orta | Orta | Product | Açık (0004 kalibrasyonu; 0201 persona kararıyla tetiklenir) |

### Risk Güncelleme Geçmişi

| Tarih | ID | Değişiklik |
|---|---|---|
| 13.07.2026 | R-01 | "Açık" → "Çözüldü" (0006 brand/domain onayı) |

## 9. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | 25 maddelik listenin eksik veya fazla olup olmadığı | Mevcut set Faz 0-4'ü kapsar; genişletme 0007 Tip 2 kararıyla. |
| ~~O-2~~ | ~~Faz geçiş kapılarının kesin zaman çizelgesi~~ | ~~0007 §5'te tanımlı; somut tarihler pilot kararına bağlı.~~ |
| **✅ O-2 (KAPANDI)** | **Event-driven model: pilot kiracı bulununca Faz 4 başlar. Tarih bazlı değil, olay bazlı geçiş.** | **PO kararı (21.07.2026). 0007 D-43.** |

## 10. Ekip Yapısı

### 10.1 Rol Dağılımı ve Çekirdek Ekip

AVIP, Faz 4 boyunca aşağıdaki ekip yapısıyla ilerler. Siz (TL + CEO) kalıcı olarak teknik liderlik ve stratejiyi üstlenir, kritik kod parçalarını (measure engine, AI connectors, scoring) bizzat yazar ve eş zamanlı olarak CEO sorumluluklarını (fon, pilot kiracı, marka, PO onayları) yürütürsünüz.

| Kişi | Rol | Dilim 1-2 | Dilim 3-4 | Pilot açılış |
|---|---|---|---|---|
| Siz | TL + CEO | 🟢 | 🟢 | 🟢 |
| Backend #1 | Platform & Identity | 1 | 1 | 1 |
| Backend #2 | Delivery, Insight + React temel | 1 | 1 | 1 |
| Frontend | React/TypeScript SPA | — | 1 | 1 |
| DevOps / SRE | Ortam, CI/CD, monitoring | — | — | 1 |
| Analist (AN) | Araştırma, dokümantasyon, görüşmeler | 1 | 1 | 1 |
| **Toplam** | | **4** | **5** | **6** |

### 10.2 Sorumluluklar

**Siz (TL + CEO):**
- measure, scoring engine, AI connectors (en kritik kod)
- Platform mimarisi, code review, mimari kararlar
- Strateji, pilot kiracı, fon, marka, PO onayları
- AN araştırmalarını yönlendirme

**Backend #1 — Platform & Identity:**
- platform katmanı (db, queue, httpmw, telemetry)
- identity (kayıt, oturum, RBAC, RLS)
- governance (denetim yazıcısı, kota iskeleti)
- altyapı (PG, Redis, S3, CI/CD ilk versiyon)

**Backend #2 — Geniş (Go + React temel):**
- delivery (output formatting, api handlers)
- insight (trend, anomali tespiti)
- scheduler, config (marka/panel tanımı)
- React SPA temel (Dilim 1-2'de 3-4 ekran)

**Frontend (React/TypeScript) — Dilim 3+:**
- White-label PDF önizleme
- Bildirim kanal ayarları
- Pano detay görünümleri (kırılım, karşılaştırma)
- Derin bağlantı yönetimi

**DevOps/SRE — Pilot öncesi:**
- 0402 ortam yönetimi, Docker, VM
- 0403 CI/CD boru hattı olgunlaştırma
- 0311 alarm seti, SLO takibi
- SOC 2 hazırlık başlangıcı

**Analist (AN):**
- Ajans görüşmeleri (0104 O-3, 11 ajans)
- Öneri kural kütüphanesi (D-52)
- Evertune incelemesi (D-49)
- Skor bileşen adları (D-89)
- Sürüm notu şablonları (D-91)
- Dokümantasyon güncellemeleri
- PO'ya hazırlık (size sunar, siz onaylarsınız)

### 10.3 İşe Alım Önceliği

| Sıra | Rol | Başlangıç | Gerekçe |
|---|---|---|---|
| 1 | Backend #1 (Platform) | Dilim 1 başı | Altyapıyı kurar, sizin en kritik koda odaklanmanızı sağlar |
| 2 | Analist (AN) | Dilim 1 başı | Araştırma yükünü alır; haftada 15-20 saat kod zamanı kazandırır |
| 3 | Backend #2 (Geniş) | Dilim 1 başı | Delivery + React temel işleri |
| 4 | Frontend (React/TS) | Dilim 3 başı | Frontend iş yükü patlayınca |
| 5 | DevOps/SRE | Pilot öncesi | En son; sözleşmeli/part-time başlanabilir |

### 10.4 Büyüme Mantığı

- **Dilim 1-2 (4 kişi):** Backend ağırlıklı. Frontend iş yükü az olduğu için Backend #2 React temel ihtiyacı karşılar. Analist, AN eylemlerini (D-78) yürütür.
- **Dilim 3-4 (5 kişi):** Frontend eklenir; Backend #2 React'tan kurtulup insight'a odaklanır.
- **Pilot açılış (6 kişi):** DevOps ile operasyonel olgunluk artırılır.

### 10.5 Teknik Beceri Seti

| Rol | Zorunlu | Tercih edilen |
|---|---|---|
| Backend #1 (Platform) | Go, PostgreSQL, Redis, Docker | sqlc, OpenTelemetry, testcontainers, Kubernetes |
| Backend #2 (Geniş) | Go, SQL, React temel | Redis Streams, S3, TanStack Query |
| Frontend | React, TypeScript, TanStack Query | Recharts, TanStack Table, E2E test |
| DevOps | Docker, CI/CD, Linux, monitoring | Kubernetes, Terraform, Prometheus/Grafana |
| Analist | Masa başı araştırma, raporlama, iletişim | SEO/GEO bilgisi, teknik yazım |

---

## Kaynaklar

- 0001–0007 · Faz 0 dokümanlarının tamamı
- 0204 PRD · 12 ve 15. maddelerin kaynağı
- 0005 Glossary · Terminoloji ve madde tanımı
- 0007 Governance · Faz geçiş kapıları, değişiklik süreci
- 0401 Development Process · Dilim planı, walking skeleton
- 0305 Services & Modules · Modül sınırları, CODEOWNERS

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 25 madde izlenebilirlik matrisi, 8 ölçütlü doküman DoD'su, Sprint 0 backlog, 7 kalem risk kaydı (R-01–R-07), türetilmiş doküman envanteri. |
| 1.1 | 21.07.2026 | O-2 kapandı: event-driven faz geçişi (pilot kiracı bulununca). 0007 D-43. |
| 1.2 | 22.07.2026 | §10 Ekip Yapısı eklendi: 4-6 kişilik büyüme planı (TL+CEO, 2 backend, frontend, DevOps, analist), işe alım önceliği, sorumluluklar, beceri seti. R-06 güncellendi. 0007 D-78 kapsamında ekip kararları. |
