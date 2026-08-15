package dev.geolens.archive.service;

import dev.geolens.common.ServiceException;

import dev.geolens.archive.ArchiveEngine;
import dev.geolens.archive.Entry;
import dev.geolens.archive.web.ArchiveRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Response Archive iş mantığı — Go {@code archive.handler} portu (FR-D13).
 * <p>Arşiv kayıtlarını listeler/okur, yanıtları {@link ArchiveEngine} ile arşivler
 * ve versiyon geçmişini döndürür; controller yalnızca HTTP katmanıdır
 * (route'lar: GET /v1/workspaces/{ws}/archive, GET /archive/{entryId},
 * POST /archive, GET /archive/versions).
 */
@Service
public class ArchiveService {

    private final ArchiveEngine engine;
    private final DSLContext dsl;

    public ArchiveService(ArchiveEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    public List<Map<String, Object>> listEntries(String workspaceId, String tenantId,
                                                 String brandId, String engineName, String versionStr) {
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
            return List.of();
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

        return entries;
    }

    public Map<String, Object> getEntry(String workspaceId, String tenantId, String entryId) {
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
            throw new ServiceException(HttpStatus.NOT_FOUND, "arşiv girişi bulunamadı");
        }
        if (e == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "arşiv girişi bulunamadı");
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
        return item;
    }

    public Entry archiveResponse(String workspaceId, String tenantId, ArchiveRequest req) {
        try {
            return engine.archive(req.brandId(), nz(req.engineName()), nz(req.promptText()),
                    req.response(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "arşivleme başarısız");
        }
    }

    public List<Map<String, Object>> getVersionHistory(String workspaceId, String tenantId,
                                                       String brandId, String engineName) {
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
            return List.of();
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

        return versions;
    }

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
}
