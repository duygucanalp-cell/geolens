# 1001 · Kodlama Standartları (Coding Standards)

| Alan | Değer |
|---|---|---|
| Doküman ID | 1001 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 1000–1006, 0502, 0403 |

---

## 1. Amaç

Bu doküman GeoLens Platform Go backend ve React frontend kodlama standartlarını tanımlar. Tutarlı, okunabilir ve bakımı kolay kod üretmek hedeflenir.

---

## 2. Go Standartları

| Kural | Açıklama |
|:-----:|----------|
| **Adlandırma** | camelCase (değişken), PascalCase (export), snake_case (DB) |
| **Format** | `gofmt` / `go fmt` zorunlu |
| **Import** | Standart → Üçüncü parti → İç (3 grup) |
| **Hata yönetimi** | Hatalar asla sessizce atlanmaz; errcheck lint |
| **Log** | Yapılandırılmış JSON log (correlation_id ile) |
| **Test** | `_test.go` her pakette; integration build tag'li |
| **DTO** | OpenAPI'den üretilir; el yazımı DTO yasak |

### Go Dosya Düzeni (Bağlam Paketi)

```
internal/{context}/
├── api.go           # Dışa açık arayüzler, DTO'lar
├── service.go       # İş mantığı
├── repo.go          # sqlc sarmalayıcı
├── events.go        # Olay tanımları
├── errors.go        # Hata sözlüğü
├── internal/        # Paket içi tipler
├── api_test.go      # API testleri
└── service_test.go  # Servis testleri
```

---

## 3. TypeScript/React Standartları

| Kural | Açıklama |
|:-----:|----------|
| **Framework** | React + TypeScript + TanStack Query |
| **State yönetimi** | TanStack Query (server), Context (client) |
| **Stil** | Tailwind CSS + CSS Modules |
| **Bileşen** | Fonksiyonel component, hooks |
| **Test** | Vitest + React Testing Library |
| **Tip** | OpenAPI'den üretilen tipler kullanılır |
| **Format** | Prettier + ESLint |

---

## 4. Genel Kurallar

| Kural | Açıklama |
|:-----:|----------|
| **Sözleşme-öncelikli** | openapi.yaml → Go/TS tipleri |
| **DRY** | Tekrar eden kod fonksiyon/bileşene çıkarılır |
| **KISS** | Karmaşık çözümler yerine basit çözümler tercih edilir |
| **Yorum** | Neden'i açıklar, ne'yi değil (kod zaten ne'yi gösterir) |
| **Commit** | Conventional Commits (feat, fix, chore, docs) |

---

## Kaynaklar

- 0502 Service Architecture — bağımlılık kuralları (D1-D7)
- 0901 — lint CI kapısı
- archive/avip-v1/0401-development-process.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: Go standartları, TypeScript/React standartları, genel kurallar. |
