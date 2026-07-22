# 0005 · Platform <> Specification Versiyon Senkronizasyon Planı

| Alan | Değer |
|---|---|
| Doküman ID | 0005 |
| Proje | GeoLens Platform + Specification |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | specification/0000 §5, specification/adr/0005, platform/docs/10-engineering/branching, platform/docs/10-engineering/git-flow, platform/docs/02-product/0206-roadmap, specification/docs/03-compliance/0305 |

---

## 1. Amaç

Bu doküman, **GeoLens Platform** ile **GeoLens Specification (GAVF)** arasındaki versiyon senkronizasyon kurallarını tanımlar. İki repo ayrı version şeması kullanır, ancak belirli olaylarda eşzamanlı güncellenmeleri gerekir.

> **Temel prensip:** Platform, Specification'ın referans uygulamasıdır. Specification'daki her versiyon değişikliği platformda karşılığını bulur. Platformdaki her yeni GAVF uygulaması Specification'a geri bildirilir.

---

## 2. İki Reponun Versiyonlama Şemaları

| Özellik | Platform | Specification |
|---------|:---------:|:-------------:|
| **Şema** | `v{major}.{minor}.{patch}` | `{major}.{minor}.{patch}` |
| **Örnek** | `v1.2.3` | `1.2.3` |
| **Major** | Kırıcı API/DB değişikliği | Kırıcı GAVF değişikliği (skor algoritması) |
| **Minor** | Yeni özellik (geri uyumlu) | Yeni skor bileşeni/katman (geri uyumlu) |
| **Patch** | Hata düzeltmesi (geri uyumlu) | Açıklama/düzeltme (skor değişmez) |
| **Git tag** | `v{major}.{minor}.{patch}` | `gavf-{major}.{minor}.{patch}` (önek: `gavf-`) |
| **Doküman** | platform/docs/10-engineering/branching.md §5 | specification/docs/adr/0005 |

> **Not:** Platform `v1.0.0` üretim sürümüne çıktığında, Specification `1.0.0` olarak yayınlanır. "v" öneki yalnızca platformdadır; Specification SemVer formatını olduğu gibi kullanır.

---

## 3. Senkronizasyon Kuralları

### Kural 1: Specification, Platform'dan Bağımsız Versiyonlanır

Specification kendi yayın döngüsüne sahiptir (Draft → RFC → Stable → Deprecated). Platform'un her sürümü Specification'ı yeni bir versiyona çıkarmaz.

### Kural 2: GAVF Etkileyen Değişiklikler Zorunlu Senkronizasyon Gerektirir

Aşağıdaki platform değişiklikleri Specification'ın güncellenmesini zorunlu kılar:

| Platform Değişikliği | Specification Etkisi | Senkronizasyon Tipi |
|----------------------|---------------------|:--------------------:|
| Skor algoritması değişikliği | GAVF major versiyon değişikliği | Kırıcı (Major) |
| Yeni skor bileşeni eklenmesi | GAVF minor versiyon değişikliği | Uyumlu (Minor) |
| Motor kademe tanımı değişikliği | Fidelite katmanı güncellemesi | Minor veya Patch |
| Örnekleme parametresi değişikliği | Metodoloji dokümanı güncellemesi | Patch |
| Alıntı şeması değişikliği | Citation framework güncellemesi | Minor |

### Kural 3: Platform Dokümantasyon Değişiklikleri Specification'a Bildirilir

Platform'daki (0401-0415) her değişiklik, Specification'ın ilgili dokümanına issue olarak kaydedilir. Her değişiklik patch/minor/major olarak sınıflandırılır ve bir sonraki yayın döngüsünde işlenir.

### Kural 4: Specification Yayını Öncesi Platform Doğrulaması

Specification'ın yeni bir versiyonu yayınlanmadan önce, platformun o versiyonu uyguladığı ve GAVF uyumluluk testlerini (0304) **platform CI pipeline'ında** veya **staging ortamında** geçtiği doğrulanmalıdır.

---

## 4. Sürüm Döngüsü Hizalaması

### Platform Sürüm Döngüsü

| Aşama | Aksiyon | Specification Bağlantısı |
|:-----:|---------|:------------------------:|
| **release cut** | release/vX.Y.Z branch'i | Specification versiyon kilidi alınır |
| **stabilizasyon** | Hata düzeltmeleri | GAVF değişikliği yoksa, spec sabit |
| **sürüm notu** | CHANGELOG güncellemesi | GAVF uyumluluk notu eklenir |
| **tag + deploy** | vX.Y.Z tag'i | Eşzamanlı spec güncellemesi varsa, o da yayınlanır |

### Specification Yayın Döngüsü

| Aşama | Aksiyon | Platform Bağlantısı |
|:-----:|---------|:--------------------:|
| **Taslak (Draft)** | İç değerlendirme | Platform PR ile eşleşebilir |
| **Yorum (RFC)** | Sektöre açık, 30 gün | Platform test ortamında uygulanır |
| **Kararlı (Stable)** | Resmi yayın | Platform üretimde kullanıma hazır |
| **Eski (Deprecated)** | 12 ay geçiş süresi | Platform migrasyon planı başlatılır |

---

## 5. Olay-Matris: Ne Zaman Hangi Repo Güncellenir?

| Olay | Platform Sürümü | Specification Sürümü | Açıklama |
|:----:|:---------------:|:--------------------:|----------|
| **MVP lansmanı** | `v1.0.0` | `1.0.0` | İlk eşzamanlı yayın |
| **HT1 özellik eklemesi** | `v1.1.0` | — (değişmez) | S4 derinleşmesi spec değişikliği gerektirmez |
| **Yeni AI motoru eklemesi** | `v1.2.0` veya `v1.2.x` | — (sadece örnek güncellemesi) | Motor kademesi spec'te örnek olarak var; güncellenebilir |
| **Skor algoritması iyileştirmesi** | `v1.3.0` | `1.1.0` (minor) | Varlık tespit metodu iyileştirmesi |
| **Örnekleme parametre değişikliği** | `v1.4.0` | `1.1.1` (patch) | n=3→4 veya temp değişikliği |
| **Yeni skor bileşeni** | `v1.2.0` | `1.2.0` (minor) | Geriye uyumlu yeni boyut |
| **Kırıcı skor değişikliği** | `v2.0.0` | `2.0.0` (major) | Ağırlıkların yeniden dağıtımı |
| **GAVF standardı revizyonu** | Platform PR | `1.1.0` (minor) | Sektör geri bildirimi sonrası |
| **Sertifikasyon lansmanı** | `v2.1.0` | `1.3.0` | Uyumluluk seviyesi güncellemesi |

### Kritik Kural

> **Aynı anda yalnızca bir major versiyon değişikliği.** Platform ve Specification aynı anda major versiyon değiştirmez. Biri major yaparsa, diğeri minor/patch ile uyum sağlar. Bu, kullanıcıların her iki tarafta da kırıcı değişiklikle karşılaşmasını önler.

---

## 6. Git Tag ve Release Koordinasyonu

### Platform Tag'leri

```
v1.0.0          → MVP lansmanı
v1.1.0          → HT1 özellikleri
v1.2.0          → HT2 özellikleri
v2.0.0          → Kırıcı değişiklik (GAVF 2.0 ile eşleşebilir)
```

### Specification Tag'leri

```
gavf-1.0.0      → GAVF 1.0 yayını (MVP ile eşzamanlı)
gavf-1.1.0      → GAVF 1.1 (yeni skor bileşeni)
gavf-2.0.0      → GAVF 2.0 (kırıcı değişiklik)
```

### GitHub Release Notları Formatı

Her release notu şu bilgiyi içermelidir:

```
## GAVF Uyumluluk
- GAVF Versiyonu: 1.0.0
- Uyumluluk Seviyesi: Temel + İleri
- Değişiklik: [Major/Minor/Patch]
- Specification Karşılığı: gavf-1.0.0
```

---

## 7. CI/CD Entegrasyonu

### Platform CI'da Specification Doğrulama

```yaml
# GAVF uyumluluk kontrolü (CI pipeline adımı)
gavf-compliance:
  stage: test
  script:
    - # specification repo klonla (örn. git clone veya actions/checkout)
    - checkout specification repo (latest stable tag)
    - run compliance test suite (0304)
    - verify all 6 GAVF principles are satisfied
    - report: "GAVF {spec_version} uyumlu - Seviye: {compliance_level}"
```

### Specification CI'da Platform Referansı

Specification CI'da (yayın öncesi) platform referans uygulamasının testlerini çalıştırmak için bir kontrol adımı eklenir:

```yaml
# Platform doğrulama (specification yayın pipeline'ı)
verify-reference-implementation:
  stage: test
  script:
    - checkout platform repo (latest develop)
    - run GAVF compliance tests
    - verify spec changes are implementable
```

---

## 8. Sürüm Geçiş Senaryoları

### Senaryo A: Platform Major, Specification Minor

```
Platform v2.0.0 (yeni API, aynı skor algoritması)
                          → Specification 1.1.0 (minor)
                          → Uyumluluk: Platform yeni API ile GAVF 1.1'i uygular
                          → Skorlar değişmez, API sözleşmesi güncellenir
```

### Senaryo B: Specification Major, Platform Major (gecikmeli)

```
Specification 2.0.0 (yeni skor algoritması)
                          → Platform v2.0.0 (6 ay sonra)
                          → Platform önce eski algoritmayı korur (GAVF 1.1)
                          → Paralel çalıştırma dönemi (30 gün)
                          → Yeni algoritma geçişi
```

### Senaryo C: Platform Minor, Specification Patch

```
Platform v1.1.0 (yeni motor, düzeltmeler)
                          → Specification 1.0.1 (patch)
                          → Motor örneği güncellenir
                          → Skor değişmez
```

---

## 9. Sorumluluk Matrisi

| Rol | Platform Sürümü | Spec Sürümü | Senkronizasyon |
|:---:|:----------------:|:-----------:|:--------------:|
| **Tech Lead** | Onay | Onay | Prosedür sahibi |
| **Product Owner** | Öncelik | Öncelik | İş kararları |
| **Backend #1 (Platform)** | Release yönetimi | — | CI pipeline |
| **Backend #2 (Geniş)** | CHANGELOG | Spec PR | Doküman senkronu |
| **Analist (AN)** | Sürüm notu | Spec changelog | Köprü dokümantasyon |

---

## 10. Açık Sorular

| ID | Soru | Not |
|:--:|------|-----|
| O-1 | Specification'daki "path" değişiklikleri (doküman içi düzeltmeler) platform patch sürümü gerektirir mi? | Öneri: Hayır, yalnızca major/minor spec değişiklikleri platform sürümünü etkiler. |
| O-2 | Platform hotfix bir GAVF ilkesini (ör. G3 fidelite etiketi) ihlal eder mi? | İhlal ederse spec major/minor değişikliği değil, platform acil düzeltmesi gerekir. |
| O-3 | Specification'ın RFC sürecinde platform hangi ortamda test etmeli? | Öneri: staging ortamı, üretim değil. |

---

## Kaynaklar

- Specification Versiyonlama: `specification/docs/00-overview/0000-master-plan.md` §5
- Specification ADR-0005: `specification/docs/adr/0005-versioning-scheme.md`
- Platform Branching: `platform/docs/10-engineering/branching.md`
- Platform Git Flow: `platform/docs/10-engineering/git-flow.md`
- Platform Roadmap: `platform/docs/02-product/0206-roadmap.md`
- GAVF Compliance Matrix: `specification/docs/03-compliance/0305-gavf-compliance-matrix.md`

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 4 senkronizasyon kuralı, olay-matrisi, tag koordinasyonu, CI/CD entegrasyonu, 3 geçiş senaryosu. |
