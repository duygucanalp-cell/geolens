package dev.geolens.registry.service;

import dev.geolens.common.ServiceException;

import dev.geolens.registry.Entity;
import dev.geolens.registry.EntityIndexer;
import dev.geolens.registry.web.AssessRiskRequest;
import dev.geolens.registry.web.CreateEntityRequest;
import dev.geolens.registry.web.UpdateEntityRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI Registry iş mantığı — Go {@code registry.handler} portu (R1).
 * <p>Varlık CRUD, risk değerlendirmesi ve indeksleme işlemlerini yönetir.
 * Controller yalnızca HTTP katmanıdır; bu sınıf DB ve engine erişimini içerir.
 */
@Service
public class RegistryService {

    private static final Set<String> VALID_TYPES = Set.of("model", "agent", "application", "dataset");

    private final DSLContext dsl;
    private final EntityIndexer indexer;

    public RegistryService(DSLContext dsl, EntityIndexer indexer) {
        this.dsl = dsl;
        this.indexer = indexer;
    }

    /** Go {@code List} karşılığı — sorgu hatasında boş liste döner. */
    public List<Entity> listEntities(String tenantId, String entityType, String lifecycle, String risk) {
        StringBuilder query = new StringBuilder("""
                SELECT id, tenant_id, entity_type, name, description, version, provider,
                    lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
                FROM registry.entities WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        int argIdx = 2;

        if (entityType != null && !entityType.isBlank()) {
            query.append(" AND entity_type = $").append(argIdx);
            args.add(entityType);
            argIdx++;
        }
        if (lifecycle != null && !lifecycle.isBlank()) {
            query.append(" AND lifecycle_state = $").append(argIdx);
            args.add(lifecycle);
            argIdx++;
        }
        if (risk != null && !risk.isBlank()) {
            query.append(" AND risk_class = $").append(argIdx);
            args.add(risk);
        }
        query.append(" ORDER BY created_at DESC");

        List<Map<String, Object>> rows;
        try {
            rows = list(query.toString(), args.toArray());
        } catch (RuntimeException e) {
            return List.of();
        }

        List<Entity> entities = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            entities.add(toEntity(r));
        }
        return entities;
    }

    /** Go {@code Get} karşılığı — bulunamazsa 404 fırlatır. */
    public Entity getEntity(String tenantId, String entityId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT id, tenant_id, entity_type, name, description, version, provider,
                        lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
                    FROM registry.entities WHERE id = ? AND tenant_id = ?
                    """, entityId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        if (row == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        return toEntity(row);
    }

    /** Go {@code Create} karşılığı — varlığı kaydeder ve indeksler. */
    public Entity createEntity(String tenantId, CreateEntityRequest req) {
        if (req.entityType() == null || !VALID_TYPES.contains(req.entityType())) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "geçersiz entity_type: model, agent, application, dataset");
        }

        String lifecycle = req.lifecycleState() == null || req.lifecycleState().isBlank() ? "development" : req.lifecycleState();
        String riskClass = req.riskClass() == null || req.riskClass().isBlank() ? "medium" : req.riskClass();
        String version = req.version() == null || req.version().isBlank() ? "1.0.0" : req.version();

        Map<String, Object> row;
        try {
            row = map("""
                    INSERT INTO registry.entities (tenant_id, entity_type, name, description, version, provider,
                        lifecycle_state, risk_class, owner, documentation_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id, tenant_id, entity_type, name, description, version, provider,
                        lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
                    """, tenantId, req.entityType(), nz(req.name()), nz(req.description()), version,
                    nz(req.provider()), lifecycle, riskClass, nz(req.owner()), nz(req.documentationUrl()));
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "varlık kaydedilemedi");
        }
        if (row == null) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "varlık kaydedilemedi");
        }
        Entity entity = toEntity(row);

        // R1: Elasticsearch'e indeksle (hatayı logla ama yanıtı engelleme — noop spike'ta)
        indexer.indexEntity(entity);

        return entity;
    }

    /** Go {@code Update} karşılığı — varlığı günceller ve yeniden indeksler. */
    public Entity updateEntity(String tenantId, String entityId, UpdateEntityRequest req) {
        Map<String, Object> row;
        try {
            row = map("""
                    UPDATE registry.entities SET
                        name = COALESCE(NULLIF(?, ''), name),
                        description = COALESCE(NULLIF(?, ''), description),
                        version = COALESCE(NULLIF(?, ''), version),
                        provider = COALESCE(NULLIF(?, ''), provider),
                        lifecycle_state = COALESCE(NULLIF(?, ''), lifecycle_state),
                        risk_class = COALESCE(NULLIF(?, ''), risk_class),
                        owner = COALESCE(NULLIF(?, ''), owner),
                        documentation_url = COALESCE(NULLIF(?, ''), documentation_url),
                        updated_at = now()
                    WHERE id = ? AND tenant_id = ?
                    RETURNING id, tenant_id, entity_type, name, description, version, provider,
                        lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
                    """, nz(req.name()), nz(req.description()), nz(req.version()), nz(req.provider()),
                    nz(req.lifecycleState()), nz(req.riskClass()), nz(req.owner()), nz(req.documentationUrl()),
                    entityId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        if (row == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        Entity entity = toEntity(row);

        // R1: Elasticsearch'e yeniden indeksle
        indexer.indexEntity(entity);

        return entity;
    }

    /** Go {@code Delete} karşılığı — varlığı siler ve indeksten kaldırır. */
    public Map<String, Object> deleteEntity(String tenantId, String entityId) {
        int rows;
        try {
            rows = dsl.execute("DELETE FROM registry.entities WHERE id = ? AND tenant_id = ?", entityId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "silme hatası");
        }
        if (rows == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }

        // R1: Elasticsearch'ten sil (non-fatal)
        indexer.deleteEntity(entityId);

        return Map.of("status", "silindi");
    }

    /** Go {@code AssessRisk} karşılığı — risk değerlendirmesini kaydeder. */
    public Map<String, Object> assessRisk(String tenantId, String entityId, AssessRiskRequest req) {
        Map<String, Object> row;
        try {
            row = map("""
                    INSERT INTO registry.risk_assessments (entity_id, tenant_id, risk_class, score, summary, assessed_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, entityId, tenantId, nz(req.riskClass()), req.score() == null ? 0 : req.score(),
                    nz(req.summary()), "");
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "değerlendirme kaydedilemedi");
        }
        String assessmentId = row == null ? "" : str(row.get("id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", assessmentId);
        result.put("status", "değerlendirildi");
        return result;
    }

    // ---------- yardımcılar ----------

    private static Entity toEntity(Map<String, Object> r) {
        return new Entity(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("entity_type")), str(r.get("name")),
                str(r.get("description")), str(r.get("version")), str(r.get("provider")),
                str(r.get("lifecycle_state")), str(r.get("risk_class")), str(r.get("owner")),
                str(r.get("documentation_url")),
                r.get("deployed_at") == null ? null : str(r.get("deployed_at")),
                str(r.get("created_at")), str(r.get("updated_at")));
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

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    /** ADR-014: plain SQL tek değer — jOOQ dönüşümüyle (fetchValue raw Object döner). */
    private <T> T value(String sql, Class<T> type, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.get(0, type);
    }
}
