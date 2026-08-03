# 0208 · Pilot Onboarding

| Alan | Değer |
|------|-------|
| Doküman ID | 0208 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Tarih | 24.07.2026 |

---

## 1. Pilot Süreci

### 1.1 Amaç
Pilot program, GeoLens Platform'un gerçek kullanıcılarla test edilmesini ve ürün-pazar uyumunun doğrulanmasını sağlar.

### 1.2 Pilot Kiracı Profilleri

| Persona | Açıklama | Hedef Sayı |
|:-------:|----------|:----------:|
| **P2** | Dijital ajans yöneticisi | 2-3 ajans |
| **P3** | Kurum içi marka yöneticisi | 3-5 marka |
| **P1 (Kurumsal)** | Büyük ölçekli kurum — Turkcell tipi müşteri adayı | 1-2 kurum |

### 1.3 Pilot Süresi
- **Minimum:** 4 hafta
- **Hedef:** 8 hafta
- **Kurumsal PoC:** 15-30 gün (Turkcell RFP standardı)
- **Değerlendirme:** Her hafta geri bildirim toplanır

### 1.4 PoC (Proof of Concept) Kriterleri

Turkcell RFP ve benzeri kurumsal müşteriler için PoC gereksinimleri:

| Kriter | Değer |
|:------:|-------|
| **PoC süresi** | 15-30 gün |
| **Minimum prompt sayısı** | 100 prompt |
| **Minimum rakip sayısı** | 5 rakip marka |
| **Marka bazlı analiz** | Her marka için ayrı görünürlük analizi |
| **Citation analizi** | Zorunlu |
| **Visibility benchmark raporu** | PoC sonunda teslim |
| **Executive Summary** | PoC sonunda yönetici özeti raporu |
| **Onboarding & eğitim** | Vendor tarafından sağlanmalı |
| **PoC sonuç sunumu** | Ekip ve yönetime sunum |

### 1.5 PoC Teslimatları

| Teslimat | Açıklama | Zamanlama |
|----------|----------|:---------:|
| **Brand Visibility Report** | Her marka için görünürlük skoru ve rakip kıyası | PoC sonu |
| **Citation Analysis** | Alıntı kaynakları, frekansları ve domain bazlı rapor | PoC sonu |
| **Content Gap Analysis** | AI sistemlerinin eksik bulduğu içerik alanları | PoC sonu |
| **Technical GEO Audit** | LLM bot erişimi, robots.txt, structured data analizi | PoC sonu |
| **Executive Summary** | Yönetici özeti ve roadmap önerileri | PoC sonu

---

## 2. Onboarding Adımları

### 2.1 Ön Hazırlık (1. Gün)

| # | Adım | Sorumlu | Süre |
|:-:|------|:-------:|:----:|
| 1 | Pilot sözleşmesi imzalanır | PO | 1 saat |
| 2 | Tenant oluşturulur + seed verisi yüklenir | TL | 30 dk |
| 3 | Kullanıcı hesapları oluşturulur | TL | 15 dk |
| 4 | Davetiye e-postası gönderilir | TL | 5 dk |

### 2.2 İlk Oturum (1. Hafta)

| # | Adım | Sorumlu | Süre |
|:-:|------|:-------:|:----:|
| 1 | Giriş ve arayüz turu (15 dk) | TL | — |
| 2 | Marka ekleme + panel oluşturma | Kullanıcı | 10 dk |
| 3 | İlk ölçümü tetikleme | Kullanıcı | 5 dk |
| 4 | Skorları inceleme | Kullanıcı | 10 dk |
| 5 | Önerileri görüntüleme | Kullanıcı | 10 dk |
| 6 | İlk geri bildirim (Google Form) | Kullanıcı | 15 dk |

### 2.3 Haftalık Rutin

| Gün | Aktivite |
|:---:|----------|
| Pazartesi | Panel taraması + ölçümler otomatik tetiklenir |
| Salı | Öneri akışı güncellenir |
| Çarşamba | Haftalık özet e-postası gönderilir |
| Cuma | Geri bildirim toplanır |

---

## 3. Kullanıcı Kılavuzu

### 3.1 Hızlı Başlangıç

```bash
# 1. Tarayıcıdan giriş yap
URL: http://app.geolens.ai
E-posta: (davetiyedeki e-posta)
Şifre: (davetiyedeki geçici şifre — ilk girişte değiştirin)

# 2. Yeni marka ekle
Sol menü → Markalar → "Marka Ekle"
Marka adı: "Acme"
Web sitesi: "https://acme.example.com"

# 3. Panel oluştur
Sol menü → Paneller → "Panel Oluştur"
Panel adı: "Haftalık Takip"
Markalar: [eklediğiniz markaları seçin]

# 4. Ölçüm başlat
Panel detayı → "Şimdi Ölç" butonu

# 5. Skorları gör
Dashboard → Skor kartı
```

### 3.2 Sık Kullanılan İşlemler

| İşlem | Adım |
|-------|------|
| Şifre değiştirme | Sağ üst → Profil → Şifre Değiştir |
| E-posta bildirimi ayarları | Ayarlar → Bildirimler → Tercihler |
| PDF rapor indirme | Raporlar → Haftalık Özet → İndir |
| Öneriyi uygulandı işaretleme | Öneri → "Uygulandı" butonu |
| Yeni kullanıcı ekleme | Ayarlar → Kullanıcılar → Davet Et |

### 3.3 Terimler Sözlüğü

| Terim | Açıklama |
|-------|----------|
| **Görünürlük Skoru** | Markanızın AI yanıtlarındaki varlığını ölçen 0-100 puan |
| **Varlık Payı** | AI yanıtlarında markanızdan kaç kez bahsedildiği |
| **Konum Ağırlığı** | AI yanıtında markanızın ne kadar erken geçtiği |
| **Kaynak Çeşitliliği** | AI'nın hangi kaynaklardan bilgi aldığı |
| **Rakip Bağlamı** | Rakiplerinize göre görünürlük durumunuz |
| **Panel** | Düzenli ölçüm yapılan marka grubu |
| **Fidelite** | Motor yanıtının güvenilirlik seviyesi (Kademe 1-3) |

---

## 4. Geri Bildirim Toplama

### 4.1 Haftalık Anket

Her Cuma Google Form üzerinden:

1. **Genel memnuniyet** (1-5): GeoLens'i kullanmaktan ne kadar memnunsunuz?
2. **Skor doğruluğu** (1-5): Görünürlük skorları beklentilerinizi karşılıyor mu?
3. **Öneri kalitesi** (1-5): Öneriler ne kadar kullanışlı?
4. **Eksik özellik** (metin): Hangi özellik eklenmeli?
5. **Hata bildirimi** (metin): Karşılaştığınız sorunlar?

### 4.2 Referans Sinyali Kriterleri

Pilot kiracısının referans vermesi için:

- ✅ En az 4 hafta aktif kullanım
- ✅ En az 1 panel aktif ve düzenli ölçüm alıyor
- ✅ Memnuniyet skoru ≥ 4/5 (son 2 hafta)
- ✅ En az 1 öneriyi uygulamış

---

## 5. Destek

| Kanal | Detay |
|-------|-------|
| E-posta | pilot@geolens.ai |
| Slack | #geolens-pilot (davetiye ile) |
| Acil durum | +90 (555) 123 4567 |
| Çalışma saatleri | Pazartesi-Cuma, 09:00-18:00 (TSİ) |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayın: onboarding adımları, kullanıcı kılavuzu, geri bildirim anketi |
| 1.1 | 27.07.2026 | Turkcell RFP kapsamında genişletme: PoC kriterleri eklendi (15-30 gün, 100+ prompt, 5+ rakip). PoC teslimatları (brand visibility, citation, content gap, technical GEO, executive summary) eklendi. Kurumsal müşteri profili (P1) eklendi. |
