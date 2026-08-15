package dev.geolens.version.service;

import dev.geolens.version.web.VersionEntryRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.geolens.jooq.version.tables.Entries.ENTRIES;

/**
 * Versiyon takibi iş mantığı — Go {@code version.handler} portu (R14).
 * <p>Versiyon kaydı, liste ve diff sorguları (ADR-014 v3.0 tip güvenli jOOQ DSL)
 * bu servistedir; controller yalnızca HTTP katmanıdır
 * (route'lar: POST /v1/versions/entries, GET /v1/versions/entries,
 * GET /v1/versions/entries/{entryId}).
 */
@Service
public class VersionService {

    private final DSLContext dsl;

    public VersionService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, Object> recordVersion(String tenantId, VersionEntryRequest req) {
        String entryId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        try {
            dsl.insertInto(ENTRIES)
                    .columns(List.of(ENTRIES.ID, ENTRIES.TENANT_ID, ENTRIES.ENTITY_TYPE, ENTRIES.ENTITY_ID,
                            ENTRIES.ENTITY_NAME, ENTRIES.OLD_VERSION, ENTRIES.NEW_VERSION,
                            ENTRIES.CHANGE_NOTES, ENTRIES.CHANGED_BY, ENTRIES.CREATED_AT))
                    .values(entryId, tenantId, req.entityType(), req.entityId(),
                            req.entityName() == null ? "" : req.entityName(),
                            req.oldVersion() == null ? "" : req.oldVersion(),
                            req.newVersion() == null ? "" : req.newVersion(),
                            req.changeNotes() == null ? "" : req.changeNotes(),
                            req.changedBy() == null ? "" : req.changedBy(),
                            now.atOffset(ZoneOffset.UTC))
                    .execute();
        } catch (RuntimeException e) {
            throw new VersionServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "versiyon kaydedilemedi");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entry_id", entryId);
        body.put("entity_type", req.entityType());
        body.put("entity_name", req.entityName());
        body.put("old_version", req.oldVersion());
        body.put("new_version", req.newVersion());
        body.put("created_at", DateTimeFormatter.ISO_INSTANT.format(now));
        return body;
    }

    public Map<String, Object> listVersions(String tenantId, String limitParam,
                                            String entityType, String entityId) {
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
            rows = dsl.select(List.of(ENTRIES.ID, ENTRIES.ENTITY_TYPE, ENTRIES.ENTITY_ID, ENTRIES.ENTITY_NAME,
                            ENTRIES.OLD_VERSION, ENTRIES.NEW_VERSION, ENTRIES.CHANGE_NOTES,
                            ENTRIES.CHANGED_BY, ENTRIES.CREATED_AT))
                    .from(ENTRIES)
                    .where(ENTRIES.TENANT_ID.eq(tenantId)
                            .and(type.isEmpty() ? DSL.noCondition() : ENTRIES.ENTITY_TYPE.eq(type))
                            .and(eid.isEmpty() ? DSL.noCondition() : ENTRIES.ENTITY_ID.eq(eid)))
                    .orderBy(ENTRIES.CREATED_AT.desc())
                    .limit(limit + 1)
                    .fetch().intoMaps();
        } catch (RuntimeException e) {
            return Map.of("data", List.of(), "has_more", false);
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
        return body;
    }

    public Map<String, Object> getVersionDiff(String tenantId, String entryId) {
        Map<String, Object> row;
        try {
            Record r = dsl.select(List.of(ENTRIES.ID, ENTRIES.ENTITY_TYPE, ENTRIES.ENTITY_ID, ENTRIES.ENTITY_NAME,
                            ENTRIES.OLD_VERSION, ENTRIES.NEW_VERSION, ENTRIES.CHANGE_NOTES,
                            ENTRIES.CHANGED_BY, ENTRIES.CREATED_AT))
                    .from(ENTRIES)
                    .where(ENTRIES.ID.eq(entryId).and(ENTRIES.TENANT_ID.eq(tenantId)))
                    .fetchOne();
            row = r == null ? null : r.intoMap();
        } catch (RuntimeException e) {
            throw new VersionServiceException(HttpStatus.NOT_FOUND, "versiyon kaydı bulunamadı");
        }
        if (row == null) {
            throw new VersionServiceException(HttpStatus.NOT_FOUND, "versiyon kaydı bulunamadı");
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
        return body;
    }
}
