package dev.geolens.auth.web;

import dev.geolens.auth.JWTService;
import dev.geolens.auth.TokenBlacklist;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

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
    private JWTService jwt;

    @MockBean
    private DSLContext dsl;

    @MockBean
    private TransactionTemplate tx;

    @MockBean
    private TokenBlacklist blacklist;

    private static final String TENANT = "T01";

    @Test
    void inviteMemberSkipsEmailWhenNoMailer() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post("/v1/tenant/invitations")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"email\":\"kisi@example.com\",\"workspace_id\":\"WS1\",\"role\":\"viewer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email_sent").value(false))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
