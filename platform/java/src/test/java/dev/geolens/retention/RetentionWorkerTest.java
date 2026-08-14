package dev.geolens.retention;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Go retention/worker.go davranış testleri — politika uygulama. */
class RetentionWorkerTest {

    @Test
    void processExpiredAppliesPolicies() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        RetentionWorker worker = new RetentionWorker(dsl);

        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        policyRow("measurement", 365, "delete"),
                        policyRow("alert", 90, "anonymize"),
                        policyRow("report", 180, "archive_s3"))));

        worker.processExpired();

        // delete + anonymize + archive_s3 = 3 execute
        verify(dsl, org.mockito.Mockito.times(3)).execute(anyString(), any(Object[].class));
    }

    @Test
    void processExpiredQueryErrorSwallowed() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        RetentionWorker worker = new RetentionWorker(dsl);

        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        worker.processExpired(); // hata fırlatılmamalı
    }

    @Test
    void processExpiredSkipsUnknownEntityType() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        RetentionWorker worker = new RetentionWorker(dsl);

        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(policyRow("unknown_type", 30, "delete"))));

        worker.processExpired(); // bilinmeyen tür yok sayılır — execute çağrılmaz
        verify(dsl, org.mockito.Mockito.times(0)).execute(anyString(), any(Object[].class));
    }

    private static Map<String, Object> policyRow(String entityType, int retentionDays, String strategy) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "p-" + entityType);
        m.put("tenant_id", "T01");
        m.put("entity_type", entityType);
        m.put("retention_days", retentionDays);
        m.put("archival_strategy", strategy);
        return m;
    }
}
