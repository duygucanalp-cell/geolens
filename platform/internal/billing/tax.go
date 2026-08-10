// Package billing provides handlers and logic for billing functionality.
package billing

import "fmt"

// AllowedVATRates, Türkiye'de 2019'dan itibaren yürürlükte olan KDV oranlarıdır.
// e-Fatura/e-Arşiv faturalarında yalnızca bu oranlar kullanılabilir.
var AllowedVATRates = []int{0, 1, 10, 20}

// TaxBreakdown, KDV hesaplamasının sonucunu taşır (tüm tutarlar kuruş cinsinden).
type TaxBreakdown struct {
	Subtotal  int64 `json:"subtotal"`
	VATRate   int   `json:"vat_rate"`
	VATAmount int64 `json:"vat_amount"`
	Total     int64 `json:"total"`
}

// CalculateVAT, net ara toplam (kuruş) üzerinden Türk KDV oranıyla vergi kırılımını hesaplar.
// Oran yalnızca izinli kümede (0, 1, 10, 20) olabilir. KDV tutarı en yakın kuruşa yuvarlanır.
func CalculateVAT(subtotal int64, vatRate int) (TaxBreakdown, error) {
	valid := false
	for _, r := range AllowedVATRates {
		if r == vatRate {
			valid = true
			break
		}
	}
	if !valid {
		return TaxBreakdown{}, fmt.Errorf("geçersiz KDV oranı: %d (izinli: 0, 1, 10, 20)", vatRate)
	}
	if subtotal < 0 {
		return TaxBreakdown{}, fmt.Errorf("ara toplam negatif olamaz: %d", subtotal)
	}

	// (subtotal * rate + 50) / 100 → en yakın kuruşa yuvarlama
	vat := (subtotal*int64(vatRate) + 50) / 100

	return TaxBreakdown{
		Subtotal:  subtotal,
		VATRate:   vatRate,
		VATAmount: vat,
		Total:     subtotal + vat,
	}, nil
}
