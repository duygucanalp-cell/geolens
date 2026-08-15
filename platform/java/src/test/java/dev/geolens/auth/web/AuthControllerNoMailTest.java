package dev.geolens.auth.web;

import dev.geolens.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go TestInviteMember_MockModeSkipsEmail parity — mail yok + baseURL boş → email_sent=false. */
@WebMvcTest(AuthController.class)
class AuthControllerNoMailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    private static final String TENANT = "T01";

    @Test
    void inviteMemberSkipsEmailWhenNoMailer() throws Exception {
        when(authService.inviteMember(anyString(), any(), any())).thenReturn(inviteResult(false));

        mockMvc.perform(post("/v1/tenant/invitations")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"email\":\"kisi@example.com\",\"workspace_id\":\"WS1\",\"role\":\"viewer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email_sent").value(false))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    private static Map<String, Object> inviteResult(boolean emailSent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "invited");
        body.put("email", "x@example.com");
        body.put("token", "abc123");
        body.put("email_sent", emailSent);
        return body;
    }
}
