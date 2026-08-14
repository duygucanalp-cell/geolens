package dev.geolens.replay;

import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

/**
 * Conversation replay motoru — Go {@code replay/engine.go} portu (FR-D12).
 * <p>Her motor için en son ham yanıtı alır, SHA-256 bütünlük hash'i + 500 karakter
 * önizleme üretir ve snapshot olarak kaydeder; iki snapshot'ı karşılaştırır.
 */
public class ReplayEngine {

    private final DSLContext dsl;

    public ReplayEngine(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Go {@code CaptureSnapshot} karşılığı: markanın motor bazlı en son yanıtını
     * snapshot'lar. Yanıt yoksa {@code IllegalArgumentException} fırlatır.
     */
    public Snapshot captureSnapshot(String brandId, String prompt, String workspaceId, String tenantId) {
        // Her motor için en son ham yanıt — DISTINCT ON (engine_name)
        java.util.List<Map<String, Object>> rows = dsl.fetch("""
                SELECT DISTINCT ON (rr.engine_name) rr.engine_name, rr.content_text, rr.id
                FROM measure.raw_responses rr
                WHERE rr.tenant_id = ? AND rr.brand_id = ?
                ORDER BY rr.engine_name, rr.created_at DESC
                """, tenantId, brandId).intoMaps();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("hiç yanıt bulunamadı");
        }

        Map<String, Object> resp = rows.get(0);
        String engineName = str(resp.get("engine_name"));
        String content = str(resp.get("content_text"));

        String hash = sha256Hex(content);
        String preview = content.length() > 500 ? content.substring(0, 500) : content;

        Snapshot snapshot = new Snapshot(
                Ulid.generate(), brandId, prompt, engineName, preview, content, hash,
                null, null, Instant.now().atOffset(ZoneOffset.UTC).toString());

        dsl.execute("""
                INSERT INTO replay.conversation_snapshots
                    (id, brand_id, prompt_text, engine_name, response_preview, response_full, content_hash, tenant_id, workspace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, snapshot.id(), snapshot.brandId(), snapshot.promptText(), snapshot.engineName(),
                snapshot.responsePreview(), snapshot.responseFull(), snapshot.contentHash(),
                tenantId, workspaceId, OffsetDateTime.parse(snapshot.createdAt()));

        return snapshot;
    }

    /**
     * Go {@code Compare} karşılığı: iki snapshot'ı karşılaştırır. Snapshot'lardan
     * biri bulunamazsa {@code IllegalArgumentException} fırlatır.
     */
    public DiffResult compare(String snapshotA, String snapshotB, String workspaceId, String tenantId) {
        Record ra = fetchSnapshot(snapshotA, workspaceId, tenantId);
        if (ra == null) {
            throw new IllegalArgumentException("snapshot A bulunamadı");
        }
        Record rb = fetchSnapshot(snapshotB, workspaceId, tenantId);
        if (rb == null) {
            throw new IllegalArgumentException("snapshot B bulunamadı");
        }

        Map<String, Object> a = ra.intoMap();
        Map<String, Object> b = rb.intoMap();
        String contentA = str(a.get("content"));
        String contentB = str(b.get("content"));

        boolean hasChanged = !contentA.equals(contentB);
        String changes = "";
        if (hasChanged) {
            changes = "Yanıt içeriği değişmiş. Detaylı karşılaştırma için snapshot'ların tam metinlerini inceleyin.";
        }

        return new DiffResult(
                snapshotA, snapshotB, str(a.get("brand_id")), str(a.get("engine_name")),
                str(a.get("prompt_text")), hasChanged, changes,
                OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private Record fetchSnapshot(String snapshotId, String workspaceId, String tenantId) {
        return dsl.fetchOne("""
                SELECT cs.brand_id, cs.prompt_text, cs.engine_name, COALESCE(cs.response_full, cs.response_preview) AS content
                FROM replay.conversation_snapshots cs
                JOIN config.brands b ON b.id = cs.brand_id
                WHERE cs.id = ? AND cs.tenant_id = ? AND b.workspace_id = ?
                """, snapshotId, tenantId, workspaceId);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }
}
