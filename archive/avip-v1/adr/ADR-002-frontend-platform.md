# ADR-002 · Frontend Platformu

| Alan | Değer |
|---|---|
| ADR ID | ADR-002 |
| Durum | Kabul |
| Tarih | 12.07.2026 |
| Karar veren | PO + TL |
| İlişkili | 0304 Technology Selection, 0007 D-02 |

## Bağlam

AVIP dashboard'u için frontend platformu seçilmelidir. Seçim kriterleri: ekosistem olgunluğu, component kütüphaneleri, TypeScript desteği, mobil genişleme yolu.

## Karar

**React + TypeScript SPA** olarak devam edilir.

Mobil genişleme (gelecekte) için Flutter yolu ayrık tutulur; mevcut API katmanı zaten bağımsızdır.

## Alternatifler

| Seçenek | Red nedeni |
|---|---|
| Vue.js | Ekip React deneyimli; ekosistem genişliği React lehine |
| Flutter (web) | Web'de olgunluk eksikliği; mevcut React yatırımının atılması |
| Next.js SSR | Dashboard uygulaması SSR gerektirmiyor; ek karmaşıklık |

## Sonuçlar

- D-02 kapanır; karar 0007 karar defterine işlenmiştir.
- Mobil penceresi açıldığında (0206 HT1) Flutter yeniden değerlendirilir (0304 O-4).

## Değişiklik Geçmişi

| Tarih | Değişiklik |
|---|---|
| 12.07.2026 | İlk karar: Kabul |
