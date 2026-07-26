# 0209 · Ürün Beklentisi (Backlog)

| Alan | Değer |
|---|---|
| Doküman ID | 0209 |
| Proje | GeoLens Platform |
| Versiyon | 4.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 26 Temmuz 2026 |
| İlişkili | 0205, 0206, 0207, ADR-001–012 |

---

## 1. Amaç

Bu doküman, **tüm MVP + HT1 + HT2 + Teknik Borç + Kurumsal maddelerinin tamamlandığı** noktayı gösterir.
Toplam **43 madde** (M1–M12, H1–H12, T1–T5, X1–X10, K1–K4) kodlanmış, build ve testlerden geçmiştir.

---

## 2. Son Durum

| Grup | Toplam | Tamam | Kalan |
|:----:|:------:|:-----:|:-----:|
| **MVP (M1–M12)** | 12 | 12 | 0 |
| **HT1 (H1–H12)** | 12 | 12 | 0 |
| **HT2 (T1–T5)** | 5 | 5 | 0 |
| **Teknik Borç (X1–X10)** | 10 | 10 | 0 |
| **Kurumsal (K1–K4)** | 4 | 4 | 0 |
| **Toplam** | **43** | **43** | **0** |

### 2.1 Kurumsal

| # | Madde | Paket | FR | Ne Yapıldı |
|:-:|-------|-------|:--:|-----------|
| **K1** | SSO/SAML (kurumsal tek oturum) | `internal/sso/` | FR-A4 | SAML SP metadata, IdP config CRUD, ACS endpoint (IdP response handling), sertifika yönetimi; migration `019_sso_config.sql` |
| **K2** | SOC 2 Tip 1 hazırlığı | `internal/compliance/` | NFR-17 | 6 SOC 2 kontrol değerlendirmesi (CC1–CC6), GDPR/KVKK + ISO 27001 uyum raporu, evidence listesi (10 kalem), evidence export |
| **K3** | Genişletilmiş veri saklama (12 ay+) | `internal/retention/` | — | Retention politikası CRUD (4 entity tipi), periyodik arşivleme işçisi (delete/anonymize/archive_s3), arşiv özeti; migration `020_retention_policies.sql` |
| **K4** | Kurumsal pilot programı | `internal/pilot/` | — | Pilot tenant kaydı, 90 günlük deneme, destek seviyesi (standard/premium), deneme uzatma, otomatik ücretliye dönüşüm, admin paneli; migration `021_pilot_tenants.sql` |

### 2.2 Yeni Route'lar

```
POST   /v1/sso/acs/{tenantId}          — K1: SAML ACS (public)
GET    /v1/sso/config                  — K1: SSO config getir (admin)
PUT    /v1/sso/config                  — K1: SSO config güncelle (admin)
GET    /v1/sso/metadata                — K1: SP metadata XML (admin)
POST   /v1/sso/enable                  — K1: SSO etkinleştir (admin)
POST   /v1/sso/disable                 — K1: SSO devre dışı (admin)
POST   /v1/sso/generate-keys           — K1: SAML sertifikası üret (admin)
GET    /v1/compliance/soc2             — K2: SOC 2 hazırlık raporu (admin)
GET    /v1/compliance/report           — K2: Tam uyum raporu (admin)
GET    /v1/compliance/evidence         — K2: Evidence listesi (admin)
GET    /v1/compliance/evidence/download — K2: Evidence export (admin)
GET    /v1/workspaces/{ws}/retention/policies       — K3: Politikaları listele (viewer)
GET    /v1/workspaces/{ws}/retention/archive-summary — K3: Arşiv özeti (viewer)
PUT    /v1/workspaces/{ws}/retention/policies       — K3: Politika oluştur/güncelle (editor)
DELETE /v1/workspaces/{ws}/retention/policies/{id}  — K3: Politikayı sil (editor)
GET    /v1/pilot/status                — K4: Pilot durumu
POST   /v1/pilot/enroll                — K4: Pilot kaydı
POST   /v1/pilot/extend                — K4: Deneme uzat
POST   /v1/pilot/cancel                — K4: Pilot iptal
GET    /v1/pilot/tenants               — K4: Tüm pilotlar (admin)
```

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Açıklama |
|:--------:|:-----:|----------|
| 1.0 | 26 Temmuz 2026 | İlk sürüm |
| 2.0 | 26 Temmuz 2026 | MVP+HT1 çıkışı — M4–M12, H1–H10, T2–T3, X1–X10 tamam |
| 3.0 | 26 Temmuz 2026 | M1–M3 (deploy), H11–H12 (adapter), T1/T4/T5 (altyapı) tamam |
| 4.0 | 26 Temmuz 2026 | **K1–K4 kurumsal tamam** — tüm 43 madde kapalı |
