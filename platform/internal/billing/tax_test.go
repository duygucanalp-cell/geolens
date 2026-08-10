package billing

import "testing"

func TestCalculateVAT_ValidRates(t *testing.T) {
	cases := []struct {
		name      string
		subtotal  int64
		rate      int
		wantVAT   int64
		wantTotal int64
	}{
		{"yüzde 20", 10000, 20, 2000, 12000},
		{"yüzde 10", 10000, 10, 1000, 11000},
		{"yüzde 1", 10000, 1, 100, 10100},
		{"yüzde 0", 10000, 0, 0, 10000},
		{"yüzde 20 kuruş yuvarlama", 995, 20, 199, 1194}, // 199.0 → 199
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got, err := CalculateVAT(c.subtotal, c.rate)
			if err != nil {
				t.Fatalf("beklenmeyen hata: %v", err)
			}
			if got.VATAmount != c.wantVAT {
				t.Fatalf("VATAmount: beklenen %d, gelen %d", c.wantVAT, got.VATAmount)
			}
			if got.Total != c.wantTotal {
				t.Fatalf("Total: beklenen %d, gelen %d", c.wantTotal, got.Total)
			}
			if got.Subtotal != c.subtotal {
				t.Fatalf("Subtotal korunmadı: beklenen %d, gelen %d", c.subtotal, got.Subtotal)
			}
		})
	}
}

func TestCalculateVAT_InvalidRate(t *testing.T) {
	if _, err := CalculateVAT(10000, 15); err == nil {
		t.Fatal("geçersiz oran (15) için hata dönülmedi")
	}
	if _, err := CalculateVAT(10000, -1); err == nil {
		t.Fatal("negatif oran için hata dönülmedi")
	}
}

func TestCalculateVAT_NegativeSubtotal(t *testing.T) {
	if _, err := CalculateVAT(-100, 20); err == nil {
		t.Fatal("negatif ara toplam için hata dönülmedi")
	}
}
