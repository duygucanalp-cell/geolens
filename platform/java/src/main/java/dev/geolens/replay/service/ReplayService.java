package dev.geolens.replay.service;

import dev.geolens.replay.DiffResult;
import dev.geolens.replay.ReplayEngine;
import dev.geolens.replay.Snapshot;
import dev.geolens.replay.web.CaptureRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversation Replay iş mantığı — Go {@code replay.handler} portu (FR-D12).
 * <p>Snapshot yakalama/karşılaştırma {@link ReplayEngine} ile yapılır, liste/okuma/
 * silme sorguları bu servistedir; controller yalnızca HTTP katmanıdır
 * (route'lar: GET /v1/workspaces/{ws}/replay, GET /replay/{snapshotId},
 * POST /replay/capture, DELETE /replay/{snapshotId}, GET /replay/compare).
 */
@Service
public class ReplayService {

    private final ReplayEngine engine;
    private final DSLContext dsl;

    public ReplayService(ReplayEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    public Snapshot captureSnapshot(String brandId, String prompt, String workspaceId, String tenantId) {
        try {
            return engine.captureSnapshot(brandId, prompt, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ReplayServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "snapshot alınamadı");
        }
    }

    public List<Map<String, Object>> listSnapshots(String workspaceId, String tenantId, String brandId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT cs.id, cs.brand_id, cs.prompt_text, cs.engine_name, cs.response_preview,
                           cs.content_hash, cs.s3_ref, cs.created_at
                    FROM replay.conversation_snapshots cs
                    JOIN config.brands b ON b.id = cs.brand_id
                    WHERE cs.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR cs.brand_id = ?)
                    ORDER BY cs.created_at DESC
                    LIMIT 100
                    """, tenantId, workspaceId, nz(brandId), nz(brandId)).intoMaps();
        } catch (RuntimeException e) {
            return List.of();
        }

        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("brand_id", str(r.get("brand_id")));
            item.put("prompt_text", str(r.get("prompt_text")));
            item.put("engine_name", str(r.get("engine_name")));
            item.put("response_preview", str(r.get("response_preview")));
            item.put("content_hash", str(r.get("content_hash")));
            item.put("s3_ref", r.get("s3_ref"));
            item.put("created_at", str(r.get("created_at")));
            snapshots.add(item);
        }

        return snapshots;
    }

    public Map<String, Object> getSnapshot(String workspaceId, String tenantId, String snapshotId) {
        Map<String, Object> s;
        try {
            Record rec = dsl.fetchOne("""
                    SELECT cs.id, cs.brand_id, cs.prompt_text, cs.engine_name,
                           COALESCE(cs.response_full, cs.response_preview) AS response_full,
                           cs.content_hash, cs.s3_ref, cs.created_at
                    FROM replay.conversation_snapshots cs
                    JOIN config.brands b ON b.id = cs.brand_id
                    WHERE cs.id = ? AND cs.tenant_id = ? AND b.workspace_id = ?
                    """, snapshotId, tenantId, workspaceId);
            s = rec == null ? null : rec.intoMap();
        } catch (RuntimeException e) {
            throw new ReplayServiceException(HttpStatus.NOT_FOUND, "snapshot bulunamadı");
        }
        if (s == null) {
            throw new ReplayServiceException(HttpStatus.NOT_FOUND, "snapshot bulunamadı");
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", str(s.get("id")));
        item.put("brand_id", str(s.get("brand_id")));
        item.put("prompt_text", str(s.get("prompt_text")));
        item.put("engine_name", str(s.get("engine_name")));
        item.put("response_full", str(s.get("response_full")));
        item.put("content_hash", str(s.get("content_hash")));
        item.put("s3_ref", s.get("s3_ref"));
        item.put("created_at", str(s.get("created_at")));
        return item;
    }

    public Map<String, Object> deleteSnapshot(String tenantId, String snapshotId) {
        int rows;
        try {
            rows = dsl.execute("DELETE FROM replay.conversation_snapshots WHERE id = ? AND tenant_id = ?",
                    snapshotId, tenantId);
        } catch (RuntimeException e) {
            throw new ReplayServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "silme başarısız");
        }
        if (rows == 0) {
            throw new ReplayServiceException(HttpStatus.NOT_FOUND, "snapshot bulunamadı");
        }
        return Map.of("status", "deleted");
    }

    public DiffResult compareSnapshots(String snapshotA, String snapshotB, String workspaceId, String tenantId) {
        try {
            return engine.compare(snapshotA, snapshotB, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ReplayServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "karşılaştırma başarısız");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
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
