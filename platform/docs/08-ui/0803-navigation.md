# 0803 · Navigasyon

| Alan | Değer |
|---|---|---|
| Doküman ID | 0803 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0800–0805, 0202, 0201 |

---

## 1. Amaç

Bu doküman GeoLens Platform navigasyon yapısını tanımlar. Ana menü, alt sayfalar, derin bağlantılar ve çalışma alanı geçişi bu dokümanda detaylandırılır.

---

## 2. Navigasyon Yapısı

```
Üst Çubuk: [Çalışma Alanı 🔽]  │  [Paket: Business]  │  👤 Kullanıcı
─────────────────────────────────────────────────────────────────────────
Kenar Menü:
┌─────────────────┐
│ 🏠 Pano          │ ← Ana sayfa
│ 📊 Skorlar       │ ← Marka bazında skor detayı
│ 📈 Trendler      │ ← Zaman serisi analizi
│ 💡 Öneriler      │ ← Öneri listesi
│ 🔔 Uyarılar      │ ← Uyarı geçmişi
│ 📄 Raporlar      │ ← Rapor kütüphanesi
│ ⚙️ Ayarlar       │
│   ├ Markalar      │ ← Marka yönetimi
│   ├ Promptlar     │ ← Prompt setleri
│   ├ Kanallar      │ ← Bildirim kanalları
│   └ Üyeler        │ ← Ekip yönetimi
└─────────────────┘
```

---

## 3. Sayfa Hiyerarşisi

| # | Sayfa | Rol | Yol (SPA) |
|:-:|:-----:|:---:|:---------:|
| 1 | Pano | Ana sayfa | `/` |
| 2 | Skorlar | Marka skor detayı | `/brands/{id}` |
| 3 | Trendler | Zaman serisi | `/trends` |
| 4 | Öneriler | Öneri yönetimi | `/recommendations` |
| 5 | Uyarılar | Uyarı geçmişi | `/alerts` |
| 6 | Raporlar | Rapor kütüphanesi | `/reports` |
| 7 | Markalar | Marka CRUD | `/settings/brands` |
| 8 | Promptlar | Prompt yönetimi | `/settings/prompts` |
| 9 | Kanallar | Bildirim kanalları | `/settings/channels` |
| 10 | Üyeler | Ekip yönetimi | `/settings/members` |

---

## 4. Çalışma Alanı Geçişi

Üst çubukta çalışma alanı seçici (dropdown):

| Özellik | Değer |
|---------|-------|
| **Standart kiracı** | Varsayılan alan, seçici gizli |
| **Ajans kiracısı** | Müşteri listesi, hızlı arama |
| **Geçiş etkisi** | Sayfa yenileme, tüm veri güncellenir |
| **URL değişimi** | `/w/{workspace_id}/...` |

---

## 5. Derin Bağlantılar

E-posta özetlerinden gelen imzalı bağlantılar:

| Bağlantı | Hedef | Token Ömrü |
|:--------:|:-----:|:----------:|
| `/l/{token}` → Skor detayı | `/w/{ws}/brands/{id}` | 7 gün |
| `/l/{token}` → Öneri | `/w/{ws}/recommendations/{id}` | 7 gün |
| `/l/{token}` → Rapor | `/w/{ws}/reports/{id}` | 7 gün |

> Derin bağlantı token'ları tek kullanımlık ve kısa ömürlüdür (D-83).

---

## 6. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-83 | Derin bağlantı token ömrü [K]: 7 gün + tek kullanım. Haftalık özet döngüsüyle uyumlu. Pilotta kalibre edilecek. | AVIP 0306 O-2 (TL 21.07.2026) |
| D-36 | Path modeli: /v1/workspaces/{ws}/... — çalışma alanı bazlı URL yapısı onaylandı. | AVIP 0306 O-1 (TL 21.07.2026) |

---

## Kaynaklar

- 0801 — görsel bileşenler
- 0802 — pano düzeni
- 0202 — kullanıcı yolculukları
- 0201 — kullanıcı profilleri
- 0701 — API uçları

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: navigasyon yapısı, sayfa hiyerarşisi, çalışma alanı geçişi, derin bağlantılar. |
| 1.1 | 23.07.2026 | D-83 referansı düzeltildi (0306 O-2 → D-83). Devralınan AVIP Kararları eklendi: D-83 (token ömrü), D-36 (path modeli). |
