package dev.geolens.privacy.web;

import dev.geolens.privacy.service.PrivacyService;
import dev.geolens.common.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
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
    private PrivacyService privacyService;

    private static final String TENANT = "T01";
    private static final String USER = "U01";

    // ---------- ExportData ----------

    @Test
    void exportDataMissingTenantReturns401() throws Exception {
        mockMvc.perform(get("/v1/account/data"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("kimlik doğrulama gerekli"));
    }

    @Test
    void exportDataSuccess() throws Exception {
        when(privacyService.exportData(any(), any()))
                .thenReturn(Map.of(
                        "tenant_id", TENANT,
                        "exported_at", "2026-08-14T00:00:00Z",
                        "format_version", 1,
                        "users", List.of(Map.<String, Object>of(
                                "id", "U01", "email", "a@b.com", "full_name", "A", "created_at", "2026-08-14T00:00:00Z")),
                        "memberships", List.of(),
                        "brands", List.of(),
                        "prompt_sets", List.of(),
                        "measurement_scores", List.of()));

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
        when(privacyService.exportData(any(), any()))
                .thenReturn(Map.of("tenant_id", TENANT, "users", List.of()));

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
        when(privacyService.requestDeletion(any(), any(), any()))
                .thenReturn(new DeletionResponse("R01", "pending",
                        "Veri silme talebiniz alındı. Admin kullanıcı talebinizi değerlendirecektir."));

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
        when(privacyService.requestDeletion(any(), any(), any()))
                .thenReturn(new DeletionResponse("R01", "completed",
                        "Hesabınız ve tüm kişisel verileriniz başarıyla anonimleştirildi."));

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
        when(privacyService.listDeletionRequests(any(), any()))
                .thenThrow(new ServiceException(HttpStatus.FORBIDDEN, "bu işlem için admin yetkisi gerekli"));

        mockMvc.perform(get("/v1/deletion-requests")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("bu işlem için admin yetkisi gerekli"));
    }

    @Test
    void listDeletionRequestsNoRoleReturns403() throws Exception {
        when(privacyService.listDeletionRequests(any(), any()))
                .thenThrow(new ServiceException(HttpStatus.FORBIDDEN, "bu işlem için admin yetkisi gerekli"));

        mockMvc.perform(get("/v1/deletion-requests")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void listDeletionRequestsAdminSuccess() throws Exception {
        when(privacyService.listDeletionRequests(any(), any()))
                .thenReturn(Map.of("requests", List.of(Map.<String, Object>of(
                        "id", "R01", "requested_by", "U01", "status", "pending",
                        "reason", "test", "requested_at", "2026-08-14T00:00:00Z",
                        "processed_at", "1970-01-01T00:00:00Z", "notes", ""))));

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
        when(privacyService.processDeletionRequest(any(), any(), any(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.FORBIDDEN, "bu işlem için admin yetkisi gerekli"));

        mockMvc.perform(post("/v1/deletion-requests/R01/process")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-User-ID", USER)
                        .contentType("application/json")
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void processDeletionRequestInvalidActionReturns400() throws Exception {
        when(privacyService.processDeletionRequest(any(), any(), any(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.BAD_REQUEST, "action 'approve' veya 'reject' olmalıdır"));

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
        when(privacyService.processDeletionRequest(any(), any(), any(), any(), any()))
                .thenReturn(new DeletionResponse("R01", "completed",
                        "Talep onaylandı ve veriler anonimleştirildi."));

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
        when(privacyService.processDeletionRequest(any(), any(), any(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "talep bulunamadı veya zaten işlenmiş"));

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
        when(privacyService.processDeletionRequest(any(), any(), any(), any(), any()))
                .thenReturn(new DeletionResponse("R01", "rejected", "Talep reddedildi."));

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
