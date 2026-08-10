package delivery

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
)

func TestBuildWebhookPayloadFormats(t *testing.T) {
	notif := Notification{
		ID:          "n1",
		TenantID:    "t1",
		WorkspaceID: "w1",
		Type:        NotificationScoreDrop,
		Title:       "Skor Düştü",
		Body:        "marka skoru 10 puan azaldı",
		Data:        map[string]interface{}{"drop": 10},
	}

	// generic
	payload, ct, err := buildWebhookPayload(notif)
	if err != nil {
		t.Fatalf("generic payload: %v", err)
	}
	if ct != "application/json" {
		t.Errorf("generic ct = %s", ct)
	}
	var generic map[string]interface{}
	if err := json.Unmarshal(payload, &generic); err != nil {
		t.Fatalf("generic json: %v", err)
	}
	if generic["title"] != "Skor Düştü" {
		t.Errorf("generic title = %v", generic["title"])
	}

	// slack — text alanı var, custom_details yok
	payload, _, err = buildWebhookPayload(Notification{WebhookKind: WebhookKindSlack, Title: "T", Body: "B"})
	if err != nil {
		t.Fatalf("slack payload: %v", err)
	}
	if !strings.Contains(string(payload), `"text"`) {
		t.Errorf("slack payload text alanı yok: %s", payload)
	}

	// teams — MessageCard sabitleri
	payload, _, err = buildWebhookPayload(Notification{WebhookKind: WebhookKindTeams, Title: "T", Body: "B"})
	if err != nil {
		t.Fatalf("teams payload: %v", err)
	}
	if !strings.Contains(string(payload), `"MessageCard"`) {
		t.Errorf("teams payload MessageCard yok: %s", payload)
	}

	// discord — content alanı
	payload, _, err = buildWebhookPayload(Notification{WebhookKind: WebhookKindDiscord, Title: "T", Body: "B"})
	if err != nil {
		t.Fatalf("discord payload: %v", err)
	}
	if !strings.Contains(string(payload), `"content"`) {
		t.Errorf("discord payload content yok: %s", payload)
	}

	// pagerduty — score_drop severity warning
	payload, _, err = buildWebhookPayload(Notification{WebhookKind: WebhookKindPagerDuty, Type: NotificationScoreDrop, Title: "T", Body: "B"})
	if err != nil {
		t.Fatalf("pagerduty payload: %v", err)
	}
	var pd map[string]interface{}
	if err := json.Unmarshal(payload, &pd); err != nil {
		t.Fatalf("pagerduty json: %v", err)
	}
	pdPayload := pd["payload"].(map[string]interface{})
	if pdPayload["severity"] != "warning" {
		t.Errorf("pagerduty severity = %v (want warning)", pdPayload["severity"])
	}
}

func TestSendWebhookErrorHandling(t *testing.T) {
	// URL yoksa hata
	s := &service{}
	if err := s.SendWebhook(Notification{}); err == nil {
		t.Fatal("webhook URL yokken hata bekleniyor")
	}

	// non-2xx dönünce hata
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	if err := s.SendWebhook(Notification{WebhookURL: server.URL, WebhookKind: WebhookKindSlack, Title: "T", Body: "B"}); err == nil {
		t.Fatal("HTTP 500 dönünce hata bekleniyor")
	}
}

func TestSendWebhookSuccess(t *testing.T) {
	var mu sync.Mutex
	received := false
	contentType := ""

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		received = true
		contentType = r.Header.Get("Content-Type")
		mu.Unlock()
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	s := &service{}
	notif := Notification{
		WebhookURL:  server.URL,
		WebhookKind: WebhookKindDiscord,
		Title:       "T",
		Body:        "B",
	}
	if err := s.SendWebhook(notif); err != nil {
		t.Fatalf("webhook gönderimi: %v", err)
	}

	mu.Lock()
	defer mu.Unlock()
	if !received {
		t.Fatal("server webhook almadı")
	}
	if contentType != "application/json" {
		t.Errorf("content-type = %s", contentType)
	}
}

func TestSendNotificationWebhookChannel(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	s := &service{}
	notif := Notification{
		ID:          "n-webhook",
		Channel:     ChannelWebhook,
		WebhookURL:  server.URL,
		WebhookKind: WebhookKindGeneric,
		Title:       "T",
		Body:        "B",
	}
	if err := s.SendNotification(notif); err != nil {
		t.Fatalf("SendNotification(webhook): %v", err)
	}
}
