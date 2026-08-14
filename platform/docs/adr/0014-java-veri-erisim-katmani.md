# ADR-014 · Java Veri Erişim Katmanı Seçimi — Karşılaştırma Analizi

| Alan | Değer |
|------|-------|
| ADR ID | ADR-014 |
| Proje | GeoLens |
| Durum | Draft |
| Tarih | 14.08.2026 |
| Karar veren | TL (onay bekliyor) |
| İlişkili | ADR-003, ADR-004 (RLS), 0507-multi-tenancy, 0602-postgresql-schema, 10-engineering, `platform/java/` |

---

## Bağlam

`platform/java/` modülü, Go backend'in Java karşılığını değerlendiren Spring Boot spike'ıdır (Java 25, virtual threads, Spring Boot 3.5). Mevcut uygulama `spring-boot-starter-jdbc` (JdbcTemplate) kullanmaktadır.

Karar sorusu: **Veri erişim katmanı için en uygun araç hangisidir?** Önceki yaklaşımda "Go ile birebir aynı olmak" (Go tarafında pgx + ham SQL) bir gerekçeydi; bu ADR'de bu kısıt **bilinçli olarak kaldırılmıştır**. Amaç, uygulamanın gerçek gereksinimlerine en uygun aracı seçmektir. Bu doküman, kod değişikliği yapılmadan önce seçeneklerin maliyet/fayda analizini sunar.

---

## Mevcut Durum (Kod Envanteri)

| Bulgu | Detay |
|-------|-------|
| Bağımlılık | `spring-boot-starter-jdbc` (pom.xml) — JPA/JOOQ yok |
| JdbcTemplate kullanımı | ~17 sınıf (`*Controller`, `*Service`, `*Dao`) |
| RLS deseni | `set_config('app.tenant_id', ?, true)` + `TransactionTemplate` — ~10 sınıfta (`JdbcScoreDao`, `JdbcSentimentDao`, `JdbcRecommendationDao`, `DeliveryService`, `AuditService`, `AuditLogger`, `UsageRecorder`, `QuotaChecker`, `ConfigController`, `SentimentController`) |
| JSONB | `?::jsonb` cast + elle `ObjectMapper` serialize/parse (`JdbcScoreDao`) |
| Sorgu profili | CRUD + ağır sorgular: marka arama (count + sayfalama), panorama agregasyonu, skor yazma/okuma, digest sorguları |
| DB'siz çalışma | `AppBeans` — `ObjectProvider<JdbcTemplate>` ile Noop/null fallback (spike DB'siz çalışabilir) |

---

## Karar Kriterleri

| # | Kriter | Neden önemli? |
|:-:|--------|---------------|
| K1 | **RLS/tenant izolasyonu güvenliği** | Kiracı sızıntısı en kritik güvenlik riski (I1 değişmezi, ADR-004). Araç, `set_config` deseniyle çakışmamalı. |
| K2 | **Karmaşık sorgu ifade gücü** | Panorama agregasyonu, sayfalı arama, JSONB sorguları mevcut; araç bunları sadeleştirmeli, darboğaz olmamalı. |
| K3 | **CRUD üretkenliği** | Skor/panel/brand kayıtları gibi tekrarlı işlemlerde boilerplate azalmalı. |
| K4 | **Tip güvenliği** | Derleme zamanında SQL/şema hatalarını yakalamak regresyon riskini azaltır. |
| K5 | **JSONB desteği** | `component_values`, `engine_breakdown` kolonları doğal desteklenmeli. |
| K6 | **Test edilebilirlik** | Mevcut Mockito tabanlı unit testler (JdbcTemplate mock) korunabilmeli; testcontainers entegrasyonu kolay olmalı. |
| K7 | **Geçiş maliyeti** | Mevcut ~17 sınıfın yeniden yazılma boyutu. |
| K8 | **Bakım/öğrenme maliyeti** | Ekip Go kökenli; JPA öğrenme eğrisi vs. SQL-first araçların yakınlığı. |

---

## Seçenekler

### Seçenek A: JdbcTemplate (mevcut durum)

| | |
|---|---|
| **Tanım** | Spring JDBC ile ham SQL + elle row mapping |
| **Artılar** | Tam SQL kontrolü; RLS `set_config` deseniyle sıfır çakışma; DB'siz spike fallback'i (AppBeans) hazır; test edilebilirlik kanıtlanmış (mevcut Mockito testleri); yeni bağımlılık yok, geçiş maliyeti 0 |
| **Eksiler** | Row mapping boilerplate'i; SQL tip güvenli değil (K4); şema değişikliklerinde derleme zamanı koruması yok; karmaşık sorgularda yazım hatası riski |
| **Değerlendirme** | "Yanlış" değil ama üretkenlik ve tip güvenliği açısından en iyisi değil. K1, K2, K5, K6 güçlü; K3, K4 zayıf. |

### Seçenek B: Spring Data JPA + Hibernate 6.x

| | |
|---|---|
| **Tanım** | ORM: entity modeli, repository arayüzleri, otomatik SQL üretimi |
| **Artılar** | CRUD üretkenliği en yüksek (K3); repository soyutlaması; Java ekosisteminde standart; `@JdbcTypeCode(JSON)` ile JSONB desteği (Hibernate 6.2+) |
| **Eksiler** | **RLS riski (K1):** L2 cache açılırsa tenant sızıntısı riski — cache kapalı tutulmalı, Hibernate'in en büyük avantajlarından biri iptal; `set_config` bağlamı Hibernate'in kendi transaction/flush akışıyla senkronize edilmeli (bağlantı alma veya transaction başlangıcında SET — ek wiring); lazy loading + virtual threads (N+1, LazyInitializationException) tuzakları; karmaşık/agregasyon sorguları yine native query'ye düşer — ORM yükü taşınırken SQL avantajı kaybolur (K2); ekip öğrenme eğrisi en dik (K8); mevcut ~17 sınıf entity modeline çevrilmeli (K7) |
| **Değerlendirme** | CRUD ağırlıklı olsaydı güçlü adaydı; ancak bu uygulamada sorgu + güvenlik sınırı öncelikli. RLS + cache kısıtı Hibernate'in faydasını ciddi azaltır. |

### Seçenek C: JOOQ

| | |
|---|---|
| **Tanım** | Type-safe SQL DSL; şemadan kod üretimi (codegen); SQL-first |
| **Artılar** | Derleme zamanı SQL/şema doğrulaması (K4); karmaşık sorgular ve agregasyonlar için en güçlü ifade gücü (K2); RLS ile sıfır çakışma — hâlâ SQL (K1); PostgreSQL/JSONB native desteği (K5); Go tarafındaki sqlc (ADR-003 karar 4) ile aynı felsefe — SQL-first, tip güvenli; CRUD'da `DSL.insertInto/selectFrom` ile orta düzey üretkenlik (K3) |
| **Eksiler** | Build'e codegen adımı eklenir; DSL öğrenme eğrisi (Go ekibi için SQL'e yakın olduğundan JPA'dan düşük); mevcut sorguların JOOQ DSL'ine çevrilmesi gerekir (K7); entity yok — DTO/record mapping yine elle (JdbcTemplate'ten hafif, ama JPA kadar otomatik değil) |
| **Değerlendirme** | Bu uygulama profiline (RLS güvenlik sınırı + sorgu ağırlıklı + JSONB) en uygun araç. K1, K2, K4, K5 güçlü; K3 orta. |

### Seçenek D: Spring Data JDBC

| | |
|---|---|
| **Tanım** | JPA'sız Spring Data: aggregate-root modeli, SQL'e birebir mapping, lazy loading yok |
| **Artılar** | JPA'dan hafif ve öngörülebilir; lazy loading tuzağı yok; RLS dostu (SQL-first) (K1); repository soyutlamasıyla CRUD boilerplate azalır (K3); JdbcTemplate'ten daha az mapping kodu |
| **Eksiler** | Karmaşık join/agregasyon desteği sınırlı — o sorgular yine `@Query`/elle SQL'e döner (K2); şema doğrulaması JOOQ kadar güçlü değil (K4); JSONB için özel converter gerekir (K5); ekosistemi JPA/JOOQ kadar zengin değil |
| **Değerlendirme** | "Orta yol": JdbcTemplate'ten üretken, Hibernate'ten güvenli. K2 (ağır sorgular) ve K4 bu projede kritik olduğundan JOOQ'un gerisinde kalır. |

---

## Karşılaştırma Tablosu

| Kriter | A: JdbcTemplate | B: JPA/Hibernate | C: JOOQ | D: Spring Data JDBC |
|--------|:---:|:---:|:---:|:---:|
| K1 RLS güvenliği | ✅ | ⚠️ (L2 cache kapalı şartı) | ✅ | ✅ |
| K2 Karmaşık sorgu | ✅ | ❌ (native query'e düşer) | ✅ (en güçlü) | 🟡 |
| K3 CRUD üretkenliği | ❌ | ✅ (en yüksek) | 🟡 | ✅ |
| K4 Tip güvenliği | ❌ | 🟡 | ✅ | 🟡 |
| K5 JSONB | ✅ | 🟡 | ✅ (native) | 🟡 |
| K6 Test edilebilirlik | ✅ | 🟡 | ✅ | ✅ |
| K7 Geçiş maliyeti | 0 | Yüksek | Orta | Orta |
| K8 Öğrenme eğrisi | Düşük | En dik | Orta (SQL'e yakın) | Düşük-orta |

---

## RLS Özel Analizi (K1)

RLS, `set_config('app.tenant_id', ?, true)` ile **transaction-scoped** çalışır: `TransactionTemplate` içinde önce SET, sonra işlem. Bu desen şu koşulları dayatır:

1. **Her veri erişimi bir transaction içinde olmalı** — bağlantı havuzundan alınan her bağlantıda tenant bağlamı ya SET ile ya da bağlantı alma anında kurulmalı.
2. **Uygulama katmanında cache olmamalı** (veya tenant-aware olmalı) — aksi halde A tenantının verisi B tenantına servis edilebilir (I1 değişmezi ihlali).

- **JdbcTemplate / JOOQ / Spring Data JDBC**: saf SQL — `TransactionTemplate` içinde mevcut `set_config` deseni aynen çalışır, ek mekanizma gerekmez.
- **Hibernate**: kendi transaction/flush yaşam döngüsüne sahip; `set_config`'i Hibernate'in SQL üretimiyle senkronize etmek için ek wiring (ör. bağlantı edinme anında SET — `ConnectionProvider` sarmalayıcı veya transaction interceptor) gerekir. Ayrıca L2 cache kalıcı olarak kapalı tutulmalıdır.

Bu analiz, Hibernate'in bu projede "en iyi" olmasını zorlaştıran birincil teknik nedendir.

---

## Öneri

**JOOQ (Seçenek C)** — uygulama profiline (RLS güvenlik sınırı, sorgu ağırlıklı, JSONB, Go tarafında zaten sqlc ile SQL-first felsefesi) en uygun araçtır. JdbcTemplate'in SQL kontrolünü korur, üzerine derleme zamanı tip güvenliği ve daha az boilerplate ekler.

İkincil alternatifler:
- **JdbcTemplate'te kalmak**: geçiş maliyeti 0; üretkenlik ve tip güvenliği kaybı kabul edilirse makul.
- **Spring Data JDBC**: JPA'ya kıyasla hafif bir repository katmanı istenirse; ağır sorgular yine elle SQL'e döneceğinden JOOQ'un gerisinde.
- **Hibernate**: yalnızca ekibin JPA'da güçlü deneyimi varsa ve CRUD ağırlığı ileride artarsa tekrar değerlendirilmeli; L2 cache kısıtı ve RLS wiring'i göz önünde bulundurulmalı.

> **Not:** Bu bölüm bir **öneridir**, karar değildir. Karar, TL/PO onayı sonrası bu ADR'nin "Kararlar" bölümüne işlenecek ve Durum `Approved`'a alınacaktır.

---

## Açık Sorular

1. **Ekip deneyimi:** JPA mı JOOQ mu konusunda ekip üyelerinin tercihi/deneyimi nedir?
2. **Cache ihtiyacı:** Uygulama katmanında cache (Redis) planlanıyor mu? (Hibernate kararına doğrudan etki eder)
3. **Sorgu/CRUD oranı:** İleride beklenen iş yükü CRUD mu ağırlıklı olacak, sorgu mu?
4. **Codegen kabulü:** JOOQ codegen adımı (build'e eklenen üretim) ekip için kabul edilebilir mi?

---

## Kapanış

Bu doküman, kod değişikliği yapılmadan önce veri erişim katmanı seçeneklerinin maliyet/fayda analizini sunar. Karar verildiğinde bu ADR'ye "Kararlar" bölümü eklenecek ve uygulama planı (`platform/java/`) ona göre güncellenecektir.

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Açıklama |
|:--------:|:-----:|----------|
| 1.0 | 14.08.2026 | İlk taslak — karşılaştırma analizi (Draft) |
