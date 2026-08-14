package dev.geolens.replay.web;

import dev.geolens.replay.DiffResult;
import dev.geolens.replay.ReplayEngine;
import dev.geolens.replay.Snapshot;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversation Replay REST controller'ı — Go {@code replay.handler} portu (FR-D12).
 * <p>Route'lar (go cmd/api): GET /v1/workspaces/{ws}/replay, GET /replay/{snapshotId},
 * POST /replay/capture, DELETE /replay/{snapshotId}, GET /replay/compare.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir; snapshot'lar
 * KVKK uyumlu silinir (FR-D12).
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/replay")
public class ReplayController {

    private final ReplayEngine engine;
    private final DSLContext dsl;

    public ReplayController(ReplayEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    // ---------- CaptureSnapshot ----------

    @PostMapping("/capture")
    public ResponseEntity<?> captureSnapshot(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestBody CaptureRequest req) {
        if (req.brandId() == null || req.brandId().isBlank() || req.prompt() == null || req.prompt().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve prompt zorunludur");
        }

        Snapshot snapshot;
        try {
            snapshot = engine.captureSnapshot(req.brandId(), req.prompt(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "snapshot alınamadı");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(snapshot);
    }

    // ---------- ListSnapshots ----------

    @GetMapping
    public ResponseEntity<?> listSnapshots(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(value = "brand_id", required = false) String brandId) {
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
            return ResponseEntity.ok(List.of());
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

        return ResponseEntity.ok(snapshots);
    }

    // ---------- GetSnapshot ----------

    @GetMapping("/{snapshotId}")
    public ResponseEntity<?> getSnapshot(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String snapshotId) {
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
            return error(HttpStatus.NOT_FOUND, "snapshot bulunamadı");
        }
        if (s == null) {
            return error(HttpStatus.NOT_FOUND, "snapshot bulunamadı");
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
        return ResponseEntity.ok(item);
    }

    // ---------- DeleteSnapshot ----------

    @DeleteMapping("/{snapshotId}")
    public ResponseEntity<?> deleteSnapshot(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String snapshotId) {
        int rows;
        try {
            rows = dsl.execute("DELETE FROM replay.conversation_snapshots WHERE id = ? AND tenant_id = ?",
                    snapshotId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "silme başarısız");
        }
        if (rows == 0) {
            return error(HttpStatus.NOT_FOUND, "snapshot bulunamadı");
        }
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ---------- CompareSnapshots ----------

    @GetMapping("/compare")
    public ResponseEntity<?> compareSnapshots(@PathVariable String workspaceId,
                                              @RequestHeader("X-Tenant-ID") String tenantId,
                                              @RequestParam(value = "snapshot_a", required = false) String snapshotA,
                                              @RequestParam(value = "snapshot_b", required = false) String snapshotB) {
        if (snapshotA == null || snapshotA.isBlank() || snapshotB == null || snapshotB.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "snapshot_a ve snapshot_b gerekli");
        }

        DiffResult diff;
        try {
            diff = engine.compare(snapshotA, snapshotB, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "karşılaştırma başarısız");
        }

        return ResponseEntity.ok(diff);
    }

    // ---------- yardımcılar ----------

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
