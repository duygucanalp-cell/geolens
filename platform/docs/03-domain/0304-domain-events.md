# 0304 · Domain Events (Alan Olayları)

| Alan | Değer |
|---|---|
| Doküman ID | 0304 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0302, 0303, 0305, 0306, 0307, 0309, 0311 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'daki tüm alan olaylarını (domain events), olay şemalarını, üretici/tüketici bağlarını ve olay fırtınası (event storming) çıktılarını tanımlar. Amaç, bağlamlar arası iletişimin olay tabanlı kısmını sabitlemek ve 0307 Background Jobs tasarımına girdi sağlamaktır.

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
| Dağıtıcı | platform/queue (periyodik polling) |
| Tüketici | cmd/worker (profil: measure/report/notify) |

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

---

## 4. Olay Fırtınası (Event Storming) Çıktısı

### 4.1 Ölçüm Hattı Olay Zinciri

```
İzleme Planı → MeasurementJobCreated → [Motor çağrıları] → MeasurementJobCompleted
                                                                     ↓
                                                            ScoreCalculated → ScoreSignificantChange
                                                                     ↓
                                                            RecommendationGenerated → [kullanıcı işaretler]
                                                                     ↓
                                                            AlertTriggered → [bildirim iletilir]
                                                                     ↓
                                                            ReportGenerated
```

### 4.2 Haftalık Özet Akışı

```
[Zamanlayıcı tetiği] → DigestGenerated → [e-posta servisi] → [kullanıcı panoya tıklar]
```

### 4.3 Kota Yönetimi Akışı

```
[Her motor çağrısı] → QuotaExceeded? → [Evet] → İş engellenir, alarm üretilir
                                     → [Hayır] → İş devam eder
```

---

## 5. Olay Şablonu (Event Schema)

Tüm olaylar aşağıdaki ortak alanları taşır:

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

1. **21 alan olayı** tanımlanmıştır. Bunlardan 12'si MVP kapsamında, 6'sı HT1'de, 3'ü HT2'de devreye girer.
2. **Ölçüm hattı olayları** en kritik olay zinciridir. MeasurementJobCreated → MeasurementJobCompleted zinciri uçtan uca izlenebilir olmalıdır (0311).
3. **Outbox pattern** tüm olay üretiminde zorunludur. Bu, olay kaybını önler ve transaction bütünlüğünü korur.
4. **Correlation_id** tüm olay zinciri boyunca taşınır. request_id → job_id → calculation_run_id zinciri log ve metriklerde izlenebilir.
5. **0307 (Background Jobs)** bu olayların kuyruk yapılandırmasını (Streams, tüketici grupları, DLQ) tanımlar.

---

## 8. Açık Sorular

| ID | Soru | Not |
|----|------|-----|
| O-1 | Per-brand partitioning MVP'de gerekli mi? | MVP'de hayır; sıra garantisi yok. HT1'de değerlendirilir. |
| O-2 | Olay şeması versiyonlama stratejisi (backward-compatible değişiklikler) | 0307 ile birlikte; öneri: yeni alan ekleme → versiyon yükseltme |
| O-3 | Benchmark olayları (HT2) şimdiden tanımlanmalı mı? | Hayır; HT2 penceresinde eklenir |

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
