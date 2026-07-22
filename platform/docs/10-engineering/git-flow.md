# Git Akışı (Git Flow)

| Alan | Değer |
|---|---|
| Doküman ID | 10-engineering/git-flow |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 10-engineering/branching, 10-engineering/code-review, 0403 |

---

## 1. Amaç

Bu doküman GeoLens Platform git akışını tanımlar. Branch stratejisi, commit kuralları ve release süreci bu dokümanda detaylandırılır.

---

## 2. Branch Modeli

```
main ───●──────────●──────────●───── (production)
         \        / \        /
develop ──●──────●───●──────●─────── (staging)
           \    /     \    /
feature ────●──●───────●──●─────── (geliştirme)
```

| Branch | Köken | Merge Hedefi | Amaç |
|:------:|:-----:|:------------:|------|
| **main** | — | — | Production-ready kod |
| **develop** | main | main | Staging, entegrasyon |
| **feature/*** | develop | develop | Yeni özellik |
| **fix/*** | develop | develop | Hata düzeltme |
| **release/*** | develop | main + develop | Release hazırlığı |
| **hotfix/*** | main | main + develop | Acil üretim hatası |

---

## 3. Commit Kuralları

[Conventional Commits](https://www.conventionalcommits.org/) formatı kullanılır:

```
<type>(<scope>): <description>

[optional body]
[optional footer]
```

| Type | Açıklama |
|:----:|----------|
| **feat** | Yeni özellik |
| **fix** | Hata düzeltme |
| **docs** | Dokümantasyon değişikliği |
| **chore** | Bakım, yapılandırma |
| **refactor** | Kod yeniden düzenleme |
| **test** | Test ekleme/değiştirme |
| **perf** | Performans iyileştirme |

Örnek: `feat(measure): add ChatGPT engine adapter`

---

## 4. PR Süreci

```
1. Feature branch → develop
2. CI lint + test → geçmeli
3. En az 1 code review → onay
4. Merge (squash)
5. Feature branch silinir
```

---

## 5. Release Süreci

| Aşama | Aksiyon |
|:-----:|---------|
| **release cut** | release/vX.Y.Z branch'i develop'dan |
| **stabilizasyon** | Hata düzeltmeleri release branch'ine |
| **sürüm notu** | CHANGELOG.md güncelleme |
| **merge** | release → main + develop |
| **tag** | vX.Y.Z tag'i main'de |
| **deploy** | CI/CD → production |

### Release Takvimi

| Olay | Gün | Açıklama |
|:----:|:---:|----------|
| **Tren günü** | Cuma | Staging'e doğrulama sürümü. Haftasonu hata ayıklama. (AVIP D-77) |
| **Terfi** | Pazartesi | Production'a yükseltme. Staging'de doğrulanmış sürüm. (AVIP D-77) |
| **Dondurma** | Kapı haftaları + yılbaşı (2 hafta) | Bu haftalarda yalnızca PATCH sürümleri. (AVIP D-57) |

---

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-77** | **Tren günü:** Cuma tren / Pazartesi terfi. TL 21.07.2026. | AVIP 0406 O-2 |
| **D-57** | **Dondurma pencereleri:** Kapı değerlendirme haftaları + yılbaşı (2 hafta). PO+TL 21.07.2026. | AVIP 0406 O-4 |
| **D-82** | **1.0.0 = GA:** Pilot çıkış kapısı sonrası. Pilot dönemi 0.x. PO 21.07.2026. | AVIP 0406 O-1 |
| **D-17** | **Hotfix sınırları:** Migration ve güvenlik PR'ları hotfix yolundan geçemez. TL 21.07.2026. | AVIP 0401 O-4 |

---

## Kaynaklar

- 10-engineering/branching — branch adlandırma kuralları
- 10-engineering/code-review — PR ve review süreci
- 09-devops/ci-cd — CI/CD entegrasyonu
- 0403 CI/CD — AVIP release referansı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: branch modeli, commit kuralları, PR süreci, release süreci. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-77 (tren günü), D-57 (dondurma), D-82 (1.0.0=GA), D-17 (hotfix). Release takvimi eklendi. Devralınan Kararlar eklendi. |
