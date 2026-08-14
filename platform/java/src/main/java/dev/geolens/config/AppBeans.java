package dev.geolens.config;

import dev.geolens.audit.AuditService;
import dev.geolens.auth.JWTService;
import dev.geolens.auth.TokenBlacklist;
import dev.geolens.auth.web.TransactionalMailer;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/**
 * Yeni taşınan bağlamların (engine/measure/governance/audit/delivery) Spring kablolaması.
 * JdbcTemplate/TransactionTemplate yoksa Noop/null fallback kurulur — DB'siz spike
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
    public UsageRecorder usageRecorder(ObjectProvider<JdbcTemplate> jdbc,
                                       ObjectProvider<TransactionTemplate> tx) {
        return new UsageRecorder(jdbc.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public QuotaChecker quotaChecker(ObjectProvider<JdbcTemplate> jdbc,
                                     ObjectProvider<TransactionTemplate> tx) {
        return new QuotaChecker(jdbc.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public AuditLogger auditLogger(ObjectProvider<JdbcTemplate> jdbc,
                                   ObjectProvider<TransactionTemplate> tx) {
        return new AuditLogger(jdbc.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public AuditService auditService(ObjectProvider<JdbcTemplate> jdbc,
                                     ObjectProvider<TransactionTemplate> tx) {
        return new AuditService(jdbc.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public EmailConfig emailConfig(@Value("${EMAIL_FROM_NAME:GeoLens}") String fromName,
                                   @Value("${EMAIL_FROM_ADDRESS:no-reply@geolens.dev}") String fromEmail,
                                   @Value("${SENDGRID_API_KEY:}") String sendGridKey) {
        return new EmailConfig(fromName, fromEmail, sendGridKey);
    }

    @Bean
    public DeliveryService deliveryService(EmailConfig emailConfig,
                                           ObjectProvider<JdbcTemplate> jdbc,
                                           ObjectProvider<TransactionTemplate> tx) {
        return new DeliveryService(emailConfig, jdbc.getIfAvailable(), tx.getIfAvailable());
    }

    @Bean
    public JWTService jwtService(@Value("${JWT_SECRET:dev-secret-change-me}") String secret,
                                 @Value("${JWT_TOKEN_TTL_HOURS:2}") long ttlHours) {
        return new JWTService(secret, Duration.ofHours(ttlHours));
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
}
