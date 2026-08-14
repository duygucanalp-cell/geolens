package dev.geolens.privacy.web;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go privacy/handler_test.go parity testleri — KVKK/GDPR veri silme ve dışa aktarım. */
@WebMvcTest(PrivacyController.class)
class PrivacyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DSLContext dsl;

    @MockBean
    private TransactionTemplate tx;

    private static final String TENANT = "T01";
    private static final String USER = "U01";

    private void runInTx() {
        when(tx.execute(any(TransactionCallback.class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<String> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private void stubRole(String role) {
        when(dsl.fetchOne(contains("config.memberships"), any(Object[].class)))
                .thenReturn(JooqTestData.record(role));
    }

    // ---------- ExportData ----------

    @Test
    void exportDataMissingTenantReturns401() throws Exception {
        mockMvc.perform(get("/v1/account/data"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("kimlik doğrulama gerekli"));
    }

    @Test
    void exportDataSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(
                        java.util.Map.<String, Object>of("id", "U01", "email", "a@b.com", "full_name", "A", "created_at", "2026-08-14T00:00:00Z")));

        mockMvc.perform(get("/v1/account/data").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value(TENANT))
                .andExpect(jsonPath("$.format_version").value(1))
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users[0].email").value("a@b.com"))
                .andExpect(jsonPath("$.memberships").isArray())
                .andExpect(jsonPath("$.brands").isArray())
                .andExpect(jsonPath("$.prompt_sets").isArray())
                .andExpect(jsonPath("$.measurement_scores").isArray());
    }

    @Test
    void exportDataQueryErrorStillReturnsPayload() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/account/data").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value(TENANT))
                .andExpect(jsonPath("$.users").isArray());
    }

    // ---------- RequestDeletion ----------

    @Test
    void requestDeletionNoUserReturns401() throws Exception {
        mockMvc.perform(post("/v1/account/deletion")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestDeletionInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/account/deletion")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void requestDeletionEditorCreatesPending() throws Exception {
        stubRole("editor");
        when(dsl.fetchOne(contains("INSERT INTO privacy.deletion_requests"), any(Object[].class)))
                .thenReturn(JooqTestData.record("R01"));

        mockMvc.perform(post("/v1/account/deletion")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("R01"))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void requestDeletionAdminAnonymizesDirectly() throws Exception {
        runInTx();
        stubRole("admin");
        when(dsl.fetchOne(contains("INSERT INTO privacy.deletion_requests"), any(Object[].class)))
                .thenReturn(JooqTestData.record("R01"));

        mockMvc.perform(post("/v1/privacy/delete")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.id").value("R01"));
    }

    // ---------- ListDeletionRequests ----------

    @Test
    void listDeletionRequestsNoAdminReturns403() throws Exception {
        stubRole("editor");

        mockMvc.perform(get("/v1/deletion-requests")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("bu işlem için admin yetkisi gerekli"));
    }

    @Test
    void listDeletionRequestsNoRoleReturns403() throws Exception {
        stubRole("");

        mockMvc.perform(get("/v1/deletion-requests")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void listDeletionRequestsAdminSuccess() throws Exception {
        stubRole("admin");
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(
                        java.util.Map.<String, Object>of(
                                "id", "R01", "requested_by", "U01", "status", "pending",
                                "reason", "test", "requested_at", "2026-08-14T00:00:00Z",
                                "processed_at", "1970-01-01T00:00:00Z", "notes", "")));

        mockMvc.perform(get("/v1/deletion-requests")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests[0].id").value("R01"))
                .andExpect(jsonPath("$.requests[0].status").value("pending"));
    }

    // ---------- ProcessDeletionRequest ----------

    @Test
    void processDeletionRequestNoAdminReturns403() throws Exception {
        stubRole("editor");

        mockMvc.perform(post("/v1/deletion-requests/R01/process")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void processDeletionRequestInvalidActionReturns400() throws Exception {
        stubRole("admin");

        mockMvc.perform(post("/v1/deletion-requests/R01/process")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("{\"action\":\"bogus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("action 'approve' veya 'reject' olmalıdır"));
    }

    @Test
    void processDeletionRequestApproveSuccess() throws Exception {
        runInTx();
        stubRole("admin");
        when(dsl.fetchOne(contains("UPDATE privacy.deletion_requests"), any(Object[].class)))
                .thenReturn(JooqTestData.record("R01"));

        mockMvc.perform(post("/v1/deletion-requests/R01/process")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("{\"action\":\"approve\",\"notes\":\"onay\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));
    }

    @Test
    void processDeletionRequestApproveNotFoundReturns404() throws Exception {
        runInTx();
        stubRole("admin");
        when(dsl.fetchOne(contains("UPDATE privacy.deletion_requests"), any(Object[].class)))
                .thenReturn(null);

        mockMvc.perform(post("/v1/deletion-requests/R01/process")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("talep bulunamadı veya zaten işlenmiş"));
    }

    @Test
    void processDeletionRequestRejectSuccess() throws Exception {
        stubRole("admin");

        mockMvc.perform(post("/v1/deletion-requests/R01/process")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("{\"action\":\"reject\",\"notes\":\"red\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.id").value("R01"));
    }
}
