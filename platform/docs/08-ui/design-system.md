# Tasarım Sistemi (Design System)

| Alan | Değer |
|---|---|
| Doküman ID | 08-ui/design-system |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 08-ui/*, 0202, 0201, 0204 |

---

## 1. Amaç

Bu doküman GeoLens Platform tasarım sistemini tanımlar. Renk paleti, tipografi, bileşen kütüphanesi ve tasarım ilkeleri bu dokümanda sabitlenir.

---

## 2. Tasarım İlkeleri

| # | İlke | Açıklama |
|:-:|------|----------|
| **D1** | Güven önce gelir | Her skor yüzeyi fidelite etiketi taşır; şeffaflık esastır |
| **D2** | Netlik karmaşıklığı yener | Karmaşık veri basit görsellerle anlatılır |
| **D3** | Tutarlılık | Tüm yüzeyler aynı dil ve desenleri kullanır |
| **D4** | TR-öncelikli | Arayüz Türkçe birincil, İngilizce ikincil |
| **D5** | Aksiyon odaklı | Her ekran kullanıcıyı bir sonraki aksiyona yönlendirir |

---

## 3. Renk Paleti

| Rol | Renk | HEX | Kullanım |
|:---:|:----:|:---:|----------|
| **Birincil** | Lacivert | `#1B2A4A` | Ana butonlar, başlıklar, üst çubuk |
| **İkincil** | Turkuaz | `#0EA5E9` | Linkler, vurgular, aktif durumlar |
| **Başarı** | Yeşil | `#10B981` | Skor yükselmesi, başarılı durum |
| **Uyarı** | Turuncu | `#F59E0B` | Orta seviye uyarılar, skor düşüşü |
| **Hata** | Kırmızı | `#EF4444` | Kritik uyarılar, hata durumları |
| **Arka plan** | Açık gri | `#F8FAFC` | Sayfa arka planı |
| **Kart** | Beyaz | `#FFFFFF` | Kart, modal, sidebar |
| **Metin** | Koyu gri | `#1E293B` | Birincil metin rengi |
| **Metin ikincil** | Gri | `#64748B` | İkincil metin, label |

---

## 4. Tipografi

| Stil | Font | Boyut | Ağırlık | Kullanım |
|:----:|:----:|:-----:|:-------:|----------|
| H1 | Inter | 32px | Bold (700) | Sayfa başlıkları |
| H2 | Inter | 24px | Bold (700) | Bölüm başlıkları |
| H3 | Inter | 20px | Semi-bold (600) | Kart başlıkları |
| Body | Inter | 16px | Regular (400) | Ana metin |
| Body small | Inter | 14px | Regular (400) | Alt metin, label |
| Mono | JetBrains Mono | 14px | Regular (400) | Skor değerleri, kod |
| Skor | Inter | 48px | Bold (700) | Skor gösterimi |

---

## 5. Bileşen Kütüphanesi (Temel)

| Bileşen | Açıklama | Durum |
|---------|----------|:-----:|
| **Skor Kartı** | Marka skorunu GA, fidelite etiketi ve trendle gösterir | ✅ MVP |
| **Trend Grafiği** | Zaman serisi; panel sınırı işareti | ✅ MVP |
| **Motor Kırılımı** | Motor bazında skor karşılaştırması | ✅ MVP |
| **Alıntı Listesi** | Kaynak URL ve başlıkla alıntılar | ✅ MVP |
| **Öneri Kartı** | Kanıt derecesi ve işaretleme butonları | ✅ MVP |
| **Uyarı Bildirimi** | Anlık bildirim ve digest görünümü | ✅ MVP |
| **White-label Rapor** | Özelleştirilebilir PDF ön izleme | 🟡 MVP (dar.) |
| **Pano (Dashboard)** | Genel bakış, son ölçümler, trend | ✅ MVP |
| **Kurulum Sihirbazı** | Adım adım marka/prompt kurulumu | ✅ MVP |

---

## 6. React Bileşen Hiyerarşisi

```
<App>
  <Layout>
    <Sidebar>
      <WorkspaceSwitcher />
      <NavMenu />
    </Sidebar>
    <Main>
      <Dashboard>
        <ScoreCard />
        <TrendChart />
        <InsightPanel>
          <RecommendationCard />
          <AlertBadge />
        </InsightPanel>
      </Dashboard>
    </Main>
  </Layout>
</App>
```

---

## 7. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-26 | Grafik/ızgara kütüphaneleri: Recharts + TanStack Table. GA bant görselleştirme ve kırılım tabloları için. | AVIP 0304 O-3 (TL 21.07.2026) |
| D-38 | Mobil talep eşiği: kullanıcıların ≥%20'si mobil talep ettiğinde veya P1 kurumsal kapısı açıldığında Flutter yeniden değerlendirilir. | AVIP 0304 O-4 (TL 21.07.2026) |
| D-81 | Sentiment (bağlam değerlendirmesi) V1 kapsamı dışı. Terim platform ufku notuyla sözlükte kalır. | AVIP 0005 O-2 (PO 21.07.2026) |

---

## Kaynaklar

- 08-ui/dashboard — pano tasarımı
- 08-ui/navigation — navigasyon yapısı
- 08-ui/onboarding — onboarding akışı
- 0202 User Journeys — kullanıcı yolculukları
- 0201 Personas — kullanıcı profilleri

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 5 tasarım ilkesi, renk paleti, tipografi, bileşen kütüphanesi, React hiyerarşisi. |
| 1.1 | 23.07.2026 | Devralınan AVIP Kararları eklendi: D-26 (Recharts+TanStack Table), D-38 (Flutter reeval), D-81 (Sentiment V1 dışı). |
