package dev.geolens.delivery.web;

import dev.geolens.delivery.DeliveryService;
import dev.geolens.delivery.NotificationSettings;
import dev.geolens.delivery.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go delivery handler davranışı ile route/response parity testleri. */
@WebMvcTest(DeliveryController.class)
class DeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService service;

    private static final String TENANT = "T01";
    private static final String WS = "WS01";

    @Test
    void updateSettingsInvalidJSONReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/{ws}/notifications/settings", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void updateSettingsEmptyBodyReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/{ws}/notifications/settings", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void updateSettingsValidationErrorReturns400() throws Exception {
        doThrow(new ValidationException("geçersiz digest_day")).when(service).updateSettings(any(), anyString());

        mockMvc.perform(put("/v1/workspaces/{ws}/notifications/settings", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"digest_day\":\"bozuk\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz digest_day"));
    }

    @Test
    void getSettingsReturnsDefaults() throws Exception {
        when(service.getSettings(WS, TENANT)).thenReturn(NotificationSettings.defaults(WS));

        mockMvc.perform(get("/v1/workspaces/{ws}/notifications/settings", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace_id").value(WS))
                .andExpect(jsonPath("$.digest_enabled").value(true));
    }

    @Test
    void sendTestEmailReturnsSent() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/notifications/test", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"email\":\"ornek@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.to").value("ornek@example.com"));
    }

    @Test
    void listNotificationsReturnsList() throws Exception {
        when(service.listInAppNotifications(TENANT, WS, false, 50)).thenReturn(List.of());

        mockMvc.perform(get("/v1/workspaces/{ws}/notifications", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void markNotificationReadReturnsRead() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/notifications/{id}/read", WS, "N01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("read"));
    }
}
