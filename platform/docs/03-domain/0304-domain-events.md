# 0304 · Domain Events (Alan Olayları)

| Alan | Değer |
|---|---|
| Doküman ID | 0304 |
| Proje | GeoLens Platform |
| Versiyon | 1.5 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 10 Ağustos 2026 |
| İlişkili | 0302, 0303, 0305, 0306, 0307, 0309, 0311, 0511, 0209, 0210, 0416, 0417, 0418, 0419 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'daki tüm alan olaylarını (domain events), olay şemalarını, üretici/tüketici bağlarını ve olay fırtınası (event storming) çıktılarını tanımlar. Amaç, bağlamlar arası iletişimin olay tabanlı kısmını sabitlemek ve 0307 Background Jobs tasarımına girdi sağlamaktır. HT1 genişletmesi (v1.2) BC7-BC10 olaylarını, Faz 4 + HT2 genişletmesi (v1.3) BC11-BC13 olaylarını kapsar (0302 v1.3 ile senkron).

> **Tasarım filtresi bağlantısı:** Bu doküman **F2** (ölçek — olay tabanlı iletişim bağlamlar arası gevşek bağlılık sağlar) filtresine kanıt sağlar.

---

## 2. Olay Taşıma Mekanizması

GeoLens'te alan olayları **outbox pattern** ile taşınır:

```
1. İşlem (transaction) → EventOutbox kaydı (aynı PG işleminde)
2. Outbox dağıtıcısı → pending kayıtları okur (SKIP LOCKED)
3. Redis Streams kuyruğuna yazar
4. Tüketici (worker) kuyruktan okur ve işler
5. dispatched işaretlenir
```

| Bileşen | Teknoloji |
|---------|----------|
| EventOutbox tablosu | PostgreSQL (0303 §5 transaction kapsamı) |
| Kuyruk | Redis Streams + tüketici grupları |
| Dağıtıcı | dev.geolens.queue.OutboxDispatcher (periyodik polling) |
| Tüketici | worker profili (Spring — q:measure + q:governance tüketicileri) |

---

## 3. Olay Kataloğu

### 3.1 Ölçüm ve Hesap Olayları (BC3)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **MeasurementJobCreated** | scheduler | measure worker | İzleme planı penceresi açıldı | job_id, workspace_id, panel_version_id, idempotency_key, zaman_aralığı |
| **MeasurementJobCompleted** | measure worker | insight, delivery | Tüm motor yanıtları alındı ve skorlar hesaplandı | job_id, calculation_run_id, skor_özeti, motor_kırılımı |
| **MeasurementJobPartial** | measure worker | insight, delivery | Bazı motorlar başarısız, kısmi sonuç var | job_id, başarılı_motor_listesi, başarısız_motor_listesi, kısmi_skor |
| **MeasurementJobFailed** | measure worker | governance | Tüm denemeler başarısız | job_id, hata_kodu, son_deneme_zamanı |
| **ScoreCalculated** | measure worker | delivery | Skor hesaplandı | calculation_run_id, brand_id, score_değer, fidelity_label, güven_aralığı |
| **ScoreSignificantChange** | measure worker | delivery | İstatistiksel anlamlı değişim tespit edildi | brand_id, eski_skor, yeni_skor, değişim_yüzdesi, anlamlılık_düzeyi |
| **SiteAuditCompleted** | measure worker | insight, delivery | Site denetimi tamamlandı | audit_run_id, site_id, bulgu_sayısı, kritik_bulgu_var_mı |

### 3.2 İçgörü Olayları (BC4)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **RecommendationGenerated** | insight worker | delivery | Yeni öneri üretildi | recommendation_id, workspace_id, kanıt_derecesi, özet |
| **RecommendationApplied** | kullanıcı (API) | insight | Kullanıcı öneriyi uygulandı işaretledi | recommendation_id, işaret_zamanı |
| **RecommendationRejected** | kullanıcı (API) | insight | Kullanıcı öneriyi reddetti | recommendation_id, gerekçe (opsiyonel) |

### 3.3 Bildirim Olayları (BC5)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **AlertTriggered** | delivery worker | notify worker | Anlamlı değişim + uyarı kuralı eşleşmesi | alert_id, workspace_id, kanal, mesaj_şablonu, derin_bağlantı |
| **AlertFeedbackReceived** | kullanıcı (API) | delivery | Kullanıcı geri bildirim verdi (yerinde/yanlış alarm) | alert_id, geri_bildirim_türü |
| **DigestGenerated** | delivery worker | notify worker | Haftalık özet zamanı | digest_id, workspace_id, özet_içerik, derin_bağlantılar |
| **ReportGenerated** | delivery worker | notify worker | Rapor hazır | report_id, workspace_id, S3_url, süre |
| **ReportFailed** | delivery worker | governance | Rapor üretimi başarısız | report_id, hata_kodu |

### 3.4 Yönetim Olayları (BC6 — Governance)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **QuotaExceeded** | governance | — (log/alarm) | Kota aşımı tespit edildi | tenant_id, sayaç_türü, limit, mevcut_kullanım |
| **TenantProvisioned** | identity | governance (audit log) | Yeni kiracı oluşturuldu | tenant_id, tür, paket |
| **EntitlementChanged** | identity | config, measure | Paket değişikliği | tenant_id, eski_haklar, yeni_haklar |

### 3.5 Kimlik Olayları (BC1)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **UserRegistered** | identity | governance (audit log) | Yeni kullanıcı kaydı | user_id, tenant_id, kayıt_yöntemi |
| **MembershipChanged** | identity | config, measure | Rol/üyelik değişikliği | membership_id, user_id, tenant_id, eski_rol, yeni_rol |
| **WorkspaceArchived** | identity | measure, delivery | Çalışma alanı arşivlendi | workspace_id, tenant_id |

### 3.6 Arşiv Olayları (BC7 — HT1)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **MeasurementCompleted → ArchiveEntryCreated** | measure worker → archive worker | archive | Ölçüm tamamlandı, ham yanıtlar arşive gönderilir | job_id, brand_id, engine_listesi, ham_yanıt_sayısı, workspace_id |
| **ArchiveExportRequested** | kullanıcı (API) | archive worker | Kullanıcı dışa aktarım talep etti | export_id, workspace_id, dönem_başlangıç/bitiş, format (json/csv), filtreler |
| **ArchiveExportCompleted** | archive worker | notify worker | Dışa aktarım dosyası hazır | export_id, S3_url, imzalı_URL, satır_sayısı, dosya_boyutu |
| **ArchiveExportFailed** | archive worker | governance | Dışa aktarım başarısız | export_id, hata_kodu, hata_detayı |

### 3.7 Replay Olayları (BC8 — HT1)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **MeasurementCompleted → SnapshotCaptured** | measure worker → replay worker | replay | Ölçüm tamamlandı, conversation snapshot'ı alınır | job_id, brand_id, prompt_text, engine_listesi, snapshot_id |
| **SnapshotCompared** | kullanıcı (API) | replay | Kullanıcı iki snapshot'ı karşılaştırdı | snapshot_a_id, snapshot_b_id, has_changed, brand_id, engine_name |

### 3.8 SEO Olayları (BC9 — HT1)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **SEOConnectionEstablished** | seo worker | governance (audit log) | Google OAuth2 bağlantısı kuruldu | connection_id, platform (SC/GA4), email, workspace_id |
| **SEOConnectionDisconnected** | kullanıcı (API) | seo worker, governance | Kullanıcı bağlantıyı kaldırdı | connection_id, platform, workspace_id |
| **SEOSyncCompleted** | seo-sc/ga4 worker | UI | Periyodik veri senkronizasyonu tamamlandı | platform, satır_sayısı, senkron_süresi, measured_at |
| **SEOSyncFailed** | seo-sc/ga4 worker | governance | Veri senkronizasyonu başarısız | platform, hata_kodu, deneme_sayısı, sonraki_deneme_zamanı |
| **TokenExpiryImminent** | seo worker | notify worker | OAuth2 token'ı 7 gün içinde süresi doluyor | connection_id, platform, email, expiry_date |

### 3.9 Analiz Olayları (BC10 — HT1)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **MeasurementCompleted → SentimentAnalyzed** | measure worker → sentiment worker | analysis | Ölçüm tamamlandı, duygu analizi tetiklenir | job_id, brand_id, mention_sayısı, overall_sentiment, positive/neutral/negative_skor |
| **HallucinationDetected** | sentiment worker | delivery (alert) | Hallüsinasyon tespit edildi | flag_id, brand_id, engine_name, tür, severity, confidence, description |
| **HallucinationVerified** | kullanıcı (API) | sentiment worker | Kullanıcı hallüsinasyonu doğruladı/yanlış pozitif işaretledi | flag_id, verified (true/false) |
| **MeasurementCompleted → GapAnalysisStarted** | measure worker → gap worker | competitive | Ölçüm tamamlandı, competitive gap analizi tetiklenir | job_id, brand_id, rakip_sayısı, gap_türleri |
| **GapAnalysisCompleted** | gap worker | insight, delivery | Gap analizi tamamlandı | snapshot_id, brand_id, competitor_id, competitive_score, gap_overview |
| **GapThresholdExceeded** | gap worker | delivery (alert) | Gap eşiği aşıldı → alert tetiklenir | snapshot_id, gap_türü, gap_değeri, eşik, brand_id, competitor_id |

### 3.10 Yapay Zekâ Yönetişim Olayları (BC11 — Faz 4)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **RegistryEntityRegistered** | registry servisi | explain, gate, governance (audit log) | Envanter kaydı oluşturuldu | entity_id, entity_type, lifecycle_state, risk_class, provider |
| **RiskAssessmentCompleted** | registry servisi | gate, incident | Risk değerlendirmesi yazıldı | entity_id, risk_class, score, assessed_by |
| **DiscoveryScanCompleted** | discovery servisi | registry (aday bulgular), governance | Kaçak AI taraması tamamlandı | scan_id, bulunan_sayısı, risk_dağılımı |
| **GuardrailViolation** | guardrail runtime | incident, delivery | Kural eşleşti, aksiyon (block/flag/log) uygulandı | evaluation_id, rule_id, category, action, severity, matched_pattern |
| **PolicyPackApplied** | policy servisi | governance (audit log) | Politika paketi uygulandı | pack_id, framework, kontrol_sayısı |
| **BiasTestCompleted** | bias servisi | incident (önyargı bulgusu), delivery | Önyargı testi tamamlandı | test_id, model_id, metric_type, fairness_score, has_bias |
| **GateCheckDecision** | gate servisi | registry (lifecycle), incident | Deployment kapısı karar verdi (onaylandı/engelli) | check_id, entity_id, target_env, decision, passed/total_checks |
| **AgentTraceCompleted** | agent runtime | governance | Ajan izi tamamlandı | trace_id, agent_name, workflow_name, status, süre |
| **RedTeamRunCompleted** | redteam servisi | delivery (rapor), governance | Kırmızı takım koşusu tamamlandı | run_id, target_name, total/passed/failed, defense_score |

### 3.11 Yapay Zekâ Operasyon Olayları (BC12 — Faz 4)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **IncidentOpened** | incident servisi | delivery (notify), governance | Olay kaydı açıldı (otomatik/manüel) | incident_id, severity, category, source, entity_id |
| **IncidentResolved** | incident servisi | delivery (notify) | Olay çözüldü/kapandı | incident_id, severity, resolution, süre |
| **DriftAlertTriggered** | drift analiz servisi | incident (olay adayı), delivery (alert) | Sapma eşiği aşıldı | alert_id, entity_id, metric, drift_score, severity, delta |
| **OptimizationRecommendationGenerated** | optimize servisi | insight, UI | Yeni optimizasyon önerisi üretildi | recommendation_id, category, impact, effort, score_potential |
| **PromptAuditCompleted** | prompt denetim servisi | delivery | Prompt denetimi tamamlandı | audit_id, prompt_id, engine_name, status, score |
| **ModelBenchmarkCompleted** | benchmark servisi | delivery | Model kıyaslaması tamamlandı | benchmark_id, model_name, engine_name, accuracy, latency |
| **VersionEntryCreated** | version servisi | governance | Sürüm değişikliği kaydedildi | entity_type, entity_id, old/new_version, changed_by |

### 3.12 Faturalama Olayları (BC13 — HT2)

| Olay | Üretici | Tüketici | Tetikleyici | İçerdiği Veri |
|------|---------|----------|------------|---------------|
| **InvoiceCreated** | Stripe webhook | billing servisi, UI | Stripe invoice.created | invoice_id, tenant_id, stripe_invoice_id, amount_total, currency |
| **InvoicePaid** | Stripe webhook | billing servisi, delivery | Stripe invoice.paid | invoice_id, tenant_id, stripe_invoice_id, amount_total |
| **InvoiceVoided** | Stripe webhook | billing servisi | Stripe invoice.voided | invoice_id, tenant_id, stripe_invoice_id |
| **EInvoiceSent** | e-Fatura servisi | UI | e-Fatura/e-Arşiv GİB'e iletildi | invoice_id, document_id, invoice_type, gib_status |
| **GIBStatusRejected** | e-Fatura servisi | delivery (uyarı) | GİB belgeyi reddetti | invoice_id, gib_response_id, hata_detayı |

---

## 4. Olay Fırtınası (Event Storming) Çıktısı

### 4.1 Ölçüm Hattı Olay Zinciri (HT1 Genişletilmiş)

```
İzleme Planı → MeasurementJobCreated → [Motor çağrıları] → MeasurementJobCompleted
                                                                     ↓
                                                            ScoreCalculated → ScoreSignificantChange
                                                                     ↓
                                                            ┌─────────────────────┬──────────────────────┬──────────────────────┐
                                                            ▼                     ▼                      ▼                      ▼
                                               Recommendation   ArchiveEntry      SnapshotCaptured   SentimentAnalyzed
                                               Generated        Created                              ↓
                                                    ↓                                    HallucinationDetected
                                               [kullanıcı                                  → AlertTriggered
                                                işaretler]                                    ↓
                                                                                        GapAnalysisStarted
                                                                                             ↓
                                                                                    GapAnalysisCompleted
                                                                                             ↓
                                                                                    GapThresholdExceeded
                                                                                             ↓
                                                                                    AlertTriggered
```

**HT1 genişletmesi:** Ölçüm tamamlandıktan sonra 4 paralel kol tetiklenir:
1. **Insight** → RecommendationGenerated (MVP'den devam)
2. **Archive** → ArchiveEntryCreated (yeni)
3. **Replay** → SnapshotCaptured (yeni)
4. **Analysis** → SentimentAnalyzed → HallucinationDetected → GapAnalysisStarted → GapAnalysisCompleted (yeni)

### 4.2 Haftalık Özet Akışı

```
[Zamanlayıcı tetiği] → DigestGenerated → [e-posta servisi] → [kullanıcı panoya tıklar]
```

### 4.3 Kota Yönetimi Akışı

```
[Her motor çağrısı] → QuotaExceeded? → [Evet] → İş engellenir, alarm üretilir
                                     → [Hayır] → İş devam eder
```

### 4.4 AI Yönetişim Olay Akışı (Faz 4)

```
Envanter Kaydı → RegistryEntityRegistered → RiskAssessmentCompleted
                                                       ↓
                                              GateCheckDecision (CI/CD)
                                                       ↓
                                      GuardrailViolation → IncidentOpened
                                               ↓                 ↓
                                       DriftAlertTriggered  IncidentResolved
                                               ↓
                                      RedTeamRunCompleted → defense_score (0-100)
```

**Faz 4 genişletmesi:** Yönetişim hattı üç kademede işler:
1. **Keşif** — RegistryEntityRegistered → RiskAssessmentCompleted (envanter ve risk)
2. **Savunma** — GuardrailViolation ve GateCheckDecision (runtime + deployment kapısı)
3. **Ölçüm** — DriftAlertTriggered ve RedTeamRunCompleted (sapma izleme + savunma testi)
Kural ihlalleri (GuardrailViolation, BiasTestCompleted, GateCheckDecision) IncidentOpened'a kaynak olur.

### 4.5 Fatura ve GİB Akışı (HT2)

```
Stripe webhook → InvoiceCreated → InvoicePaid → e-Fatura: EInvoiceSent
                                                       ↓
                                          GIBStatusRejected → uyarı
```

**HT2 genişletmesi:** Fatura olayları dış webhook (Stripe/GİB) kaynaklıdır; outbox gerektirmez (O-5 gerekçesiyle aynı). e-Fatura akışı GİB durumunu none → pending → accepted/rejected olarak taşır.

---

## 5. Olay Şablonu (Event Schema)

Tüm olaylar aşağıdaki ortak alanları taşır. HT1'de eklenen olaylar da aynı şablonu kullanır:



```json
{
  "event_id": "ulid",
  "event_type": "MeasurementJobCompleted",
  "event_version": 1,
  "producer": "measure-worker",
  "timestamp": "2026-07-22T12:00:00Z",
  "correlation_id": "correlation-chain-id",
  "tenant_id": "tenant-ulid",
  "data": { }
}
```

| Alan | Tip | Zorunlu | Açıklama |
|------|-----|:-------:|----------|
| event_id | ULID | ✅ | Benzersiz olay kimliği |
| event_type | string | ✅ | Olay türü adı (PascalCase) |
| event_version | int | ✅ | Şema versiyonu (uyumluluk için) |
| producer | string | ✅ | Üretici bileşen adı |
| timestamp | datetime | ✅ | Olay üretim zamanı (UTC) |
| correlation_id | string | ✅ | Korelasyon zinciri (request_id → job_id → calculation_run_id) |
| tenant_id | string | ✅ | Kiracı bağlamı |
| data | object | ✅ | Olaya özgü veri |

---

## 6. Olay Tüketim Garantileri

| Garanti | Düzey | Mekanizma |
|---------|:-----:|-----------|
| En az bir kez teslim (at-least-once) | ✅ | Redis Streams tüketici grupları |
| İşlemsel üretim (transactional outbox) | ✅ | EventOutbox + PG transaction |
| Sıralı işleme (ordered processing) | ⚠️ | Per-brand partitioning (HT1 hedefi); MVP'de sıra garantisi yok |
| Idempotent tüketim | ✅ | Her olay idempotency_key taşır; yeniden işleme güvenli |
| Ölü kuyruk (DLQ) | ✅ | Max deneme sonrası DLQ'ya taşınır |
| Monitoring | ✅ | 0311 metrikleri: üretim hızı, tüketim gecikmesi, DLQ sayısı |

---

## 7. GeoLens İçin Çıkarımlar

1. **21'den 38 alan olayına genişleme** (MVP → HT1). 17 yeni olay eklenmiştir: 4 arşiv (BC7), 2 replay (BC8), 5 SEO (BC9), 6 analiz (BC10).
2. **Ölçüm hattı olayları** HT1'de 4 paralel kola ayrılmıştır: insight, archive, replay, analysis. MeasurementJobCompleted artık tek bir tüketici değil, 4 farklı worker profiline yönlendirme yapar.
3. **Outbox pattern** tüm olay üretiminde zorunludur. Bu, olay kaybını önler ve transaction bütünlüğünü korur.
4. **Correlation_id** tüm olay zinciri boyunca taşınır. HT1'de measurement_job_id → snapshot_id/archive_entry_id/gap_snapshot_id zincirleri eklenmiştir.
5. **0307 (Background Jobs)** bu olayların kuyruk yapılandırmasını (Streams, tüketici grupları, DLQ) tanımlar. Kod gerçeğinde (dev.geolens.queue.QueueProperties) 12 Redis Stream sabiti vardır: q:measure, q:audit, q:report, q:notify, q:dead (DLQ) + 6 analiz akışı (q:sentiment, q:replay, q:archive, q:gap, q:technical-geo, q:content-geo) + q:governance (Faz 4 yönetişim olayları, O-6). SEO senkronu stream kullanmaz; zamanlayıcı/ticker tabanlıdır (q:seo-sc/q:seo-ga4 yoktur).
6. **SEO olayları** diğerlerinden farklı olarak periyodik zamanlayıcı ile tetiklenir (ölçüm olayıyla değil). SEOSyncCompleted/SEOSyncFailed worker profili bazlı metriklerle izlenir.
7. **GapThresholdExceeded** ve **HallucinationDetected** olayları, doğrudan alert sistemini (BC5) tetikleyerek uyarı üretir — ölçüm sonrası ikincil analizlerden kaynaklanan otomatik uyarı modelinin ilk örnekleridir.
8. **Faz 4 ve HT2 genişletmesi (v1.3):** 38'den 59 alan olayına genişleme — 21 yeni olay (BC11: 9 yönetişim, BC12: 7 operasyon, BC13: 5 fatura). Yeni olaylar 0302 v1.3'teki BC11-BC13 varlıklarının durum geçişlerinden türetilmiştir.
9. **Faz 4 olay taşıması (O-6 kapatıldı):** BC11/BC12 olayları (GuardrailViolation, GateCheckDecision, IncidentOpened, DriftAlertTriggered, RedTeamRunCompleted) outbox üzerinden taşınır — handler'lar `queue.EnqueueEvent` ile `public.event_outbox`'a yazar, Dispatcher `q:governance` stream'ine iletir. DB tablo yazımı korunur. BC13 olayları dış webhook (Stripe/GİB) kaynaklıdır ve outbox gerektirmez.

---

## 8. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Per-brand partitioning MVP'de gerekli mi? | ⏳ MVP'de hayır; HT1'de değerlendirilir. |
| O-2 | Olay şeması versiyonlama stratejisi | ⏳ 0307 ile birlikte netleşir. |
| O-3 | Benchmark olayları (HT2) şimdiden tanımlanmalı mı? | ⏳ Hayır; HT2'de eklenir. |
| O-4 | HT1 ölçüm sonrası 4 paralel kol (insight, archive, replay, analysis) — başarısızlık durumunda diğer kolları beklemeli mi? | ⏳ Mevcut tasarım: tamamen bağımsız (bir kol başarısız olursa diğerleri etkilenmez). |
| O-5 | SEO worker'larının (seo-sc, seo-ga4) olayları outbox üzerinden mi yoksa doğrudan Redis Streams'e mi yazılmalı? | ⏳ Mevcut karar: doğrudan Redis Streams (periyodik worker, transaction gerekmez). |
| O-6 | Faz 4 olayları (GuardrailViolation, GateCheckDecision, IncidentOpened, DriftAlertTriggered, RedTeamRunCompleted) outbox üzerinden mi taşınmalı, yoksa doğrudan DB yazımı yeterli mi? | ✅ **KAPANDI** (10.08.2026): Beş olay da outbox üzerinden taşınıyor — Java'da `OutboxWriter.enqueue` (dev.geolens.queue) ve `q:governance` stream'i; handler'lar olayları `public.event_outbox`'a yazar, OutboxDispatcher Redis Stream'e iletir. DB tablo yazımı korunur (olay taşıması ek dayanıklılık katmanıdır). |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-74** | **Redis Streams + tüketici grupları** — olay taşıma altyapısı. TL 21.07.2026. | AVIP 0303 O-1 |
| **D-75** | **DLQ yeniden oynatma:** Sistem otomatik + manuel override. Audit_log kaydı zorunlu. TL 21.07.2026. | AVIP 0307 O-4 |

---

## Kaynaklar

- 0302 Domain Model — varlıklar ve durum makineleri (olay tetikleyicileri)
- 0303 Aggregates — transaction kapsamı, toplam kökleri
- 0307 Background Jobs — kuyruk yapılandırması, tüketici grupları
- 0311 Observability — olay metrikleri ve izleme
- 0204 PRD — FR/NFR bağları (FR-F1 uyarı olayı, FR-C2 zamanlanmış ölçüm)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 21 alan olayı, olay şablonu, outbox pattern, olay fırtınası çıktıları (ölçüm hattı, haftalık özet, kota), tüketim garantileri. 0302/0303'ten türetilmiştir. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-74 (Redis Streams), D-75 (DLQ). Devralınan Kararlar eklendi. |
| 1.2 | 28.07.2026 | **HT1 domain events genişletmesi:** 17 yeni olay eklendi (BC7: 4 arşiv; BC8: 2 replay; BC9: 5 SEO; BC10: 6 analiz). Toplam olay sayısı 21'den 38'e çıktı. Ölçüm hattı olay zinciri 4 paralel kolla güncellendi. Çıkarımlar güncellendi. Açık sorulara O-4 (paralel kol bağımsızlığı) ve O-5 (SEO olay mekanizması) eklendi. |
| 1.3 | 04.08.2026 | **Faz 4 ve HT2 domain events genişletmesi:** 21 yeni olay eklendi (BC11: 9 yönetişim; BC12: 7 operasyon; BC13: 5 fatura). Toplam olay sayısı 38'den 59'a çıktı. §4 olay fırtınasına 4.4 AI yönetişim ve 4.5 fatura/GİB akışları eklendi. §7 çıkarımlar güncellendi. §8 açık sorulara O-6 (Faz 4 olay taşıması) eklendi. 0302 v1.3, 0209 (Faz 4) ve 0210 (rakip kapanışı) ile senkron. |
| 1.4 | 10.08.2026 | O-6 kapatıldı: Faz 4 olayları (GuardrailViolation, GateCheckDecision, IncidentOpened, DriftAlertTriggered, RedTeamRunCompleted) outbox üzerinden taşınıyor (queue.EnqueueEvent + q:governance). §8 açık sorular güncellendi. |
| 1.5 | 15.08.2026 | **Java geçişi:** Tüketici satırı Spring `worker` profiline (q:measure + q:governance tüketicileri) güncellendi. |
