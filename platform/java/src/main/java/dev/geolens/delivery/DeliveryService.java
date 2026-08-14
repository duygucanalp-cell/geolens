package dev.geolens.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Teslimat servisi — Go {@code delivery.service} portu (HT2).
 * <p>Kanallar: e-posta (SendGrid — mock key ile devre dışı), webhook (Slack/Teams/Discord/
 * PagerDuty/custom), in-app (DB). Webhook payload üretimi ve digest HTML saf fonksiyonlardır;
 * DB yoksa (null pool) governance yayını no-op, in-app işlemleri {@link DeliveryException} ile
 * başarısız olur (Go nil-pool davranışı).
 */
public final class DeliveryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration WEBHOOK_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final EmailConfig config;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final HttpClient webhookClient;

    public DeliveryService(EmailConfig config, JdbcTemplate jdbc, TransactionTemplate tx) {
        this(config, jdbc, tx, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    public DeliveryService(EmailConfig config, JdbcTemplate jdbc, TransactionTemplate tx, HttpClient webhookClient) {
        this.config = config != null ? config : EmailConfig.mock();
        this.jdbc = jdbc;
        this.tx = tx;
        this.webhookClient = webhookClient;
    }

    private static void setTenant(JdbcTemplate jdbc, String tenantId) {
        jdbc.execute("SELECT set_config('app.tenant_id', ?, true)",
                (PreparedStatementCallback<Void>) ps -> {
                    ps.setString(1, tenantId);
                    ps.execute();
                    return null;
                });
    }

    private void runInTenant(String tenantId, Runnable work) {
        if (tx == null) {
            setTenant(jdbc, tenantId);
            work.run();
            return;
        }
        tx.executeWithoutResult(status -> {
            setTenant(jdbc, tenantId);
            work.run();
        });
    }

    /**
     * Tek bir bildirimi gönderir — Go {@code SendNotification} portu.
     * Desteklenen kanallar: email, webhook, in-app. Güncellenmiş bildirimi (status/sentAt) döner.
     */
    public Notification sendNotification(Notification notif) {
        switch (notif.channel()) {
            case DeliveryConstants.CHANNEL_EMAIL -> {
                return sendEmailNotification(notif);
            }
            case DeliveryConstants.CHANNEL_WEBHOOK -> {
                sendWebhook(notif);
                return notif.sent();
            }
            case DeliveryConstants.CHANNEL_IN_APP -> {
                return saveInAppNotification(notif);
            }
            default -> throw new DeliveryException("delivery: bilinmeyen kanal: " + notif.channel());
        }
    }

    /**
     * SendGrid ile düz e-posta gönderir — Go {@code SendEmail} portu.
     * Anahtar yoksa veya "mock" ise mock mod (gönderim yok, log benzeri no-op).
     */
    public void sendEmail(String to, String subject, String htmlContent) {
        if (config.sendGridKey() == null || config.sendGridKey().isEmpty() || "mock".equals(config.sendGridKey())) {
            // mock email sent
            return;
        }
        // Gerçek SendGrid çağrısı spike kapsamı dışındadır — key varsa HTTP POST ile
        // /v3/mail/send uç noktasına gidilir. Başarısızlık DeliveryException ile yayılır.
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", Map.of("email", config.fromEmail(), "name", config.fromName()));
            body.put("subject", subject);
            body.put("content", List.of(Map.of("type", "text/html", "value", htmlContent)));
            body.put("personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + config.sendGridKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = webhookClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new DeliveryException("delivery: sendgrid hatası (HTTP " + resp.statusCode() + "): " + resp.body());
            }
        } catch (IOException e) {
            throw new DeliveryException("delivery: sendgrid gönderme hatası: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("delivery: sendgrid gönderme iptal", e);
        }
    }

    // ---- Webhook (HT2 webhook çeşitlendirme) ----

    /** Webhook payload + content-type — Go {@code ([]byte, string)} çoklu dönüş portu. */
    record WebhookPayload(byte[] body, String contentType) {
    }

    /**
     * Bildirimi harici webhook uç noktasına teslim eder — Go {@code SendWebhook} portu.
     * URL yoksa veya HTTP >= 300 dönerse {@link DeliveryException} fırlatır.
     */
    public void sendWebhook(Notification notif) {
        if (notif.webhookUrl() == null || notif.webhookUrl().isEmpty()) {
            throw new DeliveryException("delivery: webhook URL gerekli");
        }

        WebhookPayload payload = buildWebhookPayload(notif);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(notif.webhookUrl()))
                    .timeout(WEBHOOK_TIMEOUT)
                    .header("Content-Type", payload.contentType())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload.body()))
                    .build();
            HttpResponse<Void> resp = webhookClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() >= 300) {
                throw new DeliveryException("delivery: webhook hatası (HTTP " + resp.statusCode() + ")");
            }
        } catch (IOException e) {
            throw new DeliveryException("delivery: webhook çağrısı: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("delivery: webhook çağrısı iptal", e);
        }
    }

    /** Webhook türüne göre platforma özgü JSON payload üretir — Go {@code buildWebhookPayload} portu. */
    static WebhookPayload buildWebhookPayload(Notification notif) {
        return switch (notif.webhookKind()) {
            case DeliveryConstants.WEBHOOK_KIND_SLACK -> buildSlackPayload(notif);
            case DeliveryConstants.WEBHOOK_KIND_TEAMS -> buildTeamsPayload(notif);
            case DeliveryConstants.WEBHOOK_KIND_DISCORD -> buildDiscordPayload(notif);
            case DeliveryConstants.WEBHOOK_KIND_PAGERDUTY -> buildPagerDutyPayload(notif);
            default -> buildGenericPayload(notif);
        };
    }

    private static WebhookPayload buildGenericPayload(Notification notif) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", notif.type());
        payload.put("title", notif.title());
        payload.put("body", notif.body());
        payload.put("workspace", notif.workspaceId());
        payload.put("tenant", notif.tenantId());
        payload.put("sent_at", Instant.now().toString());
        payload.put("data", notif.data());
        return marshal(payload, "application/json");
    }

    private static WebhookPayload buildSlackPayload(Notification notif) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", "*" + notif.title() + "*\n" + notif.body());
        return marshal(payload, "application/json");
    }

    private static WebhookPayload buildTeamsPayload(Notification notif) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("@type", "MessageCard");
        payload.put("@context", "http://schema.org/extensions");
        payload.put("summary", notif.title());
        payload.put("title", notif.title());
        payload.put("text", notif.body());
        return marshal(payload, "application/json");
    }

    private static WebhookPayload buildDiscordPayload(Notification notif) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", "**" + notif.title() + "**\n" + notif.body());
        return marshal(payload, "application/json");
    }

    private static WebhookPayload buildPagerDutyPayload(Notification notif) {
        String severity = "info";
        if (DeliveryConstants.NOTIFICATION_SCORE_DROP.equals(notif.type())) {
            severity = "warning";
        }
        // Governance olayları: payload severity (critical/warning/high) PagerDuty severity'ye yansır
        Object sev = notif.data().get("severity");
        if (sev instanceof String s && !s.isEmpty()) {
            switch (s) {
                case "critical" -> severity = "critical";
                case "warning", "high" -> severity = "warning";
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routing_key", "geolens-alert");
        payload.put("event_action", "trigger");
        Map<String, Object> pd = new LinkedHashMap<>();
        pd.put("summary", notif.title());
        pd.put("source", "geolens-platform");
        pd.put("severity", severity);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("body", notif.body());
        details.put("workspace", notif.workspaceId());
        details.put("tenant", notif.tenantId());
        pd.put("custom_details", details);
        payload.put("payload", pd);
        return marshal(payload, "application/json");
    }

    private static WebhookPayload marshal(Map<String, Object> payload, String contentType) {
        try {
            return new WebhookPayload(MAPPER.writeValueAsBytes(payload), contentType);
        } catch (IOException e) {
            throw new DeliveryException("delivery: webhook payload oluşturma: " + e.getMessage(), e);
        }
    }

    /** Faz 4 governance olayından webhook Notification'ı üretir — Go {@code buildGovernanceNotification} portu. */
    static Notification buildGovernanceNotification(String tenantId, String workspaceId, String eventType,
                                                    Map<String, Object> payload, String kind, String url) {
        GovernanceMeta meta = governanceEventMeta(eventType, payload);
        return new Notification("", tenantId, "", workspaceId, eventType, DeliveryConstants.CHANNEL_WEBHOOK,
                meta.title(), meta.body(), "", payload, DeliveryConstants.DELIVERY_PENDING, null, null, false, url, kind);
    }

    record GovernanceMeta(String title, String body) {
    }

    /** Governance olay tipini okunur başlık/gövdeye çevirir — Go {@code governanceEventMeta} portu. */
    static GovernanceMeta governanceEventMeta(String eventType, Map<String, Object> payload) {
        java.util.function.Function<String, String> str = k -> {
            Object v = payload.get(k);
            return v instanceof String s ? s : "";
        };
        java.util.function.Function<String, Double> num = k -> {
            Object v = payload.get(k);
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            return 0.0;
        };

        String body;
        String title;
        switch (eventType) {
            case "guardrail.violation" -> {
                body = "Kural: " + str.apply("rule_name");
                String category = str.apply("category");
                if (!category.isEmpty()) {
                    body += " | Kategori: " + category;
                }
                String action = str.apply("action_taken");
                if (!action.isEmpty()) {
                    body += " | Aksiyon: " + action;
                }
                title = "Guardrail İhlali Tespit Edildi";
            }
            case "gate.check.decision" -> {
                title = "Gate Kontrol Kararı";
                body = String.format("%s %s sürümü → %s (%s)", str.apply("entity_type"), str.apply("version"),
                        str.apply("decision"), str.apply("target_env"));
            }
            case "incident.opened" -> {
                title = "Yeni Olay Açıldı";
                body = String.format("[%s] %s (%s)", str.apply("severity"), str.apply("title"), str.apply("category"));
            }
            case "drift.alert.triggered" -> {
                title = "Drift Uyarısı";
                body = String.format("%s: skor %.2f (%s, delta %.2f)", str.apply("metric"), num.apply("drift_score"),
                        str.apply("severity"), num.apply("delta"));
            }
            case "redteam.run.completed" -> {
                title = "Red Team Çalışması Tamamlandı";
                body = String.format("Hedef: %s | Geçen: %.0f | Kalan: %.0f | Savunma Skoru: %.0f",
                        str.apply("target_name"), num.apply("passed"), num.apply("failed"), num.apply("defense_score"));
            }
            default -> {
                title = "Yönetişim Olayı: " + eventType;
                body = str.apply("detail");
                if (body.isEmpty()) {
                    body = "Olay detayları için panoyu kontrol edin.";
                }
            }
        }
        return new GovernanceMeta(title, body);
    }

    /**
     * Faz 4 governance olayını webhook-aktif tüm workspace'lere iletir — Go {@code SendGovernanceEvent} portu.
     * Best-effort: tek hedef başarısız olursa diğerleri gönderilir; hedef yoksa sessizce döner.
     */
    public void sendGovernanceEvent(String tenantId, String eventType, Map<String, Object> payload) {
        if (jdbc == null) {
            return;
        }

        List<String[]> targets = new ArrayList<>();
        if (tx != null) {
            targets = tx.execute(status -> {
                setTenant(jdbc, tenantId);
                return queryWebhookTargets(tenantId);
            });
        } else {
            setTenant(jdbc, tenantId);
            targets = queryWebhookTargets(tenantId);
        }

        for (String[] t : targets) {
            Notification notif = buildGovernanceNotification(tenantId, t[0], eventType, payload, t[1], t[2]);
            try {
                sendWebhook(notif);
            } catch (DeliveryException e) {
                // best-effort: hata loglanır, diğer hedefler devam eder
                continue;
            }
        }
    }

    private List<String[]> queryWebhookTargets(String tenantId) {
        return jdbc.query("""
                SELECT workspace_id, webhook_url, webhook_kind
                FROM delivery.notification_settings
                WHERE tenant_id = ? AND webhook_active = true AND webhook_url <> ''
                """,
                (rs, rowNum) -> new String[]{
                        rs.getString("workspace_id"),
                        rs.getString("webhook_kind"),
                        rs.getString("webhook_url")},
                tenantId);
    }

    // ---- Ayarlar ----

    /** Workspace bildirim ayarlarını döner; kayıt yoksa varsayılanlar — Go {@code GetSettings} portu. */
    public NotificationSettings getSettings(String workspaceId, String tenantId) {
        if (jdbc == null) {
            return NotificationSettings.defaults(workspaceId);
        }
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT email_address, digest_enabled, digest_day, digest_time, digest_format,
                           notify_on_drop, drop_threshold, webhook_url, webhook_kind, webhook_active
                    FROM delivery.notification_settings
                    WHERE workspace_id = ? AND tenant_id = ?
                    """, workspaceId, tenantId);
        } catch (EmptyResultDataAccessException e) {
            return NotificationSettings.defaults(workspaceId);
        }
        return new NotificationSettings(workspaceId,
                (String) row.get("email_address"),
                (Boolean) row.get("digest_enabled"),
                (String) row.get("digest_day"),
                (String) row.get("digest_time"),
                (String) row.get("digest_format"),
                (Boolean) row.get("notify_on_drop"),
                ((Number) row.get("drop_threshold")).intValue(),
                (String) row.get("webhook_url"),
                (String) row.get("webhook_kind"),
                (Boolean) row.get("webhook_active"));
    }

    /** Ayarları doğrular ve kaydeder — Go {@code UpdateSettings} portu. */
    public void updateSettings(NotificationSettings settings, String tenantId) {
        validateSettings(settings);
        if (jdbc == null) {
            return;
        }
        runInTenant(tenantId, () -> jdbc.update("""
                INSERT INTO delivery.notification_settings
                    (workspace_id, tenant_id, email_address, digest_enabled, digest_day,
                     digest_time, digest_format, notify_on_drop, drop_threshold,
                     webhook_url, webhook_kind, webhook_active, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (workspace_id, tenant_id) DO UPDATE SET
                    email_address = EXCLUDED.email_address,
                    digest_enabled = EXCLUDED.digest_enabled,
                    digest_day = EXCLUDED.digest_day,
                    digest_time = EXCLUDED.digest_time,
                    digest_format = EXCLUDED.digest_format,
                    notify_on_drop = EXCLUDED.notify_on_drop,
                    drop_threshold = EXCLUDED.drop_threshold,
                    webhook_url = EXCLUDED.webhook_url,
                    webhook_kind = EXCLUDED.webhook_kind,
                    webhook_active = EXCLUDED.webhook_active,
                    updated_at = now()
                """,
                settings.workspaceId(), tenantId, settings.emailAddress(), settings.digestEnabled(),
                settings.digestDay(), settings.digestTime(), settings.digestFormat(),
                settings.notifyOnDrop(), settings.dropThreshold(),
                settings.webhookUrl(), settings.webhookKind(), settings.webhookActive()));
    }

    /** Ayarları doğrular; geçersizse {@link ValidationException} — Go {@code ValidateSettings} portu. */
    static void validateSettings(NotificationSettings s) {
        if (s.emailAddress() == null || s.emailAddress().isEmpty()) {
            throw new ValidationException("e-posta adresi gerekli");
        }

        // digest_day (zorunlu)
        if (s.digestDay() == null || s.digestDay().isEmpty()) {
            throw new ValidationException("gün gerekli (monday-sunday)");
        }
        if (!NotificationSettings.VALID_DAYS.contains(s.digestDay())) {
            throw new ValidationException("geçersiz gün: " + s.digestDay() + " (pazartesi-pazar arası olmalı, İngilizce)");
        }

        // digest_time (HH:mm 24s, zorunlu)
        if (s.digestTime() == null || s.digestTime().isEmpty()) {
            throw new ValidationException("saat gerekli (HH:mm)");
        }
        String time = s.digestTime();
        if (time.length() != 5 || time.charAt(2) != ':') {
            throw new ValidationException("geçersiz saat: " + s.digestTime() + " (HH:mm formatında olmalı, örn: 09:00)");
        }
        int h = Character.digit(time.charAt(0), 10) * 10 + Character.digit(time.charAt(1), 10);
        int m = Character.digit(time.charAt(3), 10) * 10 + Character.digit(time.charAt(4), 10);
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            throw new ValidationException("geçersiz saat: " + s.digestTime() + " (saat 00-23, dakika 00-59 arası olmalı)");
        }

        // digest_format (zorunlu)
        if (s.digestFormat() == null || s.digestFormat().isEmpty()) {
            throw new ValidationException("format gerekli (email, pdf veya both)");
        }
        if (!NotificationSettings.VALID_DIGEST_FORMATS.contains(s.digestFormat())) {
            throw new ValidationException("geçersiz format: " + s.digestFormat() + " (email, pdf veya both olmalı)");
        }

        // drop_threshold
        if (s.dropThreshold() < 1 || s.dropThreshold() > 100) {
            throw new ValidationException("düşüş eşiği 1-100 arası olmalı");
        }

        // Webhook ayarları
        if (s.webhookActive()) {
            if (s.webhookUrl() == null || s.webhookUrl().isEmpty()) {
                throw new ValidationException("webhook aktifken webhook URL'si gerekli");
            }
            if (s.webhookKind() == null || s.webhookKind().isEmpty()) {
                throw new ValidationException("webhook aktifken webhook türü gerekli (generic, slack, teams, discord, pagerduty)");
            }
            if (!NotificationSettings.VALID_WEBHOOK_KINDS.contains(s.webhookKind())) {
                throw new ValidationException("geçersiz webhook türü: " + s.webhookKind()
                        + " (generic, slack, teams, discord veya pagerduty olmalı)");
            }
        }
    }

    // ---- Haftalık özet ----

    /**
     * Workspace için haftalık özet e-postası gönderir — Go {@code SendWeeklyDigest} portu.
     * DB yoksa boş skor/öneri ile özet üretilir.
     */
    public void sendWeeklyDigest(String workspaceId, String tenantId) {
        String subject = "GeoLens Haftalık Özet — " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        List<DigestBrandScore> brands = new ArrayList<>();
        List<DigestRecommendation> recs = new ArrayList<>();
        if (jdbc != null) {
            try {
                brands = loadDigestScores(workspaceId, tenantId);
            } catch (Exception e) {
                brands = new ArrayList<>();
            }
            try {
                recs = loadDigestRecommendations(workspaceId, tenantId);
            } catch (Exception e) {
                recs = new ArrayList<>();
            }
        }

        String htmlContent = buildDigestHTML(subject, brands, recs, workspaceId, tenantId);

        String toEmail = getSettings(workspaceId, tenantId).emailAddress();
        if (toEmail == null || toEmail.isEmpty()) {
            toEmail = "user@example.com";
        }

        sendEmail(toEmail, subject, htmlContent);
    }

    private List<DigestBrandScore> loadDigestScores(String workspaceId, String tenantId) {
        return jdbc.query("""
                SELECT DISTINCT ON (b.id)
                    b.id AS brand_id,
                    b.name AS brand_name,
                    s.value AS score,
                    LAG(s.value) OVER (PARTITION BY b.id ORDER BY s.freshness_at) AS prev_score
                FROM config.brands b
                JOIN measure.scores s ON s.brand_id = b.id
                WHERE b.workspace_id = ? AND b.tenant_id = ? AND b.is_active = true
                ORDER BY b.id, s.freshness_at DESC
                """,
                (rs, rowNum) -> {
                    double score = rs.getDouble("score");
                    double prev = rs.getDouble("prev_score");
                    boolean hasPrev = rs.getObject("prev_score") != null;
                    double change = hasPrev ? score - prev : 0;
                    return new DigestBrandScore(rs.getString("brand_id"), rs.getString("brand_name"),
                            score, hasPrev ? prev : 0, change);
                },
                workspaceId, tenantId);
    }

    private List<DigestRecommendation> loadDigestRecommendations(String workspaceId, String tenantId) {
        return jdbc.query("""
                SELECT b.name, r.title, r.detail
                FROM recommendation.results r
                JOIN config.brands b ON b.id = r.brand_id
                WHERE r.workspace_id = ? AND r.tenant_id = ?
                ORDER BY r.created_at DESC
                LIMIT 5
                """,
                (rs, rowNum) -> new DigestRecommendation(rs.getString("name"), rs.getString("title"),
                        rs.getString("detail")),
                workspaceId, tenantId);
    }

    /** Haftalık özet için HTML içerik üretir — Go {@code buildDigestHTML} portu (pano derin bağlantılı). */
    static String buildDigestHTML(String subject, List<DigestBrandScore> brands, List<DigestRecommendation> recs,
                                  String workspaceId, String tenantId) {
        String dashboardUrl = "https://app.geolens.ai/v1/workspaces/" + workspaceId + "/dashboard";
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head><meta charset=\"utf-8\"><style>\n");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f1f5f9; margin: 0; padding: 0; }\n");
        html.append(".container { max-width: 600px; margin: 0 auto; padding: 20px; }\n");
        html.append(".header { background: linear-gradient(135deg, #6366f1, #4f46e5); color: white; padding: 24px; border-radius: 12px 12px 0 0; text-align: center; }\n");
        html.append(".header h1 { margin: 0; font-size: 22px; }\n");
        html.append(".header p { margin: 8px 0 0; opacity: 0.9; font-size: 14px; }\n");
        html.append(".section { background: white; padding: 20px; border-bottom: 1px solid #e2e8f0; }\n");
        html.append(".section:last-child { border-radius: 0 0 12px 12px; }\n");
        html.append(".section h2 { font-size: 16px; margin: 0 0 12px; color: #1e293b; }\n");
        html.append(".score-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f1f5f9; }\n");
        html.append(".score-row:last-child { border-bottom: none; }\n");
        html.append(".brand-name { font-weight: 600; color: #334155; }\n");
        html.append(".score-value { font-weight: 700; color: #6366f1; }\n");
        html.append(".change-up { color: #22c55e; }\n");
        html.append(".change-down { color: #ef4444; }\n");
        html.append(".change-neutral { color: #94a3b8; }\n");
        html.append(".rec-item { padding: 8px 0; border-bottom: 1px solid #f1f5f9; font-size: 14px; color: #475569; }\n");
        html.append(".rec-item:last-child { border-bottom: none; }\n");
        html.append(".rec-brand { font-weight: 600; color: #6366f1; }\n");
        html.append(".footer { text-align: center; padding: 20px; font-size: 12px; color: #94a3b8; }\n");
        html.append(".btn { display: inline-block; padding: 10px 20px; background: #6366f1; color: white; text-decoration: none; border-radius: 8px; font-size: 14px; margin-top: 12px; }\n");
        html.append(".empty-state { text-align: center; padding: 20px; color: #94a3b8; font-size: 14px; }\n");
        html.append("</style></head>\n<body>\n<div class=\"container\">\n");
        html.append("  <div class=\"header\">\n    <h1>GeoLens Haftalık Özet</h1>\n    <p>").append(today).append("</p>\n  </div>\n");
        html.append("  <div class=\"section\">\n    <h2>📊 Görünürlük Skorları</h2>\n");

        if (brands == null || brands.isEmpty()) {
            html.append("<div class=\"empty-state\">Henüz ölçüm yapılmamış. İlk ölçümünüzü başlatmak için panoya gidin.</div>\n");
        } else {
            for (DigestBrandScore b : brands) {
                String changeHtml;
                if (b.change() > 0) {
                    changeHtml = String.format("<span class=\"change-up\">↑%.0f</span>", b.change());
                } else if (b.change() < 0) {
                    changeHtml = String.format("<span class=\"change-down\">↓%.0f</span>", -b.change());
                } else {
                    changeHtml = "<span class=\"change-neutral\">—</span>";
                }
                String brandUrl = dashboardUrl + "?brand=" + b.brandId();
                html.append("    <div class=\"score-row\">\n");
                html.append("      <a href=\"").append(brandUrl).append("\" style=\"text-decoration:none;color:inherit;\"><span class=\"brand-name\">");
                html.append(escapeHtml(b.brandName())).append("</span></a>\n");
                html.append("      <span><span class=\"score-value\">").append(String.format("%.0f", b.score()))
                        .append("</span> ").append(changeHtml).append("</span>\n");
                html.append("    </div>\n");
            }
        }

        html.append("    <a href=\"").append(dashboardUrl).append("\" class=\"btn\">Panoda Görüntüle</a>\n");
        html.append("  </div>\n  <div class=\"section\">\n    <h2>💡 Öneriler</h2>\n");

        if (recs == null || recs.isEmpty()) {
            html.append("<div class=\"empty-state\">Henüz öneri bulunmuyor.</div>\n");
        } else {
            for (DigestRecommendation r : recs) {
                html.append("    <div class=\"rec-item\"><span class=\"rec-brand\">").append(escapeHtml(r.brandName()))
                        .append(":</span> ").append(escapeHtml(r.detail())).append("</div>\n");
            }
        }

        html.append("  </div>\n");
        html.append("  <div class=\"footer\">\n");
        html.append("    Bu e-posta GeoLens AI Visibility Platform tarafından otomatik gönderilmiştir.<br>\n");
        html.append("    <a href=\"").append(dashboardUrl).append("\" style=\"color:#6366f1;\">Panoya Git</a>\n");
        html.append("  </div>\n</div>\n</body>\n</html>\n");

        return html.toString();
    }

    /** HTML özel karakterlerini kaçışlar — Go {@code escapeHTML} portu. */
    static String escapeHtml(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---- In-app (FR-D10) ----

    /** In-app bildirimi kalıcı tabloya yazar — Go {@code saveInAppNotification} portu. */
    Notification saveInAppNotification(Notification notif) {
        if (jdbc == null) {
            throw new DeliveryException("delivery: veritabanı bağlantısı yok");
        }

        String dataJson = "{}";
        if (notif.data() != null && !notif.data().isEmpty()) {
            try {
                dataJson = MAPPER.writeValueAsString(notif.data());
            } catch (IOException ignored) {
                // bozuk data → "{}"
            }
        }

        String id = notif.id() == null || notif.id().isEmpty() ? dev.geolens.util.Ulid.generate() : notif.id();
        String data = dataJson;
        runInTenant(notif.tenantId(), () -> jdbc.update("""
                INSERT INTO delivery.notifications (id, tenant_id, workspace_id, user_id, type, title, body, data, is_read, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, false, now())
                """, id, notif.tenantId(), notif.workspaceId(), notif.userId(),
                notif.type(), notif.title(), notif.body(), data));
        return notif.sent();
    }

    /** Workspace'in in-app bildirimlerini listeler — Go {@code ListInAppNotifications} portu. */
    public List<Notification> listInAppNotifications(String tenantId, String workspaceId, boolean unreadOnly, int limit) {
        if (jdbc == null) {
            throw new DeliveryException("delivery: veritabanı bağlantısı yok");
        }
        if (limit <= 0 || limit > 100) {
            limit = 50;
        }
        String query = """
                SELECT id, COALESCE(user_id, ''), type, title, body, data, is_read, created_at
                FROM delivery.notifications
                WHERE tenant_id = ? AND workspace_id = ?
                """ + (unreadOnly ? " AND is_read = false" : "") + " ORDER BY created_at DESC LIMIT ?";
        int lim = limit;
        return jdbc.query(query,
                (rs, rowNum) -> {
                    Map<String, Object> data = Map.of();
                    try {
                        String raw = rs.getString("data");
                        if (raw != null && !raw.equals("{}")) {
                            data = MAPPER.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
                        }
                    } catch (IOException ignored) {
                        // bozuk data → boş harita
                    }
                    return new Notification(
                            rs.getString("id"),
                            tenantId,
                            rs.getString("user_id"),
                            workspaceId,
                            rs.getString("type"),
                            DeliveryConstants.CHANNEL_IN_APP,
                            rs.getString("title"),
                            rs.getString("body"),
                            "",
                            data,
                            DeliveryConstants.DELIVERY_SENT,
                            null,
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getBoolean("is_read"),
                            "",
                            "");
                },
                tenantId, workspaceId, lim);
    }

    /** Tek bir in-app bildirimi okundu işaretler — Go {@code MarkInAppNotificationRead} portu. */
    public void markInAppNotificationRead(String tenantId, String notificationId) {
        if (jdbc == null) {
            throw new DeliveryException("delivery: veritabanı bağlantısı yok");
        }
        runInTenant(tenantId, () -> jdbc.update("""
                UPDATE delivery.notifications SET is_read = true
                WHERE id = ? AND tenant_id = ?
                """, notificationId, tenantId));
    }

    private Notification sendEmailNotification(Notification notif) {
        String htmlContent = notif.htmlBody();
        if (htmlContent == null || htmlContent.isEmpty()) {
            htmlContent = "<h2>" + notif.title() + "</h2><p>" + notif.body() + "</p>";
        }
        sendEmail(notif.userId() + "@example.com", notif.title(), htmlContent);
        return notif.sent();
    }
}