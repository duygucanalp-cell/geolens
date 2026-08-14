package dev.geolens.version.web;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versiyon takibi REST controller'ı — Go {@code version.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/versions/entries, GET /v1/versions/entries,
 * GET /v1/versions/entries/{entryId} (R14).
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 */
@RestController
public class VersionController {

    private final DSLContext dsl;

    public VersionController(DSLContext dsl) {
        this.dsl = dsl;
    }

    @PostMapping("/v1/versions/entries")
    public ResponseEntity<?> recordVersion(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestBody VersionEntryRequest req) {
        if (req == null || req.entityType() == null || req.entityType().isBlank()
                || req.entityId() == null || req.entityId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "entity_type ve entity_id gerekli");
        }

        String entryId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        try {
            dsl.execute("""
                    INSERT INTO version.entries (id, tenant_id, entity_type, entity_id, entity_name, old_version, new_version, change_notes, changed_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, entryId, tenantId, req.entityType(), req.entityId(),
                    req.entityName() == null ? "" : req.entityName(),
                    req.oldVersion() == null ? "" : req.oldVersion(),
                    req.newVersion() == null ? "" : req.newVersion(),
                    req.changeNotes() == null ? "" : req.changeNotes(),
                    req.changedBy() == null ? "" : req.changedBy(), now);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "versiyon kaydedilemedi");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entry_id", entryId);
        body.put("entity_type", req.entityType());
        body.put("entity_name", req.entityName());
        body.put("old_version", req.oldVersion());
        body.put("new_version", req.newVersion());
        body.put("created_at", DateTimeFormatter.ISO_INSTANT.format(now));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/v1/versions/entries")
    public ResponseEntity<?> listVersions(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestParam(value = "limit", required = false) String limitParam,
                                          @RequestParam(value = "entity_type", required = false) String entityType,
                                          @RequestParam(value = "entity_id", required = false) String entityId) {
        int limit = 20;
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                int n = Integer.parseInt(limitParam);
                if (n >= 1 && n <= 100) {
                    limit = n;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String type = entityType == null ? "" : entityType;
        String eid = entityId == null ? "" : entityId;

        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, entity_type, entity_id, entity_name, old_version, new_version, change_notes, changed_by, created_at
                    FROM version.entries WHERE tenant_id = ?
                        AND (? = '' OR entity_type = ?)
                        AND (? = '' OR entity_id = ?)
                    ORDER BY created_at DESC LIMIT ?
                    """, tenantId, type, type, eid, eid, limit + 1);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("data", List.of(), "has_more", false));
        }

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("entity_type", r.get("entity_type"));
            item.put("entity_id", r.get("entity_id"));
            item.put("entity_name", r.get("entity_name"));
            item.put("old_version", r.get("old_version"));
            item.put("new_version", r.get("new_version"));
            item.put("change_notes", r.get("change_notes"));
            item.put("changed_by", r.get("changed_by"));
            item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            data.add(item);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("has_more", hasMore);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/v1/versions/entries/{entryId}")
    public ResponseEntity<?> getVersionDiff(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String entryId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT id, entity_type, entity_id, entity_name, old_version, new_version, change_notes, changed_by, created_at
                    FROM version.entries WHERE id = ? AND tenant_id = ?
                    """, entryId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "versiyon kaydı bulunamadı");
        }
        if (row == null) {
            return error(HttpStatus.NOT_FOUND, "versiyon kaydı bulunamadı");
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", row.get("id"));
        entry.put("entity_type", row.get("entity_type"));
        entry.put("entity_id", row.get("entity_id"));
        entry.put("entity_name", row.get("entity_name"));
        entry.put("old_version", row.get("old_version"));
        entry.put("new_version", row.get("new_version"));
        entry.put("change_notes", row.get("change_notes"));
        entry.put("changed_by", row.get("changed_by"));
        entry.put("created_at", row.get("created_at") == null ? null : String.valueOf(row.get("created_at")));

        String oldV = String.valueOf(row.get("old_version"));
        String newV = String.valueOf(row.get("new_version"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entry", entry);
        body.put("has_changes", !java.util.Objects.equals(oldV, newV));
        return ResponseEntity.ok(body);
    }

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
