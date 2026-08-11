package config

import (
	"math"
	"testing"
)

func TestParseIntentWeightScale_Valid(t *testing.T) {
	raw := "presence=1.25,1.00,0.90,0.90,1.10,0.90,0.90;comparison=0.90,1.00,0.90,1.40,0.90,0.90,1.30"
	scale, ok := ParseIntentWeightScaleRaw(raw)
	if !ok {
		t.Fatal("geçerli girdi ok=true olmalı")
	}
	if len(scale) != 2 {
		t.Fatalf("beklenen 2 intent, gerçek %d", len(scale))
	}
	if scale["presence"][0] != 1.25 || scale["presence"][6] != 0.90 {
		t.Errorf("presence çarpanları hatalı: %v", scale["presence"])
	}
	if scale["comparison"][3] != 1.40 {
		t.Errorf("comparison rakip çarpanı 1.40 olmalı: %v", scale["comparison"])
	}
}

func TestParseIntentWeightScale_Empty(t *testing.T) {
	if _, ok := ParseIntentWeightScaleRaw(""); ok {
		t.Fatal("boş girdi ok=false olmalı")
	}
	if _, ok := ParseIntentWeightScaleRaw("   "); ok {
		t.Fatal("boşluklu girdi ok=false olmalı")
	}
}

func TestParseIntentWeightScale_Invalid(t *testing.T) {
	cases := []string{
		"presence=1.25",                    // 7 değer değil
		"presence=1.25,1,0.9,0.9,1.1,0.9",  // 6 değer
		"presence=abc,1,0.9,0.9,1.1,0.9,1", // sayısal değil
		"presence=-1,1,0.9,0.9,1.1,0.9,1",  // negatif
		"presence=NaN,1,0.9,0.9,1.1,0.9,1", // NaN kabul edilmez
		"presence=1,1,0.9,0.9,1.1,0.9,Inf", // Inf kabul edilmez
		"presence=1,1,0.9,0.9,1.1,0.9,1;=", // ikinci girişte intent boş
		"sadece=1,1,1,1,1,1,1;sadece",      // ikinci girişte = işareti eksik
	}
	for _, c := range cases {
		if _, ok := ParseIntentWeightScaleRaw(c); ok {
			t.Errorf("geçersiz girdi ok=true olmamalı: %q", c)
		}
	}
}

func TestParseIntentWeightScale_ConfigMethod(t *testing.T) {
	c := Config{IntentWeightScaleRaw: "problem=1.00,1.15,1.10,1.00,0.90,1.00,1.00"}
	scale, ok := c.ParseIntentWeightScale()
	if !ok {
		t.Fatal("Config metodu geçerli girdiyi kabul etmeli")
	}
	if math.Abs(scale["problem"][1]-1.15) > 1e-9 {
		t.Errorf("problem konum çarpanı 1.15 olmalı: %v", scale["problem"])
	}
}
