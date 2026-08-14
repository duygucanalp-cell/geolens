package dev.geolens.config;

import dev.geolens.audit.AuditService;
import dev.geolens.benchmark.BenchmarkAggregator;
import dev.geolens.replay.ReplayEngine;
import dev.geolens.benchmark.DpConfig;
import dev.geolens.auth.JWTService;
import dev.geolens.auth.TokenBlacklist;
import dev.geolens.auth.web.TransactionalMailer;
import dev.geolens.billing.EFaturaProvider;
import dev.geolens.billing.StripeClient;
import dev.geolens.delivery.DeliveryService;
import dev.geolens.delivery.EmailConfig;
import dev.geolens.engine.Registry;
import dev.geolens.engine.chatgpt.ChatGptAdapter;
import dev.geolens.engine.gemini.GeminiAdapter;
import dev.geolens.engine.perplexity.PerplexityAdapter;
import dev.geolens.governance.AuditLogger;
import dev.geolens.governance.QuotaChecker;
import dev.geolens.governance.UsageRecorder;
import dev.geolens.measure.MeasureService;
import dev.geolens.measure.persistence.JooqScoreDao;
import dev.geolens.measure.persistence.NoopScoreDao;
import dev.geolens.measure.persistence.ScoreDao;
import dev.geolens.ml.HttpMlClient;
import dev.geolens.ml.MlClient;
import dev.geolens.seo.GoogleOAuthClient;
import dev.geolens.seo.SeoStateStore;
import org.jooq.DSLContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Yeni taşınan bağlamların (engine/measure/governance/audit/delivery) Spring kablolaması.
 * DSLContext/TransactionTemplate yoksa Noop/null fallback kurulur — DB'siz spike
 * çalışır, DB varken @Repository DAO'lar (JooqScoreDao vb.) otomatik devreye girer.
 */
@Configuration
public class AppBeans {

    @Bean
    public Config geoLensConfig(@Value("${SCORE_WEIGHTS:}") String scoreWeights,
                                @Value("${SCORE_ALGORITHM_VERSION:2.0.0}") String version,
                                @Value("${INTENT_WEIGHT_SCALE:}") String intentScale,
                                @Value("${SAMPLE_COUNT:3}") int sampleCount) {
        Config cfg = new Config();
        cfg.scoreWeightsRaw = scoreWeights;
        cfg.scoreAlgorithmVersion = version;
        cfg.intentWeightScaleRaw = intentScale;
        cfg.sampleCount = sampleCount;
        return cfg;
    }

    @Bean
    public Registry engineRegistry(@Value("${OPENAI_API_KEY:}") String openAiKey,
                                   @Value("${GEMINI_API_KEY:}") String geminiKey,
                                   @Value("${PERPLEXITY_API_KEY:}") String perplexityKey) {
        Registry registry = new Registry();
        registry.register(new ChatGptAdapter(openAiKey, null));
        registry.register(new GeminiAdapter(geminiKey, null));
        registry.register(new PerplexityAdapter(perplexityKey, null));
        return registry;
    }

    /** ML_CLASSIFY_URL set edilmişse prompt sınıflandırma istemcisi kurulur (0421 A3-3); boşsa yok. */
    @Bean
    @ConditionalOnProperty(prefix = "measure.ml", name = "classify-url", matchIfMissing = false)
    public MlClient measureMlClient(@Value("${measure.ml.classify-url:}") String url,
                                    @Value("${measure.ml.timeout-ms:5000}") long timeoutMs) {
        return new HttpMlClient(url, Duration.ofMillis(timeoutMs));
    }

    @Bean
    public MeasureService measureService(Registry engines, Config cfg,
                                         ObjectProvider<JooqScoreDao> jdbcDao,
                                         ObjectProvider<MlClient> ml) {
        ScoreDao dao = jdbcDao.getIfAvailable();
        if (dao == null) {
            dao = new NoopScoreDao();
        }
        MlClient client = ml.getIfAvailable();
        return new MeasureService(dao, engines, cfg, client);
    }

    @Bean
    public UsageRecorder usageRecorder(ObjectProvider<DSLContext> dsl,
                                       ObjectProvider<TransactionTemplate> tx) {
        return new UsageRecorder(dsl.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public QuotaChecker quotaChecker(ObjectProvider<DSLContext> dsl,
                                     ObjectProvider<TransactionTemplate> tx) {
        return new QuotaChecker(dsl.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public AuditLogger auditLogger(ObjectProvider<DSLContext> dsl,
                                   ObjectProvider<TransactionTemplate> tx) {
        return new AuditLogger(dsl.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public AuditService auditService(ObjectProvider<DSLContext> dsl,
                                     ObjectProvider<TransactionTemplate> tx) {
        return new AuditService(dsl.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public EmailConfig emailConfig(@Value("${EMAIL_FROM_NAME:GeoLens}") String fromName,
                                   @Value("${EMAIL_FROM_ADDRESS:no-reply@geolens.dev}") String fromEmail,
                                   @Value("${SENDGRID_API_KEY:}") String sendGridKey) {
        return new EmailConfig(fromName, fromEmail, sendGridKey);
    }

    @Bean
    public DeliveryService deliveryService(EmailConfig emailConfig,
                                           ObjectProvider<DSLContext> dsl,
                                           ObjectProvider<TransactionTemplate> tx) {
        return new DeliveryService(emailConfig, dsl.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public JWTService jwtService(@Value("${JWT_SECRET:dev-secret-change-me}") String secret,
                                 @Value("${JWT_TOKEN_TTL_HOURS:2}") long ttlHours) {
        return new JWTService(secret, Duration.ofHours(ttlHours));
    }

    /** Faturalama (FR-A6): Stripe istemcisi — STRIPE_API_KEY boş/"mock" ise mock mod (gerçek ödeme alınmaz). */
    @Bean
    public StripeClient stripeClient(@Value("${STRIPE_API_KEY:}") String apiKey,
                                     @Value("${STRIPE_WEBHOOK_SECRET:}") String webhookSecret,
                                     @Value("${STRIPE_PRICE_IDS:}") String priceIdsRaw) {
        StripeClient client = new StripeClient(apiKey, webhookSecret);
        Map<String, String> ids = parseStripePriceIds(priceIdsRaw);
        if (!ids.isEmpty()) {
            client.setPriceIds(ids);
        }
        return client;
    }

    /** Faturalama (FR-A6): e-Fatura sağlayıcısı — EFATURA_MODE "mock" (varsayılan) veya "gib". */
    @Bean
    public EFaturaProvider efaturaProvider(@Value("${EFATURA_MODE:mock}") String mode) {
        return EFaturaProvider.create(mode);
    }

    /** STRIPE_PRICE_IDS env'ini ("tier=priceId,tier=priceId,...") map'e çevirir — Go {@code ParseStripePriceIDs} portu. */
    private static Map<String, String> parseStripePriceIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : raw.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            if (!key.isEmpty()) {
                out.put(key, kv[1].trim());
            }
        }
        return out;
    }

    /** Spike'ta bellek içi blacklist; üretimde Redis uygulamasıyla değiştirilir. */
    @Bean
    public TokenBlacklist tokenBlacklist() {
        return new TokenBlacklist() {
            private final java.util.Map<String, Long> store = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public boolean exists(String jti) {
                Long until = store.get(jti);
                if (until == null) {
                    return false;
                }
                if (System.currentTimeMillis() > until) {
                    store.remove(jti);
                    return false;
                }
                return true;
            }

            @Override
            public void set(String jti, Duration ttl) {
                store.put(jti, System.currentTimeMillis() + ttl.toMillis());
            }
        };
    }

    /** Davet vb. işlemsel e-postalar delivery servisi (SendGrid) üzerinden gider. */
    @Bean
    public TransactionalMailer transactionalMailer(DeliveryService delivery) {
        return delivery::sendEmail;
    }

    /** SEO (FR-B8): Google OAuth2 istemcisi — GOOGLE_OAUTH_CLIENT_ID boşsa yapılandırılmamış sayılır. */
    @Bean
    public GoogleOAuthClient googleOAuthClient(@Value("${GOOGLE_OAUTH_CLIENT_ID:}") String clientId,
                                               @Value("${GOOGLE_OAUTH_CLIENT_SECRET:}") String clientSecret) {
        return new GoogleOAuthClient(clientId, clientSecret);
    }

    /** SEO (FR-B8): OAuth state token deposu — spike'ta in-memory TTL (Go: governance.cache_store). */
    @Bean
    public SeoStateStore seoStateStore() {
        return new SeoStateStore();
    }

    /** Benchmark (FR-D5): sektör istatistiği toplayıcı — DP yapılandırması env'den (varsayılan NFR-13: ≥5 kiracı). */
    @Bean
    public BenchmarkAggregator benchmarkAggregator(ObjectProvider<DSLContext> dsl,
                                                   @Value("${BENCHMARK_MIN_TENANTS:5}") int minTenants,
                                                   @Value("${BENCHMARK_DP_EPSILON:1.0}") double epsilon) {
        DpConfig cfg = new DpConfig();
        cfg.minTenants = minTenants;
        cfg.epsilon = epsilon;
        return new BenchmarkAggregator(dsl.getIfAvailable(), cfg);
    }

    /** Replay (FR-D12): conversation snapshot motoru — DSLContext üzerinden çalışır. */
    @Bean
    public ReplayEngine replayEngine(DSLContext dsl) {
        return new ReplayEngine(dsl);
    }
}
