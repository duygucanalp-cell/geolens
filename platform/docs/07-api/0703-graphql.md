# 0703 · GraphQL (Rezerve)

| Alan | Değer |
|---|---|---|
| Doküman ID | 0703 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0701, 0206, 0504 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'da ileride kullanılmak üzere rezerve edilmiş GraphQL entegrasyonunu tanımlar. **MVP'de GraphQL kullanılmamaktadır.** Tüm API iletişimi REST üzerinden yapılır.

---

## 2. GraphQL İhtiyacı

| İhtiyaç | MVP Çözümü | GraphQL'e Geçiş Kriteri |
|---------|:----------:|-------------------------|
| Pano verisi tek sorguda | REST (paralel çağrı) | 5+ paralel REST çağrısı |
| Müşteriye özel veri şekillendirme | REST (sabit yanıt) | REST fazla veri döndürüyor |
| White-label rapor özelleştirme | PDF şablon | Kullanıcı talebi |

---

## 3. Rezerve Şema Tasarımı (Gelecek)

```graphql
type Query {
  workspace(id: ID!): Workspace
  scores(workspaceId: ID!, brandId: ID, engine: String): [Score!]!
  trends(workspaceId: ID!, brandId: ID!, period: Period!): [TrendPoint!]!
  recommendations(workspaceId: ID!, status: RecStatus): [Recommendation!]!
}

type Score {
  id: ID!
  value: Float!
  ciLow: Float!
  ciHigh: Float!
  fidelityLabel: String!
  engine: String
  freshnessAt: DateTime!
}
```

---

## 4. Alternatif: REST + GraphQL Proxy

GeoLens'in mevcut REST API'si üzerinde, ileride bir GraphQL proxy katmanı eklenebilir:

```
İstemci → GraphQL Proxy → REST API → PostgreSQL
```

Bu yaklaşım, mevcut REST API'yi değiştirmeden GraphQL desteği sağlar.

---

## Kaynaklar

- 0701 — mevcut REST API
- 0504 API Architecture — API tasarım standartları
- 0206 Roadmap — Platform Ufku

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GraphQL rezervasyonu, MVP'de kullanılmaz. REST + proxy alternatifi. |
