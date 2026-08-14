package dev.geolens.archive.web;

import dev.geolens.archive.ArchiveEngine;
import dev.geolens.archive.Entry;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * Response Archive REST controller'ı — Go {@code archive.handler} portu (FR-D13).
 * <p>Route'lar (go cmd/api): GET /v1/workspaces/{ws}/archive, GET /archive/{entryId},
 * POST /archive, GET /archive/versions.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir; yanıtlar
 * SHA-256 hash + versiyonlama ile arşivlenir.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/archive")
public class ArchiveController {

    private final ArchiveEngine engine;
    private final DSLContext dsl;

    public ArchiveController(ArchiveEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    // ---------- ListEntries ----------

    @GetMapping
    public ResponseEntity<?> listEntries(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestParam(value = "brand_id", required = false) String brandId,
                                         @RequestParam(value = "engine", required = false) String engineName,
                                         @RequestParam(value = "version", required = false) String versionStr) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT ae.id, ae.brand_id, ae.engine_name, ae.prompt_text, ae.response_preview,
                           ae.s3_ref, ae.version, ae.content_hash, ae.created_at
                    FROM archive.response_entries ae
                    JOIN config.brands b ON b.id = ae.brand_id
                    WHERE ae.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR ae.brand_id = ?)
                        AND (? = '' OR ae.engine_name = ?)
                        AND (? = '' OR ae.version = ?::int)
                    ORDER BY ae.created_at DESC
                    LIMIT 100
                    """, tenantId, workspaceId, nz(brandId), nz(brandId),
                    nz(engineName), nz(engineName), nz(versionStr), nz(versionStr)).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("brand_id", str(r.get("brand_id")));
            item.put("engine_name", str(r.get("engine_name")));
            item.put("prompt_text", str(r.get("prompt_text")));
            item.put("response_preview", str(r.get("response_preview")));
            item.put("s3_ref", r.get("s3_ref"));
            item.put("version", num(r.get("version")));
            item.put("content_hash", str(r.get("content_hash")));
            item.put("created_at", str(r.get("created_at")));
            entries.add(item);
        }

        return ResponseEntity.ok(entries);
    }

    // ---------- GetEntry ----------

    @GetMapping("/{entryId}")
    public ResponseEntity<?> getEntry(@PathVariable String workspaceId,
                                      @RequestHeader("X-Tenant-ID") String tenantId,
                                      @PathVariable String entryId) {
        Map<String, Object> e;
        try {
            Record rec = dsl.fetchOne("""
                    SELECT ae.id, ae.brand_id, ae.engine_name, ae.prompt_text,
                           ae.response_full, ae.version, ae.content_hash, ae.created_at
                    FROM archive.response_entries ae
                    JOIN config.brands b ON b.id = ae.brand_id
                    WHERE ae.id = ? AND ae.tenant_id = ? AND b.workspace_id = ?
                    """, entryId, tenantId, workspaceId);
            e = rec == null ? null : rec.intoMap();
        } catch (RuntimeException ex) {
            return error(HttpStatus.NOT_FOUND, "arşiv girişi bulunamadı");
        }
        if (e == null) {
            return error(HttpStatus.NOT_FOUND, "arşiv girişi bulunamadı");
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", str(e.get("id")));
        item.put("brand_id", str(e.get("brand_id")));
        item.put("engine_name", str(e.get("engine_name")));
        item.put("prompt_text", str(e.get("prompt_text")));
        item.put("response_full", str(e.get("response_full")));
        item.put("version", num(e.get("version")));
        item.put("content_hash", str(e.get("content_hash")));
        item.put("created_at", str(e.get("created_at")));
        return ResponseEntity.ok(item);
    }

    // ---------- ArchiveResponse ----------

    @PostMapping
    public ResponseEntity<?> archiveResponse(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestBody ArchiveRequest req) {
        if (req.brandId() == null || req.brandId().isBlank() || req.response() == null || req.response().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve response zorunludur");
        }

        Entry entry;
        try {
            entry = engine.archive(req.brandId(), nz(req.engineName()), nz(req.promptText()),
                    req.response(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "arşivleme başarısız");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    // ---------- GetVersionHistory ----------

    @GetMapping("/versions")
    public ResponseEntity<?> getVersionHistory(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestParam(value = "brand_id", required = false) String brandId,
                                               @RequestParam(value = "engine", required = false) String engineName) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT ae.version, ae.id, ae.content_hash, ae.created_at
                    FROM archive.response_entries ae
                    JOIN config.brands b ON b.id = ae.brand_id
                    WHERE ae.tenant_id = ? AND b.workspace_id = ? AND ae.brand_id = ?
                        AND (? = '' OR ae.engine_name = ?)
                    ORDER BY ae.version DESC
                    """, tenantId, workspaceId, brandId, nz(engineName), nz(engineName)).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> versions = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("version", num(r.get("version")));
            item.put("entry_id", str(r.get("id")));
            item.put("content_hash", str(r.get("content_hash")));
            item.put("created_at", str(r.get("created_at")));
            versions.add(item);
        }

        return ResponseEntity.ok(versions);
    }

    // ---------- yardımcılar ----------

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
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
