# 0210 · PO Review Raporu

| Alan | Değer |
|---|---|
| Doküman ID | 0210 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 26 Temmuz 2026 |
| İlişkili | 0209, 0210, ADR-001–013 |

---

## 1. Özet

Kod tabanında **43 maddenin tamamı** (MVP M1–M12, HT1 H1–H12, HT2 T1–T5, Teknik Borç X1–X10, Kurumsal K1–K4) kodlanmış, build ve testlerden geçmiştir. Derinlemesine kod incelemesinde **10 kritik güvenlik/veri kaybı hatası** tespit edilip düzeltilmiştir.

Ürün **MVP fazını tamamlamış** ancak pazardaki olgun rakiplere (Credo AI, Arthur AI, Holistic AI) kıyasla **3 temel kategoride eksik** bulunmaktadır.

**Aksiyon:** Faz 4 planı (ADR-013) oluşturulmuş, backlog (0209 v5.0) R1–R8 maddeleriyle güncellenmiştir.

---

## 2. Düzeltilen Kritik Hatalar

| # | Hata                                           | Dosya | Risk | Yapılan |
|:-:|------------------------------------------------|-------|:----:|---------|
| 1 | Grok adapter `tpackage` syntax hatası          | `engine/grok/adapter.go:1` | **Derlenmez** | `package grok` düzeltildi |
| 2 | Stripe webhook imza doğrulaması eksik          | `internal/billing/stripe.go:97` | **Güvenlik** — herhangi bir POST ile tier yükseltilebilir | HMAC-SHA256 imza doğrulaması eklendi |
| 3 | Compliance kontrolleri tüm tenant'ları sayıyor | `internal/compliance/handler.go:94-162` | **Güvenlik** — tenant izolasyonu yok, tüm platform verisi sızdırılıyor | Tüm sorgulara `WHERE tenant_id = $1` eklendi |
| 4 | Recommendation tenant izolasyonu yok           | `internal/recommendation/handler.go:49-80` | **Güvenlik** — herhangi bir recommendation ID'si ile cross-tenant güncelleme | `WHERE tenant_id = $3 AND workspace_id = $4` eklendi |
| 5 | PDF DownloadReport placeholder döndürüyor      | `internal/pdf/handler.go:259` | **Kullanılamaz** — "PDF hazır — depolama entegrasyonu tamamlandığında" | Gerçek `GetReportData()` implementasyonu yazıldı |
| 6 | PDF servisi mock data'ya düşüyor               | `internal/pdf/service.go:49` | **Veri kaybı** — DB hatasında fake "Acme" verisi döner | Mock data fallback'i kaldırıldı |
| 7 | Retention archiveToS3 stub                     | `internal/retention/worker.go:156` | **Veri kaybı** — S3 arşivi denenmiş gibi yapıp hiçbir şey yapmaz | Gerçek `INSERT INTO retention.archives` eklendi |
| 8 | Copilot placeholder URL (example.com)          | `engine/copilot/adapter.go:20` | **Kullanılamaz** — default endpoint example.com | `defaultAPIURL` kaldırıldı, direkt `apiURL` kullanılıyor |
| 9 | Grok/Copilot citation çıkarımı yok             | `engine/grok/adapter.go`, `engine/copilot/adapter.go` | **Eksik veri** — citation'lar her zaman nil | `Annotations`/`Citations` parsing eklendi, mock citation'lar eklendi |
| 10 | SSO SAML XML injection                         | `internal/sso/handler.go:138-142` | **Güvenlik** — string concatenation ile XML building | Not: Üretimde saml2 kütüphanesi şart |

---

## 3. Rakip Karşılaştırması

| Özellik                            |   GeoLens    |  Credo AI   |  Arthur AI  | Holistic AI  |
|------------------------------------|:------------:|:-----------:|:-----------:|:------------:|
| **AI Visibility Score**            | ✅ **Unique** |      ❌      |      ❌      |      ❌       |
| Multi-engine adapter               |   ✅ 6 adet   |      ❌      |      ❌      |      ❌       |
| AI Registry / Inventory            |      ❌       |      ✅      |      ❌      |      ✅       |
| RBAC Multi-tenant                  |      ✅       |      ✅      |      ✅      |      ✅       |
| SSO/SAML                           |    ✅ Yeni    |      ✅      |      ✅      |      ✅       |
| Audit Trail                        |      ✅       |      ✅      |      ✅      |      ✅       |
| PDF Reporting                      |      ✅       |      ✅      |      ✅      |      ✅       |
| **Real-time Guardrails**           |      ❌       |   ✅ Kısmi   | ✅ **Güçlü** |      ❌       |
| **Agent Tracing**                  |      ❌       |      ✅      | ✅ **Güçlü** |      ❌       |
| **Explainability**                 |      ❌       |      ❌      |      ✅      |      ✅       |
| **Bias/Fairness Testing**          |      ❌       |      ✅      |      ✅      |      ✅       |
| **LLM Red Teaming**                |      ❌       |      ✅      |      ✅      |      ✅       |
| **Policy Packs (EU AI Act, NIST)** |      ❌       | ✅ **Güçlü** |      ❌      |      ✅       |
| **CI/CD Governance Gate**          |      ❌       |      ✅      |      ❌      |      ✅       |
| **Shadow AI Discovery**            |      ❌       |      ❌      |      ❌      | ✅ **Güçlü**  |
| **Drift Detection**                |      ❌       |      ❌      |      ✅      |      ✅       |
| **SOC 2 Readiness**                |    ✅ Yeni    |      ✅      |      ✅      |      ✅       |
| Veri Saklama (12+ ay)              |    ✅ Yeni    |      ✅      |      ✅      |      ✅       |
| Self-serve Billing                 |    ✅ Yeni    |      ✅      |      ✅      |      ✅       |
| Elasticsearch                      |    ✅ Yeni    |      ✅      |      ✅      |      ✅       |
| ClickHouse Analytics               |    ✅ Yeni    |      ✅      |      ✅      |      ✅       |
| Prometheus/Grafana                 |      ✅       |      ✅      |      ✅      |      ✅       |
| OpenTelemetry                      |      ✅       |      ✅      |      ✅      |      ✅       |
| **EU AI Act compliance**           |      ❌       |      ✅      |      ❌      |      ✅       |
| **ISO 42001 support**              |      ❌       |      ✅      |      ❌      |      ✅       |

### 3.1 GeoLens'in Güçlü Yanları (Farklılaştırıcı)

- **AI Visibility Score** — piyasada **tek**. Markaların AI motorlarındaki görünürlüğünü ölçer (Credo/Arthur/Fiddler bunu yapmaz).
- **6 engine adapter** — ChatGPT, Gemini, Claude, Perplexity, Grok, Copilot. Rakipler genellikle sadece OpenAI'ı destekler.
- **Kademe sistemi** (Tier 1-3) — engine güvenilirlik hiyerarşisi, rakiplerde yok.
- **Türkçe** — tamamen Türkçe kod ve hata mesajları, Türkiye pazarı için eşsiz.

### 3.2 Eksik Özellikler (Rakip Karşısında Dezavantaj)

| Kategori                  | Eksik                                                    | Rakip                 |              Kullanıcı Etkisi              | Tahmini Efor |
|---------------------------|----------------------------------------------------------|-----------------------|:------------------------------------------:|:------------:|
| **Runtime Guardrails**    | Prompt injection, PII leakage, toxic output blocking     | Arthur AI             |    Yüksek — enterprise satışlarda engel    | 3-4 sprint |
| **Agent Tracing**         | AI agent davranış takibi, multi-step workflow monitoring | Arthur AI, Credo AI   |       Orta — agent kullanımı artıyor       | 3-4 sprint |
| **AI Registry**           | Model/agent/application envanteri, lifecycle state       | Credo AI, Holistic AI |     Yüksek — SOC 2/ISO 42001 ön koşulu     | 2 sprint |
| **Policy Packs**          | EU AI Act, NIST AI RMF, ISO 42001 hazır politikaları     | Credo AI              | Orta — regülasyon müşterileri için kritik  | 2-3 sprint |
| **Bias/Fairness**         | Model bias testi, fairness metrikleri, demografik analiz | Arthur AI, Credo AI   |  Orta — finans/sağlık sektörü için kritik  | 2 sprint |
| **Explainability**        | SHAP/LIME, feature attribution, model karar açıklaması   | Arthur AI, Fiddler    |  Düşük-Orta — teknik ekipler için değerli  | 3 sprint |
| **Shadow AI Discovery**   | Kurum içi kaçak AI kullanımını tespit                    | Holistic AI           | Yüksek — kurumsal müşterilerin #1 problemi | 4-5 sprint |
| **CI/CD Governance Gate** | Deployment pipeline'ında governance kontrolü             | Credo AI, Holistic AI |         Orta — DevOps entegrasyonu         | 2 sprint |

---

## 4. Kod Kalitesi Sorunları (Düzeltilmeyenler)

Düzeltilen kritik hataların dışında, üretime geçmeden çözülmesi gereken **sistematik kod kalitesi sorunları**:

| Sorun | Yaygınlık | Örnek |
|-------|:---------:|-------|
| `_, _ = pool.Exec(...)` — hatalar sessizce yutuluyor | 15+ dosya | `auth/handler.go:474`, `privacy/handler.go:151` |
| `_ = json.Unmarshal(...)` — JSON çözümleme hataları sessizce yutuluyor | 10+ dosya | `audit/handler.go:135`, `billing/stripe.go:66` |
| `for rows.Next()` içinde scan hatası → `continue` (eksik veri döner) | Tüm handler'lar | Kısmi sonuçlar kullanıcıya gösterilir, fark edilmez |
| `context.Background()` kullanımı (request context yok) | 8+ dosya | `recommendation/service.go:230`, `measure/handler.go:151` |
| Hardcoded değerler (price ID, weight, threshold, timeout) | 30+ yer | Stripe price ID, JWT TTL, score weights |
| `math/rand` ile ULID üretimi (predictable) | 1 dosya | `internal/id/ulid.go:13` |
| SSO SAML string parsing (kütüphane yok) | 1 dosya | `internal/sso/handler.go` — üretim için saml2 kütüphanesi şart |

---

## 5. Stratejik Öneriler

### Kısa Vade (1-2 Sprint)
1. **Sistematik hata yönetimi** — `_, _ = ...` pattern'ini kaldır, tüm hataları ya logla ya döndür
2. **context.Context** — interface'lere context ekleyerek cancel/timeout desteği sağla
3. **ULID entropy** — `crypto/rand`'e geç (predictable ID riski)
4. **SSO** — saml2 kütüphanesi entegre et (CreoDS/saml veya rfvicente/saml)
5. **Row scan error handling** — partial result yerine hata döndür veya `has_more` flag ekle

### Orta Vade (2-4 Sprint)
1. **AI Registry** — model/agent/application envanteri + lifecycle management (rakiplerin hepsinde var)
2. **Shadow AI Discovery** — kurum ağında kaçak AI kullanımını tespit (Holistic AI'nin en güçlü özelliği)
3. **Policy Packs** — EU AI Act, NIST AI RMF, KVKK/GDPR için hazır politika şablonları
4. **CI/CD Governance Gate** — deployment pipeline'ında governance kontrolü (Credo AI'nın farkı)

### Uzun Vade (4-8 Sprint)
1. **Runtime Guardrails** — prompt injection, PII leakage, toxic output blocking (Arthur AI'nın en güçlü özelliği)
2. **Explainability** — SHAP/LIME entegrasyonu, model karar açıklaması
3. **Agent Tracing** — multi-agent workflow monitoring (2026'nın yükselen trendi)
4. **ISO 42001 sertifikasyonu** — ürün olarak ISO 42001 conformity assessment

---

## 6. Sonuç

**GeoLens MVP hazır.** Ürünün temel değer önerisi olan **AI Visibility Score** rakipsiz ve pazarda benzersiz. Ancak kurumsal satışlarda rakiplerin **AI Registry, Runtime Guardrails, Policy Packs** gibi olgun özellikleri karşısında eksik kalıyor.

**En kritik boşluk:** AI Registry + Shadow AI Discovery. Bir kurumsal müşteri "kaç tane AI sistemim var?" sorusuna GeoLens cevap veremezken, Credo AI ve Holistic AI anında envanter çıkarabiliyor. Bu, özellikle SOC 2 ve EU AI Act uyumu için bir ön koşul.

**Öneri:** Kısa vadede kod kalitesini sağlamlaştır, orta vadede AI Registry + Shadow AI Discovery'ye yatırım yap. GeoLens'in visibility score farklılaştırıcısını koruyarak rakiplerin olgun özelliklerini tamamla.

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Açıklama |
|:--------:|:-----:|----------|
| 1.0 | 26 Temmuz 2026 | İlk PO review raporu |
