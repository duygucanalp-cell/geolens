package dev.geolens.recommendation;

import dev.geolens.recommendation.domain.AuditSnapshot;
import dev.geolens.recommendation.domain.Brand;
import dev.geolens.recommendation.domain.Category;
import dev.geolens.recommendation.domain.EvidenceLabel;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.ScoreSnapshot;
import dev.geolens.recommendation.domain.Severity;
import dev.geolens.recommendation.persistence.AppliedRecommendation;
import dev.geolens.recommendation.persistence.JooqRecommendationDao;
import dev.geolens.recommendation.persistence.RecommendationNotFoundException;
import dev.geolens.recommendation.persistence.ScoreAt;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JOOQ DAO entegrasyon testi — Docker gerekir.
 * <p>Çalıştırma: {@code mvnw test "-Dsurefire.groups=integration"} (Go'daki {@code -tags=integration} karşılığı).
 * <p>Her test öncesi tablolar truncate edilip yeniden seed edilir; testler izole çalışır.
 */
@Tag("integration")
@Testcontainers
class JooqRecommendationDaoIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("geolens")
            .withUsername("geolens")
            .withPassword("geolens")
            .withInitScript("schema/recommendation_integration.sql");

    private DriverManagerDataSource ds;
    private JooqRecommendationDao dao;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        if (dao != null) {
            return;
        }
        ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        dao = new JooqRecommendationDao(DSL.using(ds, SQLDialect.POSTGRES),
                new TransactionTemplate(new DataSourceTransactionManager(ds)));

        // RLS'li tablolar dahil her şeyi sıfırla; üst tablolar (RLS'siz) düz eklenir
        tx.executeWithoutResult(status -> {
            jdbc.update("TRUNCATE recommendation.results, measure.scores, governance.audit_results, config.brands, config.workspaces, identity.tenants CASCADE");
            jdbc.update("INSERT INTO identity.tenants (id) VALUES ('T01')");
            jdbc.update("INSERT INTO config.workspaces (id, tenant_id) VALUES ('WS01', 'T01')");
        });

        // RLS'li markalar tenant bağlamında eklenir
        insertInTenant("T01", "INSERT INTO config.brands (id, workspace_id, tenant_id, name, is_active) VALUES ('B01', 'WS01', 'T01', 'Acme', true)");
        insertInTenant("T01", "INSERT INTO config.brands (id, workspace_id, tenant_id, name, is_active) VALUES ('B02', 'WS01', 'T01', 'Beta', false)");
    }

    private <T> T inTenant(String tenantId, Supplier<T> work) {
        return tx.execute(status -> {
            jdbc.execute("SELECT set_config('app.tenant_id', ?, true)",
                    (PreparedStatementCallback<Void>) ps -> {
                        ps.setString(1, tenantId);
                        ps.execute();
                        return null;
                    });
            return work.get();
        });
    }

    private void insertInTenant(String tenantId, String sql, Object... args) {
        inTenant(tenantId, () -> {
            jdbc.update(sql, args);
            return null;
        });
    }

    private Integer countInTenant(String tenantId, String sql, Object... args) {
        return inTenant(tenantId, () -> jdbc.queryForObject(sql, Integer.class, args));
    }

    @Test
    void loadScoreReturnsLatestAndPrevious() {
        insertInTenant("T01", "INSERT INTO measure.scores (id, tenant_id, workspace_id, brand_id, value, engine_breakdown, freshness_at) VALUES ('S1', 'T01', 'WS01', 'B01', 50, '{\"chatgpt\": 45, \"gemini\": 80}'::jsonb, now() - interval '2 hours')");
        insertInTenant("T01", "INSERT INTO measure.scores (id, tenant_id, workspace_id, brand_id, value, freshness_at) VALUES ('S2', 'T01', 'WS01', 'B01', 70, now() - interval '2 days')");

        ScoreSnapshot s = dao.loadScore("B01", "WS01", "T01");
        assertEquals(50.0, s.value());
        assertEquals(70.0, s.previousValue());
        assertNotNull(s.freshnessAt());
        assertNotNull(s.previousAt());
        assertEquals(45.0, s.engineBreakdown().get("chatgpt"));
        assertEquals(80.0, s.engineBreakdown().get("gemini"));
    }

    @Test
    void loadScoreReturnsEmptyWhenNoScores() {
        ScoreSnapshot s = dao.loadScore("B01", "WS01", "T01");
        assertEquals(0.0, s.value());
        assertEquals(0.0, s.previousValue());
        assertNull(s.freshnessAt());
    }

    @Test
    void loadAuditReadsFlagsAndOverall() {
        insertInTenant("T01", "INSERT INTO governance.audit_results (id, brand_id, workspace_id, tenant_id, overall_score, robots_txt, bot_access, ssr) VALUES ('A1', 'B01', 'WS01', 'T01', 42.5, '{\"disallowed_all\": true}'::jsonb, '{\"accessible\": false}'::jsonb, '{\"has_structured_data\": false}'::jsonb)");

        AuditSnapshot a = dao.loadAudit("B01", "T01");
        assertTrue(a.hasData());
        assertTrue(a.robotsDisallowedAll());
        assertFalse(a.botAccessible());
        assertFalse(a.hasStructuredData());
        assertEquals(42.5, a.overallScore());
    }

    @Test
    void loadAuditReturnsEmptyWhenNoAudit() {
        AuditSnapshot a = dao.loadAudit("B01", "T01");
        assertFalse(a.hasData());
    }

    @Test
    void listActiveBrandsReturnsOnlyActive() {
        List<Brand> brands = dao.listActiveBrands("WS01", "T01");
        assertEquals(1, brands.size());
        assertEquals("B01", brands.get(0).id());
        assertEquals("Acme", brands.get(0).name());
    }

    @Test
    void saveIsIdempotentAndMarkAppliedWorks() {
        Recommendation rec = new Recommendation("R1", "T01", "WS01", "B01", Category.VISIBILITY, Severity.HIGH,
                EvidenceLabel.CORRELATIONAL, "Görünürlük skorunuz düşüyor", "detay", "/audit", 85,
                false, false, Instant.now().minus(1, ChronoUnit.HOURS));

        dao.save(rec);
        dao.save(rec); // ON CONFLICT DO NOTHING → aynı satır

        assertEquals(1, countInTenant("T01", "SELECT count(*) FROM recommendation.results WHERE id = 'R1'"));

        dao.markApplied("R1", "T01", "WS01");
        assertEquals(1, countInTenant("T01",
                "SELECT count(*) FROM recommendation.results WHERE id = 'R1' AND applied = true AND applied_at IS NOT NULL"));
    }

    @Test
    void markAppliedOnMissingRecordThrows() {
        assertThrows(RecommendationNotFoundException.class, () -> dao.markApplied("YOK", "T01", "WS01"));
    }

    @Test
    void loadAppliedReturnsRecordWhenApplied() {
        Recommendation rec = new Recommendation("R2", "T01", "WS01", "B01", Category.VISIBILITY, Severity.HIGH,
                EvidenceLabel.CORRELATIONAL, "başlık", "detay", null, 85, false, false,
                Instant.now().minus(3, ChronoUnit.DAYS));
        dao.save(rec);
        dao.markApplied("R2", "T01", "WS01");

        AppliedRecommendation app = dao.loadApplied("R2", "WS01", "T01");
        assertNotNull(app);
        assertEquals("B01", app.brandId());
        assertNotNull(app.appliedAt());
    }

    @Test
    void loadAppliedReturnsNullWhenNotApplied() {
        assertNull(dao.loadApplied("YOK", "WS01", "T01"));
    }

    @Test
    void loadScoreAtReturnsBeforeAndAfterBoundaries() {
        insertInTenant("T01", "INSERT INTO config.brands (id, workspace_id, tenant_id, name, is_active) VALUES ('B10', 'WS01', 'T01', 'Izole', true)");

        Instant appliedAt = Instant.now();
        insertInTenant("T01", "INSERT INTO measure.scores (id, tenant_id, workspace_id, brand_id, value, fidelity_label, freshness_at) VALUES ('S10', 'T01', 'WS01', 'B10', 30, 'low', ?)",
                java.sql.Timestamp.from(appliedAt.minus(2, ChronoUnit.DAYS)));
        insertInTenant("T01", "INSERT INTO measure.scores (id, tenant_id, workspace_id, brand_id, value, fidelity_label, freshness_at) VALUES ('S11', 'T01', 'WS01', 'B10', 55, 'full', ?)",
                java.sql.Timestamp.from(appliedAt.plus(1, ChronoUnit.DAYS)));

        ScoreAt before = dao.loadScoreAt("B10", "WS01", "T01", appliedAt, true);
        ScoreAt after = dao.loadScoreAt("B10", "WS01", "T01", appliedAt, false);

        assertNotNull(before);
        assertEquals(30.0, before.value());
        assertEquals("low", before.fidelity());

        assertNotNull(after);
        assertEquals(55.0, after.value());
        assertEquals("full", after.fidelity());
    }
}
