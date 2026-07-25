# 0300 · Domain Katmanı Kapsamı

| Alan | Değer |
|---|---|
| Doküman ID | 0300 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 25 Temmuz 2026 |
| İlişkili | 0301–0311, 0000, 0501, ADR-001 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un domain katmanı dokümantasyonunun kapsamını tanımlar. 03-domain/ dizini altındaki tüm dokümanlara giriş niteliğindedir.

---

## 2. Kapsam

| # | Doküman | Konu |
|:-:|---------|------|
| 0301 | Çekirdek Kavramlar | Domain terminology, ubiquitous language |
| 0302 | Domain Modeli | Entity'ler, value object'ler, ilişkiler |
| 0303 | Aggregate'ler | Transaction sınırları, aggregate root'lar |
| 0304 | Domain Olayları | Event listesi, fırlatma kuralları |
| 0305 | Sınırlı Bağlamlar | BC1-BC6, context map |
| 0306 | Domain Servisleri | Stateless service tanımları |
| 0307 | Arkaplan İşleri | Job kuyruğu, worker tasarımı, DLQ |
| 0308 | AI Bağlayıcıları | Engine adapter sözleşmesi, hata sınıfları |
| 0309 | Hesap Motoru | Skor hesaplama, GA, fidelite, determinizm |
| 0310 | Domain Güvenlik | RLS, IAM, sır yönetimi |
| 0311 | Gözlemlenebilirlik | Metrik kataloğu, alarmlar, panolar |

---

## 3. Domain Bağlamları

| BC # | Bağlam | Sorumluluk |
|:----:|--------|------------|
| BC1 | Identity | Kullanıcı, tenant, workspace yönetimi |
| BC2 | Config | Marka, panel, prompt set yapılandırması |
| BC3 | Measure | Ölçüm, skorlama, hesaplama |
| BC4 | Insight | Trend, öneri, analiz |
| BC5 | Delivery | E-posta, PDF, bildirim |
| BC6 | Governance | Audit, quota, usage, RBAC |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: domain kapsamı ve bağlam haritası |
