package dev.geolens.recommendation.config;

import dev.geolens.recommendation.persistence.JdbcRecommendationDao;
import dev.geolens.recommendation.persistence.RecommendationDao;
import dev.geolens.recommendation.service.RecommendationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC DAO ve servis bean'lerini kurar. */
@Configuration
public class RecommendationDataConfig {

    @Bean
    public RecommendationDao recommendationDao(JdbcTemplate jdbcTemplate, PlatformTransactionManager txManager) {
        return new JdbcRecommendationDao(jdbcTemplate, new TransactionTemplate(txManager));
    }

    @Bean
    public RecommendationService recommendationService(RecommendationDao dao) {
        return new RecommendationService(dao);
    }
}