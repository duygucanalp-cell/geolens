package dev.geolens.registry.web;

import dev.geolens.registry.Entity;
import dev.geolens.registry.EntityIndexer;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI Registry REST controller'ı — Go {@code registry.handler} portu (R1).
 * <p>Route'lar (go cmd/api): GET /v1/registry/entities, GET/PUT/DELETE /entities/{entityId},
 * POST /entities, POST /entities/{entityId}/assess.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; varlık kayıt/güncellemede
 * Elasticsearch indeksleme noop indexer ile yapılır (Go {@code noopIndexer} karşılığı).
 */
@RestController
@RequestMapping("/v1/registry")
public class RegistryController {

    private static final Set<String> VALID_TYPES = Set.of("model", "agent", "application", "dataset");

    private final DSLContext dsl;
    private final EntityIndexer indexer;

    public RegistryController(DSLContext dsl) {
        this(dsl, EntityIndexer.noop());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RegistryController(DSLContext dsl, EntityIndexer indexer) {
        this.dsl = dsl;
        this.indexer = indexer;
    }

    // ---------- List ----------

    @GetMapping("/entities")
    public ResponseEntity<?> list(@RequestHeader("X-Tenant-ID") String tenantId,
                                  @RequestParam(value = "entity_type", required = false) String entityType,
                                  @RequestParam(value = "lifecycle_state", required = false) String lifecycle,
                                  @RequestParam(value = "risk_class", required = false) String risk) {
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
            rows = dsl.fetch(query.toString(), args.toArray()).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("entities", List.of()));
        }

        List<Entity> entities = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            entities.add(toEntity(r));
        }
        return ResponseEntity.ok(Map.of("entities", entities));
    }

    // ---------- Get ----------

    @GetMapping("/entities/{entityId}")
    public ResponseEntity<?> get(@RequestHeader("X-Tenant-ID") String tenantId,
                                 @PathVariable String entityId) {
        Record rec;
        try {
            rec = dsl.fetchOne("""
                    SELECT id, tenant_id, entity_type, name, description, version, provider,
                        lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
                    FROM registry.entities WHERE id = ? AND tenant_id = ?
                    """, entityId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        if (rec == null) {
            return error(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        return ResponseEntity.ok(toEntity(rec.intoMap()));
    }

    // ---------- Create ----------

    @PostMapping("/entities")
    public ResponseEntity<?> create(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody CreateEntityRequest req) {
        if (req.entityType() == null || !VALID_TYPES.contains(req.entityType())) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz entity_type: model, agent, application, dataset");
        }

        String lifecycle = req.lifecycleState() == null || req.lifecycleState().isBlank() ? "development" : req.lifecycleState();
        String riskClass = req.riskClass() == null || req.riskClass().isBlank() ? "medium" : req.riskClass();
        String version = req.version() == null || req.version().isBlank() ? "1.0.0" : req.version();

        Record rec;
        try {
            rec = dsl.fetchOne("""
                    INSERT INTO registry.entities (tenant_id, entity_type, name, description, version, provider,
                        lifecycle_state, risk_class, owner, documentation_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id, tenant_id, entity_type, name, description, version, provider,
                        lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
                    """, tenantId, req.entityType(), nz(req.name()), nz(req.description()), version,
                    nz(req.provider()), lifecycle, riskClass, nz(req.owner()), nz(req.documentationUrl()));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "varlık kaydedilemedi");
        }
        if (rec == null) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "varlık kaydedilemedi");
        }
        Entity entity = toEntity(rec.intoMap());

        // R1: Elasticsearch'e indeksle (hatayı logla ama yanıtı engelleme — noop spike'ta)
        indexer.indexEntity(entity);

        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    // ---------- Update ----------

    @PutMapping("/entities/{entityId}")
    public ResponseEntity<?> update(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String entityId,
                                    @RequestBody UpdateEntityRequest req) {
        Record rec;
        try {
            rec = dsl.fetchOne("""
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
            return error(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        if (rec == null) {
            return error(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        Entity entity = toEntity(rec.intoMap());

        // R1: Elasticsearch'e yeniden indeksle
        indexer.indexEntity(entity);

        return ResponseEntity.ok(entity);
    }

    // ---------- Delete ----------

    @DeleteMapping("/entities/{entityId}")
    public ResponseEntity<?> delete(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String entityId) {
        int rows;
        try {
            rows = dsl.execute("DELETE FROM registry.entities WHERE id = ? AND tenant_id = ?", entityId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "silme hatası");
        }
        if (rows == 0) {
            return error(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }

        // R1: Elasticsearch'ten sil (non-fatal)
        indexer.deleteEntity(entityId);

        return ResponseEntity.ok(Map.of("status", "silindi"));
    }

    // ---------- AssessRisk ----------

    @PostMapping("/entities/{entityId}/assess")
    public ResponseEntity<?> assessRisk(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String entityId,
                                        @RequestBody AssessRiskRequest req) {
        Record rec;
        try {
            rec = dsl.fetchOne("""
                    INSERT INTO registry.risk_assessments (entity_id, tenant_id, risk_class, score, summary, assessed_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, entityId, tenantId, nz(req.riskClass()), req.score() == null ? 0 : req.score(),
                    nz(req.summary()), "");
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "değerlendirme kaydedilemedi");
        }
        String assessmentId = rec == null ? "" : str(rec.get(0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", assessmentId);
        result.put("status", "değerlendirildi");
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
