package dev.geolens.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code delivery/webhook_test.go} portu. */
class WebhookTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static DeliveryService svc() {
        return new DeliveryService(EmailConfig.mock(), null, null);
    }

    private static Notification notif(String channel, String kind, String title, String body, Map<String, Object> data) {
        return new Notification("n1", "t1", "", "w1", DeliveryConstants.NOTIFICATION_SCORE_DROP, channel,
                title, body, "", data, DeliveryConstants.DELIVERY_PENDING, null, Instant.now(), false, "", kind);
    }

    private static HttpServer server(int status, AtomicBoolean received, AtomicReference<String> contentType) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            if (received != null) {
                received.set(true);
            }
            if (contentType != null) {
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void buildWebhookPayloadFormats() throws Exception {
        Notification notif = notif(DeliveryConstants.CHANNEL_WEBHOOK, "", "Skor Düştü",
                "marka skoru 10 puan azaldı", Map.of("drop", 10));

        // generic
        DeliveryService.WebhookPayload payload = DeliveryService.buildWebhookPayload(notif);
        assertEquals("application/json", payload.contentType());
        Map<?, ?> generic = MAPPER.readValue(payload.body(), Map.class);
        assertEquals("Skor Düştü", generic.get("title"));

        // slack — text alanı var
        payload = DeliveryService.buildWebhookPayload(notif("", DeliveryConstants.WEBHOOK_KIND_SLACK, "T", "B", Map.of()));
        assertTrue(new String(payload.body(), StandardCharsets.UTF_8).contains("\"text\""));

        // teams — MessageCard sabitleri
        payload = DeliveryService.buildWebhookPayload(notif("", DeliveryConstants.WEBHOOK_KIND_TEAMS, "T", "B", Map.of()));
        assertTrue(new String(payload.body(), StandardCharsets.UTF_8).contains("MessageCard"));

        // discord — content alanı
        payload = DeliveryService.buildWebhookPayload(notif("", DeliveryConstants.WEBHOOK_KIND_DISCORD, "T", "B", Map.of()));
        assertTrue(new String(payload.body(), StandardCharsets.UTF_8).contains("\"content\""));

        // pagerduty — score_drop severity warning
        payload = DeliveryService.buildWebhookPayload(notif("", DeliveryConstants.WEBHOOK_KIND_PAGERDUTY, "T", "B", Map.of()));
        Map<?, ?> pd = MAPPER.readValue(payload.body(), Map.class);
        Map<?, ?> pdPayload = (Map<?, ?>) pd.get("payload");
        assertEquals("warning", pdPayload.get("severity"));
    }

    @Test
    void sendWebhookErrorHandling() throws Exception {
        DeliveryService s = svc();

        // URL yoksa hata
        assertThrows(DeliveryException.class,
                () -> s.sendWebhook(notif("", DeliveryConstants.WEBHOOK_KIND_SLACK, "T", "B", Map.of())));

        // non-2xx dönünce hata
        HttpServer server = server(500, null, null);
        try {
            Notification base = notif("", DeliveryConstants.WEBHOOK_KIND_SLACK, "T", "B", Map.of());
            Notification n = new Notification(base.id(), base.tenantId(), base.userId(), base.workspaceId(), base.type(),
                    base.channel(), base.title(), base.body(), base.htmlBody(), base.data(), base.status(), base.sentAt(),
                    base.createdAt(), base.isRead(),
                    "http://localhost:" + server.getAddress().getPort() + "/", base.webhookKind());
            assertThrows(DeliveryException.class, () -> s.sendWebhook(n));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendWebhookSuccess() throws Exception {
        AtomicBoolean received = new AtomicBoolean(false);
        AtomicReference<String> contentType = new AtomicReference<>();
        HttpServer server = server(200, received, contentType);
        try {
            DeliveryService s = svc();
            Notification base = notif("", DeliveryConstants.WEBHOOK_KIND_DISCORD, "T", "B", Map.of());
            Notification n = new Notification(base.id(), base.tenantId(), base.userId(), base.workspaceId(), base.type(),
                    base.channel(), base.title(), base.body(), base.htmlBody(), base.data(), base.status(), base.sentAt(),
                    base.createdAt(), base.isRead(),
                    "http://localhost:" + server.getAddress().getPort() + "/", base.webhookKind());
            s.sendWebhook(n);
            assertTrue(received.get(), "server webhook almadı");
            assertEquals("application/json", contentType.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendNotificationWebhookChannel() throws Exception {
        HttpServer server = server(200, null, null);
        try {
            DeliveryService s = svc();
            Notification base = notif(DeliveryConstants.CHANNEL_WEBHOOK, DeliveryConstants.WEBHOOK_KIND_GENERIC, "T", "B", Map.of());
            Notification n = new Notification(base.id(), base.tenantId(), base.userId(), base.workspaceId(), base.type(),
                    base.channel(), base.title(), base.body(), base.htmlBody(), base.data(), base.status(), base.sentAt(),
                    base.createdAt(), base.isRead(),
                    "http://localhost:" + server.getAddress().getPort() + "/", base.webhookKind());
            s.sendNotification(n);
        } finally {
            server.stop(0);
        }
    }
}