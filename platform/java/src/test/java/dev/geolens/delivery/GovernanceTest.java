package dev.geolens.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code delivery/governance_test.go} portu. */
class GovernanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void governanceEventMeta() {
        record Case(String eventType, Map<String, Object> payload, String wantTitle, String wantBodySub) {
        }

        Case[] cases = new Case[]{
                new Case("guardrail.violation",
                        Map.of("rule_name", "Email Leak", "category", "pii_leakage", "action_taken", "block"),
                        "Guardrail İhlali Tespit Edildi", "Email Leak"),
                new Case("gate.check.decision",
                        Map.of("entity_type", "prompt", "version", "v1.2", "decision", "blocked", "target_env", "production"),
                        "Gate Kontrol Kararı", "blocked"),
                new Case("incident.opened",
                        Map.of("severity", "critical", "title", "Skor düştü", "category", "visibility"),
                        "Yeni Olay Açıldı", "Skor düştü"),
                new Case("drift.alert.triggered",
                        Map.of("metric", "visibility_score", "drift_score", 62.5, "severity", "critical", "delta", 4.2),
                        "Drift Uyarısı", "visibility_score"),
                new Case("redteam.run.completed",
                        Map.of("target_name", "checkout-bot", "passed", 8.0, "failed", 2.0, "defense_score", 80.0),
                        "Red Team Çalışması Tamamlandı", "checkout-bot"),
                new Case("future.event.type",
                        Map.of("detail", "detay"),
                        "Yönetişim Olayı: future.event.type", "detay"),
        };

        for (Case c : cases) {
            DeliveryService.GovernanceMeta meta = DeliveryService.governanceEventMeta(c.eventType(), c.payload());
            assertEquals(c.wantTitle(), meta.title(), c.eventType() + " title");
            assertTrue(meta.body().contains(c.wantBodySub()), c.eventType() + " body: " + meta.body());
        }
    }

    @Test
    void buildGovernanceNotification() {
        Notification notif = DeliveryService.buildGovernanceNotification("T01", "W01", "incident.opened",
                Map.of("severity", "critical", "title", "X"),
                DeliveryConstants.WEBHOOK_KIND_SLACK, "https://hooks.slack.com/xyz");

        assertEquals("T01", notif.tenantId());
        assertEquals("W01", notif.workspaceId());
        assertEquals(DeliveryConstants.CHANNEL_WEBHOOK, notif.channel());
        assertEquals("incident.opened", notif.type());
        assertEquals("https://hooks.slack.com/xyz", notif.webhookUrl());
        assertEquals(DeliveryConstants.WEBHOOK_KIND_SLACK, notif.webhookKind());
        assertEquals(DeliveryConstants.DELIVERY_PENDING, notif.status());
    }

    @Test
    void buildPagerDutySeverityFromGovernancePayload() throws Exception {
        record T(String severity, String want) {
        }
        T[] tests = new T[]{
                new T("critical", "critical"),
                new T("warning", "warning"),
                new T("high", "warning"),
                new T("", "info"),
        };

        for (T t : tests) {
            Notification notif = DeliveryService.buildGovernanceNotification("T01", "W01", "drift.alert.triggered",
                    Map.of("severity", t.severity(), "metric", "m"),
                    DeliveryConstants.WEBHOOK_KIND_PAGERDUTY, "https://events.pagerduty.com/x");

            DeliveryService.WebhookPayload payload = DeliveryService.buildWebhookPayload(notif);
            Map<?, ?> pd = MAPPER.readValue(payload.body(), Map.class);
            Map<?, ?> pdPayload = (Map<?, ?>) pd.get("payload");
            assertEquals(t.want(), pdPayload.get("severity"), "severity " + t.severity());
        }
    }

    @Test
    void sendGovernanceEventNilPool() {
        // pool yoksa sessizce başarılı dönmeli — panic olmamalı
        DeliveryService s = new DeliveryService(EmailConfig.mock(), null, null);
        s.sendGovernanceEvent("T01", "guardrail.violation", Map.of("rule_id", "R1"));
    }
}