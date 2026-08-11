## A3-2 · Sektör Ağırlık Profilleri ve Duyarlılık Analizi (İP-06 §çıktı 2)

| Sektör profili | presence | position | citation | competitor | appearance | sentiment | compvis |
|:--|---|---|---|---|---|---|---|
| Varsayılan | 0.30 | 0.20 | 0.15 | 0.15 | 0.10 | 0.05 | 0.05 |
| E-ticaret (recommendation/appearance odaklı) | 0.20 | 0.15 | 0.10 | 0.10 | 0.25 | 0.10 | 0.10 |
| Sağlık (authority/sentiment odaklı — güven) | 0.15 | 0.10 | 0.20 | 0.05 | 0.10 | 0.30 | 0.10 |
| Teknoloji/B2B (citation/competitor odaklı) | 0.25 | 0.15 | 0.20 | 0.20 | 0.10 | 0.05 | 0.05 |

### Duyarlılık Analizi (tek bileşen ağırlığı +%5, geri kalan orantılı düşürülür)

| Bileşen | ΔVI (puan) |
|:--|:--|
| presence | +1.060 |
| position | +0.930 |
| citation | +0.870 |
| competitor | -4.980 |
| appearance | +0.820 |
| sentiment | +0.780 |
| compvis | +0.780 |

Örnek bileşen seti: {"presence": 1.0, "position": 1.0, "citation": 1.0, "competitor": 0.5, "appearance": 1.0, "sentiment": 100.0, "compvis": 1.0}

Yorum: presence/position yüksek ağırlıklı bileşenlerdeki artış VI'yı en çok
etkiler; düşük değerli bileşenlerin ağırlığının artması bağıl dağıtımdan
dolayı küçük negatif etki yaratabilir. Sektör profili seçimi (profile_by_sector)
API'nin `X-GeoLens-Profile` başlığıyla yönetilir.
