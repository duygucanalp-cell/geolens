package dev.geolens.config;

import dev.geolens.audit.AuditService;
import dev.geolens.delivery.DeliveryService;
import dev.geolens.engine.Registry;
import dev.geolens.governance.AuditLogger;
import dev.geolens.governance.QuotaChecker;
import dev.geolens.governance.UsageRecorder;
import dev.geolens.measure.MeasureService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * AppBeans kablolaması DB'siz doğrular: DSLContext/TransactionTemplate mock'lanır,
 * bileşen taraması yoktur — böylece @Repository/@Service bağımlılıkları yüklenmez.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AppBeans.class, AppBeansSmokeTest.TestSupport.class})
class AppBeansSmokeTest {

    @Configuration
    static class TestSupport {
        @Bean
        DSLContext dslContext() {
            return mock(DSLContext.class);
        }

        @Bean
        TransactionTemplate transactionTemplate() {
            return mock(TransactionTemplate.class);
        }
    }

    @Autowired
    Registry registry;

    @Autowired
    MeasureService measureService;

    @Autowired
    AuditService auditService;

    @Autowired
    DeliveryService deliveryService;

    @Autowired
    UsageRecorder usageRecorder;

    @Autowired
    QuotaChecker quotaChecker;

    @Autowired
    AuditLogger auditLogger;

    @Test
    void allBeansAreWired() {
        assertEquals(3, registry.count(), "chatgpt/gemini/perplexity kayıtlı olmalı");
        assertNotNull(measureService);
        assertNotNull(auditService);
        assertNotNull(deliveryService);
        assertNotNull(usageRecorder);
        assertNotNull(quotaChecker);
        assertNotNull(auditLogger);
    }
}
