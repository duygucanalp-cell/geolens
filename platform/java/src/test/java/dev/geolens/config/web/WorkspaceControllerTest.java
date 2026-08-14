package dev.geolens.config.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go config/workspace_handler.go parity testleri — arşivle/geri al/devret. */
@WebMvcTest(WorkspaceController.class)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbc;

    @MockBean
    private TransactionTemplate tx;

    private static final String TENANT = "T01";

    @Test
    void archiveWorkspaceSuccess() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post("/v1/workspaces/WS01/archive")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("archived"))
                .andExpect(jsonPath("$.archived_at").isNotEmpty());
    }

    @Test
    void archiveWorkspaceDBErrorReturns500() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/workspaces/WS01/archive")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("arşivleme başarısız"));
    }

    @Test
    void unarchiveWorkspaceSuccess() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post("/v1/workspaces/WS01/unarchive")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("unarchived"));
    }

    @Test
    void unarchiveWorkspaceDBErrorReturns500() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/workspaces/WS01/unarchive")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("geri alma başarısız"));
    }

    @Test
    void transferWorkspaceSuccess() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .thenReturn(true);
        when(tx.execute(any(TransactionCallback.class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });

        mockMvc.perform(post("/v1/workspaces/WS01/transfer")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"target_tenant_id\":\"T02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("transferred"))
                .andExpect(jsonPath("$.target_tenant_id").value("T02"));
    }

    @Test
    void transferWorkspaceMissingTargetReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/transfer")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("hedef kiracı ID gerekli"));
    }

    @Test
    void transferWorkspaceTargetNotFoundReturns404() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .thenReturn(false);

        mockMvc.perform(post("/v1/workspaces/WS01/transfer")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"target_tenant_id\":\"BOGUS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("hedef kiracı bulunamadı"));
    }

    @Test
    void transferWorkspaceDBErrorReturns500() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .thenReturn(true);
        when(tx.execute(any(TransactionCallback.class)))
                .thenThrow(new RuntimeException("tx error"));

        mockMvc.perform(post("/v1/workspaces/WS01/transfer")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"target_tenant_id\":\"T02\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("devir başarısız"));
    }
}
