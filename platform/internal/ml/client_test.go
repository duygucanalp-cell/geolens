package ml

import (
	"context"
	"encoding/json"
	"math"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestNewClient_EmptyURL(t *testing.T) {
	c := NewClient("", 0)
	if c != nil {
		t.Error("boş baseURL için nil dönmeli")
	}
}

func TestPredictSentiment_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/predict" {
			t.Errorf("beklenen /v1/predict, gerçek %s", r.URL.Path)
		}
		// logits: [neg=0.1, nötr=0.2, poz=2.1] → softmax → positive
		_, _ = w.Write([]byte(`{"model":"sentiment","model_version":"1.0.0","outputs":{"logits":[[0.1,0.2,2.1]]}}`))
	}))
	defer srv.Close()

	pred, err := NewClient(srv.URL, 0).PredictSentiment(context.Background(), "Acme harika bir marka", "tr")
	if err != nil {
		t.Fatalf("PredictSentiment hata: %v", err)
	}
	if pred.ModelVersion != "1.0.0" {
		t.Errorf("beklenen 1.0.0, gerçek %s", pred.ModelVersion)
	}
	if pred.Label != "positive" {
		t.Errorf("beklenen positive, gerçek %s", pred.Label)
	}
	// softmax([0.1,0.2,2.1]): pozitif olasılık en yüksek ve >0.5 olmalı
	if pred.Probabilities[2] < 0.5 || pred.Confidence != pred.Probabilities[2] {
		t.Errorf("beklenen yüksek pozitif olasılık, gerçek %v conf=%f", pred.Probabilities, pred.Confidence)
	}
	sum := pred.Probabilities[0] + pred.Probabilities[1] + pred.Probabilities[2]
	if math.Abs(sum-1.0) > 1e-9 {
		t.Errorf("softmax toplamı 1 olmalı, gerçek %f", sum)
	}
}

func TestPredictSentiment_MissingLogits(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"model":"sentiment","model_version":"1.0.0","outputs":{"score":[0.9]}}`))
	}))
	defer srv.Close()

	_, err := NewClient(srv.URL, 0).PredictSentiment(context.Background(), "x", "")
	if err == nil {
		t.Fatal("logits yokken hata bekleniyor")
	}
}

func TestPredictSentiment_ServerError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"detail":"inference hatası"}`))
	}))
	defer srv.Close()

	_, err := NewClient(srv.URL, 0).PredictSentiment(context.Background(), "x", "")
	if err == nil {
		t.Fatal("500 için hata bekleniyor")
	}
}

func TestPredictSentiment_NilClient(t *testing.T) {
	if _, err := (*Client)(nil).PredictSentiment(context.Background(), "x", ""); err == nil {
		t.Fatal("nil client için hata bekleniyor")
	}
}

func TestClassifyPrompt_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/prompt/classify" {
			t.Errorf("beklenen /v1/prompt/classify, gerçek %s", r.URL.Path)
		}
		_, _ = w.Write([]byte(`{"intent":{"label":"comparison","confidence":0.92},"topic":{"label":"brand","confidence":0.88},"persona":{"label":"consumer","confidence":0.71},"funnel":{"label":"evaluation","confidence":0.65}}`))
	}))
	defer srv.Close()

	cls, err := NewClient(srv.URL, 0).ClassifyPrompt(context.Background(), "Acme'nin en iyi rakibi kim?")
	if err != nil {
		t.Fatalf("ClassifyPrompt hata: %v", err)
	}
	if cls.Intent.Label != "comparison" || cls.Intent.Confidence != 0.92 {
		t.Errorf("intent hatalı: %+v", cls.Intent)
	}
	if cls.Topic.Label != "brand" || cls.Funnel.Label != "evaluation" {
		t.Errorf("topic/funnel hatalı: %+v", cls)
	}
}

// TestClassifyPrompt_NewIntentLabel: 0421-8INTENT yeni intent etiketi (opinion)
// serbest string olarak client'tan aynen geçer (enum yok).
func TestClassifyPrompt_NewIntentLabel(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"intent":{"label":"opinion","confidence":0.81},"topic":{"label":"brand","confidence":0.7},"persona":{"label":"executive","confidence":0.6},"funnel":{"label":"consideration","confidence":0.55}}`))
	}))
	defer srv.Close()

	cls, err := NewClient(srv.URL, 0).ClassifyPrompt(context.Background(), "Acme hakkında ne düşünüyorsun?")
	if err != nil {
		t.Fatalf("ClassifyPrompt hata: %v", err)
	}
	if cls.Intent.Label != "opinion" || cls.Intent.Confidence != 0.81 {
		t.Errorf("yeni intent etiketi aynen geçmeli: %+v", cls.Intent)
	}
}

func TestClassifyPrompt_ModelMissing(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte(`{"detail":"model bulunamadı: prompt_intent"}`))
	}))
	defer srv.Close()

	if _, err := NewClient(srv.URL, 0).ClassifyPrompt(context.Background(), "x"); err == nil {
		t.Fatal("404 için hata bekleniyor")
	}
}

func TestClassifyPrompt_NilClient(t *testing.T) {
	if _, err := (*Client)(nil).ClassifyPrompt(context.Background(), "x"); err == nil {
		t.Fatal("nil client için hata bekleniyor")
	}
}

func TestDetectHallucinations_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/hallucination/detect" {
			t.Errorf("beklenen /v1/hallucination/detect, gerçek %s", r.URL.Path)
		}
		_, _ = w.Write([]byte(`{"findings":[{"type":"T3","severity":"high","description":"Çelişik sayısal claim","confidence":0.7,"engine":"chatgpt"}]}`))
	}))
	defer srv.Close()

	findings, err := NewClient(srv.URL, 0).DetectHallucinations(context.Background(), []HallucinationResponse{
		{ID: "r1", Engine: "chatgpt", Text: "%30 büyüme"},
		{ID: "r2", Engine: "gemini", Text: "%60 büyüme"},
	})
	if err != nil {
		t.Fatalf("DetectHallucinations hata: %v", err)
	}
	if len(findings) != 1 {
		t.Fatalf("beklenen 1 finding, gerçek %d", len(findings))
	}
	if findings[0].Type != "T3" || findings[0].Engine != "chatgpt" || findings[0].Confidence != 0.7 {
		t.Errorf("finding alanları hatalı: %+v", findings[0])
	}
}

func TestDetectHallucinations_SingleResponseNoCall(t *testing.T) {
	// <2 yanıt → HTTP çağrısı yapılmadan nil döner
	findings, err := NewClient("http://localhost:1", 0).DetectHallucinations(context.Background(), []HallucinationResponse{
		{ID: "r1", Engine: "chatgpt", Text: "tek cevap"},
	})
	if err != nil {
		t.Fatalf("tek yanıtta hata beklenmiyor: %v", err)
	}
	if findings != nil {
		t.Errorf("tek yanıtta nil dönmeli, gerçek %+v", findings)
	}
}

func TestDetectHallucinations_ServerError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"detail":"hata"}`))
	}))
	defer srv.Close()

	_, err := NewClient(srv.URL, 0).DetectHallucinations(context.Background(), []HallucinationResponse{
		{ID: "r1", Engine: "chatgpt", Text: "a"},
		{ID: "r2", Engine: "gemini", Text: "b"},
	})
	if err == nil {
		t.Fatal("500 için hata bekleniyor")
	}
}

func TestSoftmaxRow_RowShape(t *testing.T) {
	// Satır sayısı != 3 → hata
	if _, err := softmaxRow([]any{[]any{0.1, 0.2}}); err == nil {
		t.Fatal("2 sınıflı logits için hata bekleniyor")
	}
	// Satır öğesi sayı değil → hata
	if _, err := softmaxRow([]any{[]any{0.1, "x", 0.2}}); err == nil {
		t.Fatal("string öğe için hata bekleniyor")
	}
	// Boş → hata
	if _, err := softmaxRow([]any{}); err == nil {
		t.Fatal("boş logits için hata bekleniyor")
	}
}

func TestPredict_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/predict" {
			t.Errorf("beklenen /v1/predict, gerçek %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"model":"sentiment","model_version":"1.0.0","outputs":{"score":[0.92]}}`))
	}))
	defer srv.Close()

	c := NewClient(srv.URL, 0)
	res, err := c.Predict(context.Background(), map[string]any{"model": "sentiment", "lang": "tr", "text": "Acme harika"})
	if err != nil {
		t.Fatalf("Predict hata: %v", err)
	}
	if res.Model != "sentiment" {
		t.Errorf("beklenen sentiment, gerçek %s", res.Model)
	}
	if res.ModelVersion != "1.0.0" {
		t.Errorf("beklenen 1.0.0, gerçek %s", res.ModelVersion)
	}
	score, ok := res.Outputs["score"].([]any)
	if !ok || len(score) != 1 {
		t.Fatalf("outputs.score beklenmiyordu: %#v", res.Outputs)
	}
}

func TestPredict_NotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte(`{"detail":"model bulunamadi"}`))
	}))
	defer srv.Close()

	_, err := NewClient(srv.URL, 0).Predict(context.Background(), map[string]any{"model": "yok"})
	if err == nil {
		t.Fatal("404 için hata bekleniyor")
	}
}

func TestPredict_Timeout(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(3 * time.Second)
	}))
	defer srv.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	_, err := NewClient(srv.URL, 0).Predict(ctx, map[string]any{"model": "sentiment"})
	if err == nil {
		t.Fatal("timeout için hata bekleniyor")
	}
}

func TestPredict_MarshalFailureNoPanic(t *testing.T) {
	c := NewClient("http://localhost:1", 0)
	// döngüsel yapı marshal hatası üretir
	payload := map[string]any{"loop": func() {}}
	_, err := c.Predict(context.Background(), payload)
	if err == nil {
		t.Fatal("marshal hatası için hata bekleniyor")
	}
}

func TestHealth(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode(map[string]any{"status": "ok", "models": []string{}})
	}))
	defer srv.Close()

	out, err := NewClient(srv.URL, 0).Health(context.Background())
	if err != nil {
		t.Fatalf("Health hata: %v", err)
	}
	if out["status"] != "ok" {
		t.Errorf("beklenen ok, gerçek %v", out["status"])
	}
}
