# ADR-014 · Java Veri Erişim Katmanı Seçimi — Karşılaştırma Analizi

| Alan | Değer |
|------|-------|
| ADR ID | ADR-014 |
| Proje | GeoLens |
| Durum | Review |
| Tarih | 14.08.2026 |
| Karar veren | TL (PO onayı bekliyor — Approved için gerekli) |
| İlişkili | ADR-003, ADR-004 (RLS), 0507-multi-tenancy, 0602-postgresql-schema, 10-engineering, `platform/java/` |

---

## Bağlam

`platform/java/` modülü, Go backend'in Java karşılığını değerlendiren Spring Boot spike'ıdır (Java 25, virtual threads, Spring Boot 3.5). Mevcut uygulama `spring-boot-starter-jdbc` (JdbcTemplate) kullanmaktadır.

Karar sorusu: **Veri erişim katmanı için en uygun araç hangisidir?** Önceki yaklaşımda "Go ile birebir aynı olmak" (Go tarafında pgx + ham SQL) bir gerekçeydi; bu ADR'de bu kısıt **bilinçli olarak kaldırılmıştır**. Amaç, uygulamanın gerçek gereksinimlerine en uygun aracı seçmektir. Bu doküman, kod değişikliği yapılmadan önce seçeneklerin maliyet/fayda analizini sunar.

---

## Mevcut Durum (Kod Envanteri)

| Bulgu | Detay |
|-------|-------|
| Bağımlılık | `spring-boot-starter-jooq` + `jooq-codegen-maven` (pom.xml) — JPA yok |
| JdbcTemplate kullanımı | **Yok** — DAO + kontrolör/service katmanlarının tamamı jOOQ/DSLContext'e taşındı (kademeli geçiş tamamlandı) |
| RLS deseni | `set_config('app.tenant_id', ?, true)` + `TransactionTemplate` — tüm veri erişiminde (`JooqScoreDao`, `JooqSentimentDao`, `JooqRecommendationDao`, `DeliveryService`, `AuditService`, `AuditLogger`, `UsageRecorder`, `QuotaChecker`, tüm kontrolörler) |
| JSONB | `?::jsonb` cast + elle `ObjectMapper` serialize/parse (`JooqScoreDao`) |
| Sorgu profili | CRUD + ağır sorgular: marka arama (count + sayfalama), panorama agregasyonu, skor yazma/okuma, digest sorguları |
| DB'siz çalışma | `AppBeans` — `ObjectProvider<DSLContext>` ile Noop/null fallback (spike DB'siz çalışabilir) |

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

## Karar

| | |
|---|---|
| **Seçenekler** | (a) JdbcTemplate (mevcut), (b) JPA/Hibernate, (c) JOOQ, (d) Spring Data JDBC |
| **Karar** | **(c) JOOQ** — DAO katmanı (`ScoreDao`, `RecommendationDao`, `SentimentDao`) JOOQ/DSLContext'e taşındı |
| **Gerekçe** | RLS güvenlik sınırı ile sıfır çakışma (saf SQL), karmaşık sorgularda en güçlü ifade gücü, derleme zamanı tip güvenliği, JSONB native destek; Go tarafındaki sqlc (ADR-003) ile aynı SQL-first felsefe |
| **Etki** | `pom.xml` (starter + codegen), 3 DAO dönüşümü, entegrasyon testleri, kontrolör/service katmanı dönüşümü (kademeli geçiş tamamlandı) |

## Uygulama Notları

1. **Codegen (DDLDatabase):** `jooq-codegen-maven` `generate-sources` fazına bağlıdır; canlı DB gerektirmez. Kaynak `src/main/resources/ddl/geolens.sql` — DAO'ların kullandığı 13 tablonun üretim migration'larından küratörlü kopyası (RLS/function/extension ifadeleri hariç; OSS jOOQ parser'ı `CREATE FUNCTION`'ı çözemez). `defaultNameCase=lower` (PostgreSQL küçük harf tanımlayıcılar). Üretilen kod `target/generated-sources/jooq` altındadır, commit edilmez.
2. **Türkçe locale tuzağı:** H2, unquoted identifier'ları Türkçe locale ile büyütünce `identity` → `İdentity` üretiyordu. `.mvn/jvm.config` ile Maven JVM'i İngilizce locale'de başlatılır (deterministik).
3. **JSONB:** DDLDatabase JSONB'yi `org.jooq.JSON` olarak üretir; yazmada `?::jsonb` cast'i (önceki JDBC SQL'iyle birebir) uygulanır.
4. **RLS:** `set_config('app.tenant_id', ?, true)` + `TransactionTemplate` deseni aynen korundu; `DSLContext` Spring transaction'ına bağlı bağlantıyı kullandığından tenant bağlamı tüm sorguları kapsar.
5. **SetTenant bind düzeltmesi:** `dsl.fetchValue(sql, Class, ...)` overload'u jOOQ'da yoktur — `String.class` bind değeri olarak bağlanıp runtime'da `SQLDialectNotSupportedException` üretiyordu; `dsl.fetch(sql, bind...)` ile değiştirildi. Bu düzeltme, entegrasyon testlerinin (17/17) gerçek PostgreSQL'de geçmesini sağlar.
6. **Doğrulama:** unit 434/434 + entegrasyon 17/17 (`mvnw test -Dsurefire.groups=integration`, Docker/Testcontainers) geçti.
7. **Kademeli geçiş (tamamlandı):** Kontrolör/service katmanındaki tüm JdbcTemplate kullanımları plain SQL üzerinden jOOQ `DSLContext`'e taşındı: `queryForList`→`dsl.fetch(...).intoMaps()`, `queryForMap`→`dsl.fetchOne(...).intoMap()`, `queryForObject`→`dsl.fetchOne(...).get(0, Class)` (`fetchValue(String, Object...)` jOOQ 3.19'da raw `Object` döndüğü için `value()` helper'ı), `update`→`dsl.execute`. Satır erişimi Map ile korunduğundan davranış birebir; unit testler `DSLContext` mock'larına çevrildi (paylaşılan `JooqTestData` test helper'ı).

## Açık Sorular

1. ~~Ekip deneyimi: JPA mı JOOQ mu?~~ → JOOQ uygulandı.
2. ~~Cache ihtiyacı: uygulama katmanında cache planlanıyor mu?~~ → DAO katmanında cache yok; JOOQ kararı bundan bağımsız.
3. ~~Sorgu/CRUD oranı~~ → Sorgu ağırlıklı olduğu uygulama sırasında doğrulandı.
4. ~~Codegen kabulü~~ → DDLDatabase canlı DB gerektirmediğinden build'e maliyeti düşüktür; kabul edildi.
5. ~~Kademeli geçiş~~ → Tamamlandı: kontrolör/service katmanı da JOOQ/DSLContext'e taşındı (v3.0).

---

## Açık Sorular

1. **Ekip deneyimi:** JPA mı JOOQ mu konusunda ekip üyelerinin tercihi/deneyimi nedir?
2. **Cache ihtiyacı:** Uygulama katmanında cache (Redis) planlanıyor mu? (Hibernate kararına doğrudan etki eder)
3. **Sorgu/CRUD oranı:** İleride beklenen iş yükü CRUD mu ağırlıklı olacak, sorgu mu?
4. **Codegen kabulü:** JOOQ codegen adımı (build'e eklenen üretim) ekip için kabul edilebilir mi?

---

## Kapanış

Karşılaştırma analizi sonrası JOOQ kararı verilmiş, DAO katmanına uygulanmış ve kademeli geçişle kontrolör/service katmanına da taşınmıştır (`platform/java/`). Durum `Review`'da kalmıştır; `Approved` için PO onayı beklenir (AGENTS.md kuralı).

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Açıklama |
|:--------:|:-----:|----------|
| 1.0 | 14.08.2026 | İlk taslak — karşılaştırma analizi (Draft) |
| 2.0 | 14.08.2026 | Karar: JOOQ — uygulama notları + setTenant bind düzeltmesi (Review) |
| 3.0 | 14.08.2026 | Kademeli geçiş tamamlandı — kontrolör/service katmanı DSLContext'e taşındı, JdbcTemplate kalmadı; test mock'ları güncellendi; doğrulama 434/434 + 17/17 (Review) |
