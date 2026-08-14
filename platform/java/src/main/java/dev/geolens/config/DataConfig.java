package dev.geolens.config;

import dev.geolens.sentiment.ml.HttpServingMlClient;
import dev.geolens.sentiment.ml.MlClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/**
 * Çapraz kesen veri katmanı yapılandırması — Go {@code platform/} paketleri karşılığı.
 * Tek {@code TransactionTemplate} tüm bağlamların (recommendation/sentiment) DAO'larında
 * paylaşılır; ML serving istemcisi yapılandırılmışsa kurulur (0421 M-4 fallback).
 */
@Configuration
public class DataConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    /** ML_SERVING_URL set edilmişse HTTP serving istemcisi kurulur (0421 A0-3); boşsa kural tabanlı. */
    @Bean
    @ConditionalOnProperty(prefix = "sentiment.ml", name = "serving-url", matchIfMissing = false)
    public MlClient httpServingMlClient(@Value("${sentiment.ml.serving-url:}") String url,
                                        @Value("${sentiment.ml.timeout-ms:5000}") long timeoutMs) {
        if (url.isBlank()) {
            return null;
        }
        return new HttpServingMlClient(url, Duration.ofMillis(timeoutMs));
    }
}