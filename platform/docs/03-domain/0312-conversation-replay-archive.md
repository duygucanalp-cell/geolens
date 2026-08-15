# 0312 · Conversation Replay & Response Archive

| Alan | Değer |
|---|---|
| Doküman ID | 0312 |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 04 Ağustos 2026 |
| İlişkili | 0302, 0304, 0307, 0308, 0309, 0501, 0506, 0601, 0605, 0204, 0205, 0207, dev.geolens.replay, dev.geolens.archive, **docs/AI_Visibility_Generative_Search_Intelligence_Platform.md** |

---

## 1. Amaç

Bu doküman, AI motor yanıtlarının **anlık görüntü olarak saklanması (Conversation Replay)** ve **tarihsel olarak arşivlenmesi (Response Archive)** özelliklerinin mimari tasarımını tanımlar.

Turkcell RFP'deki aşağıdaki gereksinimleri karşılar:

| RFP Gereksinimi | FR Karşılığı (0204) |
|:----------------|:-------------------:|
| "ChatGPT ne cevap verdi? / Gemini ne cevap verdi?" — ekran görüntüsü gibi tutulabilmeli | FR-D12 |
| Geçmiş AI cevaplarının saklanabilmesi | FR-D13 |
| Tarihsel karşılaştırma yapılabilmesi | FR-D13 |
| AI cevap değişimlerinin izlenebilmesi | FR-D13 |

---

## 2. Kavramlar

### 2.1 Conversation Replay

Conversation Replay, bir AI motoruna gönderilen prompt'un ve alınan yanıtın **anlık görüntüsünü** (snapshot) saklama ve sonradan **birebir aynı şekilde görüntüleme** özelliğidir.

| Özellik | Açıklama |
|---------|----------|
| **Amaç** | AI yanıtının o anki halini kanıt olarak saklamak, ekibin aynı yanıtı görmesini sağlamak |
| **Kapsam** | Her motor çağrısı (probe) için bir snapshot |
| **İçerik** | Prompt metni, motor yanıtı (tam metin), alıntı listesi, motor adı, zaman damgası, fidelite etiketi |
| **Görüntüleme** | Dashboard'da "Replay" butonu ile orijinal yanıtı birebir gösterir |

### 2.2 Response Archive

Response Archive, tüm AI yanıtlarının **versiyonlu, aranabilir, tarihsel arşividir**. Aynı prompt'un farklı zamanlardaki yanıtlarını karşılaştırmaya olanak tanır.

| Özellik | Açıklama |
|---------|----------|
| **Amaç** | AI yanıtlarının zaman içinde nasıl değiştiğini izlemek, trend analizine girdi sağlamak |
| **Kapsam** | Tüm motor yanıtları, süresiz (saklama politikasına tabi) |
| **İçerik** | Conversation Replay verisi + karşılaştırma metaverisi |
| **Görüntüleme** | Dashboard'da Archive görünümü, tarihsel karşılaştırma (diff) arayüzü |

### 2.3 AI Cevap Değişimi İzleme

Aynı prompt setinin farklı zamanlardaki yanıtlarını karşılaştırarak AI motorunun yanıt değişimlerini tespit eder.

| Karşılaştırma Türü | Açıklama |
|:------------------:|----------|
| **İçerik değişimi** | Yanıt metninde eklenen/çıkarılan/değişen bölümler |
| **Alıntı değişimi** | Kaynak URL'lerinde eklenen/kaybolan değişiklikler |
| **Sıralama değişimi** | Marka/rakip sıralamasındaki değişim |
| **Sentiment değişimi** | Duygu durumundaki değişim |

---

## 3. Mimari

### 3.1 Yüksek Seviye Mimari

```
┌─────────────────────────────────────────────────────┐
│                   Ölçüm Hattı                       │
│  Worker → Engine → Ham Yanıt → Skor                │
│                         ↓                           │
│              Conversation Replay                    │
│              (S3 + PostgreSQL)                      │
│                         ↓                           │
│              Response Archive                       │
│              (S3 + PostgreSQL + Elasticsearch)      │
└─────────────────────────────────────────────────────┘
         ↓                           ↓
   Dashboard Replay           Archive Görünümü
   (FR-D12)                   (FR-D13)
```

### 3.2 Bileşenler

| Bileşen | Sorumluluk | Teknoloji |
|---------|-----------|-----------|
| **Snapshot Service** | Conversation Replay kaydı oluşturma, okuma, silme | `dev.geolens.replay` |
| **Archive Service** | Response Archive yönetimi, arama, karşılaştırma | `dev.geolens.archive` |
| **Diff Engine** | İki yanıt sürümü arasındaki farkı hesaplama | `dev.geolens.replay` (Diff) |
| **Replay Storage** | Snapshot'ların S3'te saklanması | S3 + PostgreSQL meta |
| **Archive Index** | Arşiv verilerinin aranabilir indeksi | Elasticsearch (HT1+) / PostgreSQL (MVP) |

### 3.3 Veri Akışı

```
Adım 1: Ölçüm tamamlandığında
──────────────────────────────
Worker → MeasurementJobCompleted olayı
         ↓
    Snapshot Service tetiklenir
         ↓
    Ham yanıt (raw_response_id) + prompt + motor bilgisi
    → ConversationSnapshot oluşturulur
    → S3'e yazılır (JSON + HTML snapshot)
    → PostgreSQL metadata yazılır
         ↓
    Archive Service tetiklenir
         ↓
    ResponseArchiveEntry oluşturulur
    → Bir önceki sürümle karşılaştırma yapılır
    → Değişim varsa archive_event üretilir

Adım 2: Kullanıcı replay görüntülediğinde
──────────────────────────────────────────
Dashboard → GET /v1/replay/{replay_id}
         ↓
    Snapshot Service → S3'ten okuma → Dashboard'a döner
         ↓
    Kullanıcı orijinal yanıtı birebir görür

Adım 3: Kullanıcı archive karşılaştırması yaptığında
──────────────────────────────────────────────────────
Dashboard → GET /v1/archive/compare?prompt_set_id=X&engine=chatgpt
         ↓
    Archive Service → iki sürümü okur → Diff Engine çalıştırır
         ↓
    Değişim raporu (diff) Dashboard'a döner
```

---

## 4. Veri Modeli

### 4.1 ConversationSnapshot (Varlık)

| Alan | Tip | Zorunlu | Açıklama |
|------|:---:|:-------:|----------|
| replay_id | ULID | ✅ | Benzersiz snapshot kimliği |
| measurement_job_id | ULID | ✅ | Bağlı ölçüm işi |
| raw_response_id | ULID | ✅ | Bağlı ham yanıt |
| workspace_id | ULID | ✅ | Çalışma alanı |
| engine_name | string | ✅ | Motor adı (chatgpt, gemini, perplexity) |
| prompt_text | text | ✅ | Gönderilen prompt metni |
| response_content | text | ✅ | Motor yanıtı (tam metin) |
| response_snapshot_s3_key | string | ✅ | S3'teki snapshot dosya yolu |
| citations | jsonb | ✅ | Alıntı listesi [{url, title, position}] |
| fidelity_label | string | ✅ | Kademe etiketi (T1/T2/T3) |
| sentiment_score | float | — | Opsiyonel sentiment skoru (FR-D7) |
| created_at | timestamp | ✅ | Kayıt zamanı (UTC) |
| content_hash | string | ✅ | SHA-256 içerik karması (bütünlük) |
| tenant_id | string | ✅ | Kiracı bağlamı (RLS için) |

### 4.2 ResponseArchiveEntry (Varlık)

| Alan | Tip | Zorunlu | Açıklama |
|------|:---:|:-------:|----------|
| archive_id | ULID | ✅ | Benzersiz arşiv kaydı kimliği |
| prompt_set_id | ULID | ✅ | Bağlı prompt seti |
| prompt_text | text | ✅ | Prompt metni |
| engine_name | string | ✅ | Motor adı |
| version | int | ✅ | Sürüm numarası (artan) |
| replay_id | ULID | ✅ | ConversationSnapshot referansı |
| previous_version_id | ULID | — | Önceki sürüm referansı (varsa) |
| diff_summary | jsonb | — | Değişim özeti {changed_words, added_citations, removed_citations} |
| has_content_change | bool | ✅ | İçerik değişimi var mı? |
| has_citation_change | bool | ✅ | Alıntı değişimi var mı? |
| created_at | timestamp | ✅ | Arşiv zamanı (UTC) |
| tenant_id | string | ✅ | Kiracı bağlamı (RLS için) |

### 4.3 ResponseArchiveEntry İlişkileri

```
PromptSet (0302)
    │
    1-N
    │
ResponseArchiveEntry ──1-1── ConversationSnapshot
    │
    0-1
    │
ResponseArchiveEntry (previous_version)
```

### 4.4 S3 Depolama Yapısı

```
s3://{bucket}/
  replay/
    {tenant_id}/
      {year}/
        {month}/
          {day}/
            {replay_id}.json        # ConversationSnapshot ham verisi
            {replay_id}.html        # HTML render snapshot (opsiyonel)
  archive/
    {tenant_id}/
      {prompt_set_id}/
        {engine_name}/
          v1.json                   # Sürüm 1 ham yanıt
          v2.json                   # Sürüm 2 ham yanıt
          v3.json                   # Sürüm 3 ...
```

---

## 5. Depolama ve Saklama Politikaları

0605 Data Retention ile uyumlu:

| Veri Türü | S3 STANDARD | S3 GLACIER | Sil | Toplam |
|:---------:|:-----------:|:----------:|:---:|:------:|
| ConversationSnapshot (ham) | 0-30 gün | 31-90 gün | 90+ gün | 90 gün |
| ConversationSnapshot (meta) | — | — | 90+ gün | 90 gün (PG) |
| ResponseArchiveEntry | — | — | 1 yıl | 1 yıl (PG) |
| Diff özeti | — | — | 1 yıl | 1 yıl (PG) |

> **KVKK/GDPR uyumu:** Conversation Replay verileri kişisel veri içerebilir (kullanıcı sorgusu). Bu nedenle:
> - Kullanıcı onayı ile saklanır (NFR-12)
> - KVKK silme talebinde kripto-silme (zarf anahtarı imhası) uygulanır
> - Anonimleştirme mümkün değilse tamamen silinir

---

## 6. API Tasarımı

### 6.1 Replay API (FR-D12)

```
GET    /v1/replay/{replay_id}              — ConversationSnapshot detayı
GET    /v1/replay/{replay_id}/snapshot     — S3 snapshot içeriğini döndürür
GET    /v1/replay?measurement_job_id=X     — Ölçüm işine ait tüm replay'leri listeler
DELETE /v1/replay/{replay_id}              — Snapshot silme (admin)
```

**GET /v1/replay/{replay_id} yanıtı:**

```json
{
  "replay_id": "01J...",
  "engine": "chatgpt",
  "prompt": "{brand_name} hakkında ne biliyorsun?",
  "response": "Acme şirketi 2005 yılında kurulmuş bir teknoloji firmasıdır...",
  "citations": [
    {"url": "https://example.com/acme", "title": "Acme Hakkında", "position": 1}
  ],
  "fidelity_label": "T2:official_proxy",
  "sentiment_score": 0.85,
  "created_at": "2026-07-27T12:00:00Z"
}
```

### 6.2 Archive API (FR-D13)

```
GET    /v1/archive/entries                 — Arşiv kayıtlarını listeler (filtre: prompt_set_id, engine, tarih aralığı)
GET    /v1/archive/entries/{archive_id}    — Tekil arşiv kaydı detayı
GET    /v1/archive/compare                 — İki sürüm karşılaştırması (query: prompt_set_id, engine, v1, v2)
GET    /v1/archive/versions                — Bir prompt+engine için tüm sürümleri listeler
GET    /v1/archive/changelog               — AI cevap değişim günlüğü (zaman sıralı)
```

**GET /v1/archive/compare yanıtı:**

```json
{
  "prompt": "{brand_name} hakkında ne biliyorsun?",
  "engine": "chatgpt",
  "v1": {
    "archive_id": "01J...",
    "version": 1,
    "created_at": "2026-07-20T12:00:00Z"
  },
  "v2": {
    "archive_id": "01K...",
    "version": 2,
    "created_at": "2026-07-27T12:00:00Z"
  },
  "diff": {
    "content_changes": {
      "added": ["Acme yeni ürün lansmanı yaptı."],
      "removed": ["Acme eski ürün hattını kapatıyor."],
      "modified": ["Pazar payı %25 → %28"]
    },
    "citation_changes": {
      "added": [{"url": "https://new-source.com/acme-new"}],
      "removed": [{"url": "https://old-source.com/acme-old"}],
      "unchanged": [{"url": "https://example.com/acme"}]
    },
    "sentiment_change": {
      "v1_score": 0.72,
      "v2_score": 0.85,
      "direction": "positive"
    },
    "summary": "2 alıntı eklendi, 1 alıntı çıkarıldı, sentiment olumluya döndü"
  }
}
```

---

## 7. Domain Events

| Olay | Üretici | Tüketici | Tetikleyici |
|------|---------|----------|-------------|
| **ConversationSnapshotCreated** | measure worker | archive service | Ham yanıt alındı, snapshot oluşturuldu |
| **ResponseArchived** | archive service | delivery (digest) | Yeni arşiv kaydı oluşturuldu |
| **ResponseContentChanged** | archive service | delivery (alert) | AI cevabı önceki sürüme göre değişti |
| **ResponseCitationChanged** | archive service | delivery (alert) | Alıntı listesi değişti |
| **ResponseSentimentChanged** | archive service | delivery (alert) | Sentiment skoru anlamlı değişti |
| **ArchiveEntryDeleted** | archive service | governance (audit log) | KVKK silme talebi sonucu kayıt silindi |

### 7.1 Olay Taşıma Mekanizması

Tüm olaylar, 0304'te tanımlanan **transactional outbox pattern** ile taşınır:

1. Olay üretimi → `public.event_outbox` tablosuna yazılır (aynı PG işleminde)
2. Outbox dağıtıcısı (dev.geolens.queue.OutboxDispatcher) → pending kayıtları okur (SKIP LOCKED)
3. Redis Streams kuyruğuna yazar (`q:replay`, `q:archive`)
4. Tüketici (worker) kuyruktan okur ve işler
5. `dispatched` işaretlenir

Her olay, 0304'te tanımlanan ortak olay şablonunu kullanır:

```json

```json
{
  "event_id": "ulid",
  "event_type": "ResponseContentChanged",
  "event_version": 1,
  "producer": "archive-service",
  "timestamp": "2026-07-27T12:00:00Z",
  "correlation_id": "measurement-job-chain-id",
  "tenant_id": "tenant-ulid",
  "data": {
    "archive_id": "01K...",
    "prompt_set_id": "01J...",
    "engine": "chatgpt",
    "previous_version": 1,
    "current_version": 2,
    "change_type": "content_and_citations",
    "diff_summary": "2 alıntı eklendi, 1 alıntı çıkarıldı"
  }
}
```

---

## 8. Alerting Entegrasyonu

Response Archive, 0415'te tanımlanan alerting sistemiyle aşağıdaki uyarılara kaynak sağlar:

| Uyarı Türü | Tetikleyici | Eşik |
|:----------:|-------------|:----:|
| **Citation Kaybı** | Önceki sürümde var olan alıntının yeni sürümde kaybolması | ≥1 kaynak kaybı |
| **AI Cevap Değişimi** | Aynı prompt'a AI'ın farklı yanıt üretmesi | Her değişim |
| **Sentiment Değişimi** | Sentiment skorunda anlamlı düşüş | ≥0.2 puan düşüş |
| **Sıralama Değişimi** | Markanın AI yanıtındaki sıralaması değişirse | Sıra değişimi |

---

## 9. Ön Yüz Entegrasyonu

### 9.1 Conversation Replay UI

Dashboard'daki skor kartında her motor sonucunun yanında bir **"▶ Replay"** butonu bulunur:

```
[Skor Kartı: ChatGPT - 72.3]
  ┌─────────────────────────────────────┐
  │ "Acme hakkında ne biliyorsun?"      │
  │                                     │
  │ Acme, 2005 yılında kurulmuş...      │
  │                                     │
  │ Kaynaklar: example.com, acme.com    │
  │                                     │
  │ [▶ Replay] [Arşivde Gör]           │
  └─────────────────────────────────────┘
```

Replay tıklandığında, aynı prompt ve yanıt **o anki haliyle** modal/panel içinde görüntülenir.

### 9.2 Response Archive UI

Archive görünümü aşağıdaki bileşenleri içerir:

| Bileşen | Açıklama |
|---------|----------|
| **Zaman Çizelgesi** | Aynı prompt'un zaman içindeki yanıt sürümlerini kronolojik gösterir |
| **Diff Görünümü** | İki sürüm arasındaki farkları renk kodlu gösterir (ekleme: yeşil, çıkarma: kırmızı) |
| **Alıntı Karşılaştırması** | Kaynak URL'lerindeki eklenen/kaybolan değişiklikleri listeler |
| **Değişim Günlüğü** | Tüm değişikliklerin kronolojik günlüğü, filtrelenebilir |
| **Bildirim Geçmişi** | Archive değişimleriyle tetiklenen uyarıların geçmişi |

---

## 10. Güvenlik ve KVKK/GDPR Uyumu

| Gereksinim | Mekanizma |
|-----------|-----------|
| **Kiracı izolasyonu** | Tüm replay/archive verileri RLS ile korunur (tenant_id) |
| **RBAC** | Replay görüntüleme: viewer+; Silme: admin |
| **KVKK silme** | Kripto-silme: zarf anahtarı imha edilir, S3 verisi erişilemez olur |
| **Denetim izi** | Tüm replay/archive işlemleri audit_log'a yazılır (NFR-6) |
| **Veri bütünlüğü** | content_hash (SHA-256) ile doğrulama — snapshot değiştirilemez |
| **Saklama süresi** | 0605 ile uyumlu — 90 gün snapshot, 1 yıl archive |
| **Kullanıcı onayı** | Conversation Replay özelliği kullanıcı tarafından açılıp kapatılabilir |
| **Anonimleştirme** | KVKK talebinde prompt metni anonimleştirilir (marka adı maskelenir) |

---

## 11. Migration Planı

### 11.1 Veritabanı Migration'ları

> **Kod gerçeği (v1.1, 04.08.2026):** Replay/Archive şemaları **`038_conversation_replay.sql`** migration'ı ile yayındadır (Faz 4). Aşağıdaki ilk tasarım planı (037/038 dosya adları ve detaylı kolon seti) hayata geçirilmemiştir; shipped migration sadeleştirilmiş bir şemaya sahiptir.

Shipped migration `038_conversation_replay.sql`:

```sql
-- 038_conversation_replay.sql
CREATE SCHEMA IF NOT EXISTS replay;
CREATE SCHEMA IF NOT EXISTS archive;

CREATE TABLE replay.conversation_snapshots (
    id                 TEXT PRIMARY KEY,            -- ULID
    brand_id           TEXT NOT NULL REFERENCES config.brands(id),
    prompt_text        TEXT NOT NULL,
    engine_name        TEXT NOT NULL,
    response_preview   TEXT NOT NULL,               -- İlk 500 karakter
    response_full      TEXT,                        -- Tam yanıt (opsiyonel)
    content_hash       TEXT NOT NULL,               -- SHA-256
    s3_ref             TEXT,                        -- S3 tam metin referansı
    measurement_job_id TEXT REFERENCES measure.measurement_jobs(id),
    raw_response_id    TEXT,
    tenant_id          TEXT NOT NULL,
    workspace_id       TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- RLS: tenant_isolation (tenant_id) + indexler (brand_id, tenant_id, content_hash)

CREATE TABLE archive.response_entries (
    id                TEXT PRIMARY KEY,             -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    engine_name       TEXT NOT NULL,
    prompt_text       TEXT NOT NULL DEFAULT '',
    response_preview  TEXT NOT NULL,                -- İlk 1000 karakter
    response_full     TEXT NOT NULL,
    version           INT NOT NULL DEFAULT 1,
    content_hash      TEXT NOT NULL,
    s3_ref            TEXT,
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(brand_id, engine_name, version)
);
-- RLS: tenant_isolation (tenant_id) + indexler (brand_id/engine/version, tenant_id)
```

> **Tasarım-uygulama farkı:** Bu dokümanın §4.1/§4.2 veri modelindeki `replay_id` (→ `id`), `response_snapshot_s3_key` (→ `s3_ref`), `prompt_set_id` (→ `brand_id`), `citations`, `fidelity_label`, `sentiment_score`, `diff_summary`, `has_content_change/has_citation_change`, `previous_version_id` alanları ilk migration'da yer almaz. Diff (`dev.geolens.replay` DiffResult: has_changed + changes) iki snapshot okunarak talep üzerine hesaplanır. `018_archive.sql` (workspace/brand `archived_at`) bu özellikle ilgisizdir; `037_sentiment_hallucination.sql` sentiment/hallüsinasyon şemasıdır.

### 11.2 Worker Profili Genişletmesi

Mevcut **measure** worker profiline aşağıdaki adımlar eklenir:

| Adım | Sorumlu | Açıklama |
|:----:|---------|----------|
| 5b | measure worker | Ham yanıt alındıktan hemen sonra ConversationSnapshot oluşturulur |
| 5c | measure worker | S3'e snapshot yazılır (JSON formatında) |
| 5d | measure worker | ResponseArchiveEntry oluşturulur, önceki sürümle karşılaştırılır |
| 5e | measure worker | Değişim varsa ResponseContentChanged / ResponseCitationChanged olayı üretilir |

Bu adımlar, 0501 §4'te tanımlanan 9 adımlı ölçüm hattını 12 adıma genişletir. Orijinal adım 7 (hesaplama) → adım 10, orijinal adım 8 (skor) → adım 11, orijinal adım 9 (korelasyon) → adım 12 olarak kayar:

```
Güncellenmiş Ölçüm Hattı:

1.  Tetikleme           — scheduler
2.  İş üretimi          — scheduler
3.  Outbox dağıtımı     — dev.geolens.queue.OutboxDispatcher
4.  Kuyruktan okuma     — worker
5.  Motor çağrısı       — engines
6.  Ham yanıt saklama   — measure (S3 + meta)
7.  **Snapshot oluşturma** — **replay service (YENİ)**
8.  **Archive kaydı**       — **archive service (YENİ)**
9.  **Değişim tespiti**     — **archive/diff (YENİ)**
10. Hesaplama           — measure/calc (orijinal adım 7)
11. Skor üretimi        — measure/calc (orijinal adım 8)
12. Korelasyon          — Tümü (orijinal adım 9)
```

---

## 12. GeoLens İçin Çıkarımlar

1. **FR-D12 ve FR-D13**, Turkcell RFP'nin Conversation Replay ve Response Archive gereksinimlerini karşılar. Bu özellikler, kurumsal müşteriler için "AI yanıtlarını geriye dönük kanıtlama" ihtiyacını giderir.
2. **Mimari etki:** Ölçüm hattına 3 yeni adım eklenir (snapshot, archive, diff). Worker profili güncellenir. Mevcut 9 adım 12 adıma çıkar.
3. **Veri modeli etkisi:** İki yeni schema (`replay`, `archive`) ve iki yeni tablo eklenir. Mevcut `raw_responses` tablosuyla ilişkilendirilir.
4. **Saklama maliyeti:** Conversation Replay verileri (JSON snapshot) boyutu tahmini 5-20 KB/kayıt. 10 kiracı × 1000 ölçüm/ay = ~100 MB/ay S3 depolama. Archive metadata PostgreSQL'de ihmal edilebilir boyuttadır.
5. **KVKK/GDPR uyumu:** Snapshot verileri kullanıcı sorgusu içerebilir. Kripto-silme mekanizması 0310 ve 0605 ile uyumlu çalışır.
6. **Specification bağlantısı:** Conversation Replay formatı, GAVF Yanıt Standardı (S2) ile uyumlu olmalıdır. Snapshot şeması, specification reposundaki yanıt standardına referansla doğrulanır.

---

## 13. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Conversation Replay varsayılan olarak açık mı olmalı? Kullanıcı tarafından kapatılabilmeli mi? | ⏳ Gizlilik ve ürün kararı — PO onayı gerekli |
| O-2 | HTML snapshot (birebir UI kopyası) MVP'de gerekli mi, yoksa JSON yeterli mi? | ⏳ MVP'de JSON yeterli. HTML snapshot HT1 adayı. |
| O-3 | Archive arama indeksi için MVP'de Elasticsearch gerekli mi? | ⏳ MVP'de PostgreSQL LIKE/tsvector yeterli. Elasticsearch HT1'de. |
| O-4 | ResponseArchiveEntry versiyon sınırı var mı? (Maksimum kaç sürüm saklanmalı?) | ⏳ 1 yıl saklama süresi yeterli. Sürüm sayısı ölçüm frekansına bağlı. |
| O-5 | Diff Engine hangi kütüphane ile implemente edilmeli? | ⏳ go-diff (sergi/go-diff) MVP için yeterli. |

---

## Kaynaklar

- **Turkcell AI Visibility Platform RFP:** `docs/AI_Visibility_Generative_Search_Intelligence_Platform.md`
- 0204 PRD — FR-D12 (conversation replay), FR-D13 (response archive)
- 0205 MVP — MVP kapsamı, conversation replay tam kapsamla içeride
- 0207 Feature Catalog — FR-D12, FR-D13 özellik tanımları
- 0302 Domain Model — measurement_job, raw_response varlıkları
- 0304 Domain Events — olay şablonu, outbox pattern
- 0307 Background Jobs — worker tasarımı, DLQ
- 0308 AI Connectors — motor bağdaştırıcıları
- 0309 Scoring Engine — skor hesaplama
- 0501 System Architecture — ölçüm hattı
- 0506 Worker Design — worker profilleri
- 0601 Data Model — veri kategorileri
- 0605 Data Retention — saklama politikaları
- 0310 Security — KVKK/GDPR, kripto-silme
- 0415 AI Observability — alerting entegrasyonu

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 27.07.2026 | İlk yayın: Conversation Replay (FR-D12) ve Response Archive (FR-D13) mimari tasarımı. Kavramlar, veri modeli, API tasarımı, domain events, alerting entegrasyonu, migration planı, güvenlik ve KVKK/GDPR uyumu. Turkcell RFP gereksinimlerini karşılar. |
| 1.1 | 04.08.2026 | **Kod gerçeği senkronu:** §7.1 olay taşıma şeması `governance.event_outbox` → `public.event_outbox` düzeltildi. §11.1 migration planı shipped `038_conversation_replay.sql` ile yenilendi (replay+archive şemaları; sadeleştirilmiş kolon seti). Tasarım-uygulama farkı notu eklendi: §4.1/§4.2'deki detaylı kolonların ilk migration'da olmadığı, diff'in talep üzerine `internal/replay.DiffResult` ile hesaplandığı belirtildi. `018_archive.sql`'in ilgisiz olduğu netleştirildi. |
| 1.2 | 15.08.2026 | **Java geçişi:** Outbox dağıtıcı referansları `dev.geolens.queue.OutboxDispatcher` ile güncellendi. |
