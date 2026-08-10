package delivery

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestGovernanceEventMeta(t *testing.T) {
	cases := []struct {
		eventType   string
		payload     map[string]interface{}
		wantTitle   string
		wantBodySub string
	}{
		{
			eventType: "guardrail.violation",
			payload:   map[string]interface{}{"rule_name": "Email Leak", "category": "pii_leakage", "action_taken": "block"},
			wantTitle: "Guardrail İhlali Tespit Edildi", wantBodySub: "Email Leak",
		},
		{
			eventType: "gate.check.decision",
			payload:   map[string]interface{}{"entity_type": "prompt", "version": "v1.2", "decision": "blocked", "target_env": "production"},
			wantTitle: "Gate Kontrol Kararı", wantBodySub: "blocked",
		},
		{
			eventType: "incident.opened",
			payload:   map[string]interface{}{"severity": "critical", "title": "Skor düştü", "category": "visibility"},
			wantTitle: "Yeni Olay Açıldı", wantBodySub: "Skor düştü",
		},
		{
			eventType: "drift.alert.triggered",
			payload:   map[string]interface{}{"metric": "visibility_score", "drift_score": 62.5, "severity": "critical", "delta": 4.2},
			wantTitle: "Drift Uyarısı", wantBodySub: "visibility_score",
		},
		{
			eventType: "redteam.run.completed",
			payload:   map[string]interface{}{"target_name": "checkout-bot", "passed": 8.0, "failed": 2.0, "defense_score": 80.0},
			wantTitle: "Red Team Çalışması Tamamlandı", wantBodySub: "checkout-bot",
		},
		{
			eventType: "future.event.type",
			payload:   map[string]interface{}{"detail": "detay"},
			wantTitle: "Yönetişim Olayı: future.event.type", wantBodySub: "detay",
		},
	}

	for _, c := range cases {
		title, body := governanceEventMeta(c.eventType, c.payload)
		if title != c.wantTitle {
			t.Errorf("%s: title = %q, want %q", c.eventType, title, c.wantTitle)
		}
		if !strings.Contains(body, c.wantBodySub) {
			t.Errorf("%s: body %q, want substring %q", c.eventType, body, c.wantBodySub)
		}
	}
}

func TestBuildGovernanceNotification(t *testing.T) {
	notif := buildGovernanceNotification("T01", "W01", "incident.opened",
		map[string]interface{}{"severity": "critical", "title": "X"},
		WebhookKindSlack, "https://hooks.slack.com/xyz")

	if notif.TenantID != "T01" || notif.WorkspaceID != "W01" {
		t.Fatalf("tenant/workspace taşınmadı: %+v", notif)
	}
	if notif.Channel != ChannelWebhook {
		t.Fatalf("channel = %s, want webhook", notif.Channel)
	}
	if notif.Type != NotificationType("incident.opened") {
		t.Fatalf("type = %s", notif.Type)
	}
	if notif.WebhookURL != "https://hooks.slack.com/xyz" || notif.WebhookKind != WebhookKindSlack {
		t.Fatalf("webhook alanları taşınmadı: %+v", notif)
	}
	if notif.Status != DeliveryPending {
		t.Fatalf("status = %s, want pending", notif.Status)
	}
}

func TestBuildPagerDutySeverityFromGovernancePayload(t *testing.T) {
	for _, tc := range []struct {
		severity string
		want     string
	}{
		{"critical", "critical"},
		{"warning", "warning"},
		{"high", "warning"},
		{"", "info"},
	} {
		notif := buildGovernanceNotification("T01", "W01", "drift.alert.triggered",
			map[string]interface{}{"severity": tc.severity, "metric": "m"},
			WebhookKindPagerDuty, "https://events.pagerduty.com/x")

		payload, _, err := buildWebhookPayload(notif)
		if err != nil {
			t.Fatalf("pagerduty payload: %v", err)
		}
		var pd map[string]interface{}
		if err := json.Unmarshal(payload, &pd); err != nil {
			t.Fatalf("pagerduty json: %v", err)
		}
		pdPayload := pd["payload"].(map[string]interface{})
		if pdPayload["severity"] != tc.want {
			t.Errorf("severity %q: got %v, want %v", tc.severity, pdPayload["severity"], tc.want)
		}
	}
}

func TestSendGovernanceEventNilPool(t *testing.T) {
	// pool yoksa (test ortamı) sessizce başarılı dönmeli — panic olmamalı
	s := &service{}
	if err := s.SendGovernanceEvent(t.Context(), "T01", "guardrail.violation", map[string]interface{}{"rule_id": "R1"}); err != nil {
		t.Fatalf("nil pool ile hata beklenmiyor: %v", err)
	}
}
