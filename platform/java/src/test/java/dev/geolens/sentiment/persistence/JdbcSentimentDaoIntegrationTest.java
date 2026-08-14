package dev.geolens.sentiment.persistence;

import dev.geolens.sentiment.domain.CheckTarget;
import dev.geolens.sentiment.domain.RawResponse;
import dev.geolens.sentiment.domain.SentimentResult;
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
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDBC DAO entegrasyon testi — Docker gerekir.
 * <p>Çalıştırma: {@code mvnw test "-Dsurefire.groups=integration"} (Go'daki {@code -tags=integration} karşılığı).
 * <p>RLS davranışını doğrular: tenant bağlamı dışındaki sorgular satır döndürmez.
 */
@Tag("integration")
@Testcontainers
class JdbcSentimentDaoIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("geolens")
            .withUsername("geolens")
            .withPassword("geolens")
            .withInitScript("schema/sentiment_integration.sql");

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private JdbcSentimentDao dao;

    @BeforeEach
    void setUp() {
        if (dao != null) {
            return;
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        dao = new JdbcSentimentDao(jdbc, tx);

        tx.executeWithoutResult(status -> {
            jdbc.update("TRUNCATE analysis.hallucination_flags, analysis.sentiment_scores, measure.raw_responses, config.brands, config.workspaces, identity.tenants CASCADE");
            jdbc.update("INSERT INTO identity.tenants (id) VALUES ('T01')");
            jdbc.update("INSERT INTO config.workspaces (id, tenant_id) VALUES ('WS01', 'T01')");
        });
        insertInTenant("T01", "INSERT INTO config.brands (id, workspace_id, tenant_id, name, is_active) VALUES ('B01', 'WS01', 'T01', 'Acme', true)");
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
        inTenant(tenantId, () -> jdbc.update(sql, args));
    }

    @Test
    void loadRawResponsesReturnsEngineRows() {
        insertInTenant("T01", """
                INSERT INTO measure.raw_responses (id, tenant_id, brand_id, engine_name, content_text, prompt_text)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "R1", "T01", "B01", "chatgpt", "Acme harika bir ürün", "değerlendir");

        List<RawResponse> rows = dao.loadRawResponses("T01", "B01");
        assertEquals(1, rows.size());
        assertEquals("R1", rows.get(0).id());
        assertEquals("chatgpt", rows.get(0).engineName());
        assertEquals("Acme harika bir ürün", rows.get(0).content());
        assertNotNull(rows.get(0).createdAt());
    }

    @Test
    void loadCheckTargetsJoinsBrandProfile() {
        insertInTenant("T01", """
                INSERT INTO measure.raw_responses (id, tenant_id, brand_id, engine_name, content_text, prompt_text)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "R1", "T01", "B01", "chatgpt", "rakip ürünler hakkında bilgi", "P1");

        List<CheckTarget> targets = dao.loadCheckTargets("T01", "B01");
        assertEquals(1, targets.size());
        CheckTarget t = targets.get(0);
        assertEquals("R1", t.id());
        assertEquals("chatgpt", t.engineName());
        assertEquals("P1", t.prompt());
        assertEquals("Acme", t.brandName());
    }

    @Test
    void saveAndLoadLatestSentimentRoundTrips() {
        SentimentResult result = new SentimentResult(null, "B01", "chatgpt",
                1.0, 1.0, 0.0, 0.0, 1, null, Instant.now());
        dao.saveSentiment("T01", "WS01", result);

        SentimentResult latest = dao.loadLatest("B01", "T01");
        assertNotNull(latest);
        assertEquals("chatgpt", latest.engineName());
        assertEquals(1.0, latest.overallSentiment());
        assertEquals(1, latest.mentionCount());
    }

    @Test
    void loadLatestNoneReturnsNull() {
        assertNull(dao.loadLatest("B01", "T01"));
    }

    @Test
    void saveSentimentIsTenantScoped() {
        SentimentResult result = new SentimentResult(null, "B01", "chatgpt",
                0.8, 0.7, 0.2, 0.1, 3, null, Instant.now());
        dao.saveSentiment("T01", "WS01", result);

        // Farklı tenant bağlamı: RLS satırı görünür yapmaz.
        assertNull(dao.loadLatest("B01", "T99"));

        // Aynı tenant: görünür.
        assertNotNull(dao.loadLatest("B01", "T01"));
    }

    @Test
    void saveHallucinationPersists() {
        var h = new dev.geolens.sentiment.domain.HallucinationResult(null, "B01", "chatgpt", "T3",
                "critical", "AI yanıtı kaynaksız istatistik/rakam içeriyor", 0.5, null, Instant.now());
        dao.saveHallucination("T01", "WS01", h);

        Integer count = inTenant("T01", () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM analysis.hallucination_flags WHERE brand_id = 'B01'", Integer.class));
        assertEquals(1, count);
    }

    @Test
    void rawResponsesTenantIsolation() {
        insertInTenant("T01", """
                INSERT INTO measure.raw_responses (id, tenant_id, brand_id, engine_name, content_text)
                VALUES (?, ?, ?, ?, ?)
                """, "R1", "T01", "B01", "chatgpt", "Acme harika");

        assertEquals(1, dao.loadRawResponses("T01", "B01").size());
        assertTrue(dao.loadRawResponses("T99", "B01").isEmpty(), "farklı tenant satır görmemeli");
    }
}