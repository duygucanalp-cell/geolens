package dev.geolens.pilot.web;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go pilot.handler davranış parity testleri — Kurumsal Pilot REST (K4). */
@WebMvcTest(PilotController.class)
class PilotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- GetStatus ----------

    @Test
    void getStatusNotEnrolled() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(get("/v1/pilot/status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(false))
                .andExpect(jsonPath("$.message").value("Bu tenant pilot programına kayıtlı değil"));
    }

    @Test
    void getStatusQueryErrorGraceful() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/pilot/status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(false));
    }

    @Test
    void getStatusEnrolledStandard() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(pilotRow("standard")));

        mockMvc.perform(get("/v1/pilot/status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(true))
                .andExpect(jsonPath("$.program.program_name").value("Kurumsal Pilot Programı"))
                .andExpect(jsonPath("$.program.max_workspaces").value(10))
                .andExpect(jsonPath("$.program.max_engines").value(5))
                .andExpect(jsonPath("$.features.max_workspaces").value(10))
                .andExpect(jsonPath("$.features.premium_support").value(false))
                .andExpect(jsonPath("$.features.dedicated_slack_channel").value(false))
                .andExpect(jsonPath("$.features.custom_integration_support").value(false))
                .andExpect(jsonPath("$.features.extended_trial").value(true))
                .andExpect(jsonPath("$.days_remaining").isNumber());
    }

    @Test
    void getStatusEnrolledPremium() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(pilotRow("premium")));

        mockMvc.perform(get("/v1/pilot/status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.premium_support").value(true))
                .andExpect(jsonPath("$.features.dedicated_slack_channel").value(true))
                .andExpect(jsonPath("$.features.custom_integration_support").value(true));
    }

    // ---------- Enroll ----------

    @Test
    void enrollInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/pilot/enroll")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void enrollSuccess() throws Exception {
        mockMvc.perform(post("/v1/pilot/enroll")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"program_name\": \"Özel Pilot\", \"support_level\": \"premium\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("kayıtlı"))
                .andExpect(jsonPath("$.program").value("Özel Pilot"))
                .andExpect(jsonPath("$.support_level").value("premium"))
                .andExpect(jsonPath("$.auto_convert").value(true))
                .andExpect(jsonPath("$.trial_ends_at").isNotEmpty());
    }

    @Test
    void enrollDefaults() throws Exception {
        mockMvc.perform(post("/v1/pilot/enroll")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.program").value("Kurumsal Pilot Programı"))
                .andExpect(jsonPath("$.support_level").value("standard"));
    }

    @Test
    void enrollDbErrorReturns500() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/pilot/enroll")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"program_name\": \"P\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("pilot programa kayıt başarısız"));
    }

    // ---------- ExtendTrial ----------

    @Test
    void extendInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/pilot/extend")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void extendOutOfRangeReturns400() throws Exception {
        mockMvc.perform(post("/v1/pilot/extend")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"extra_days\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ek süre 1-365 gün arasında olmalıdır"));

        mockMvc.perform(post("/v1/pilot/extend")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"extra_days\": 366}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extendSuccess() throws Exception {
        mockMvc.perform(post("/v1/pilot/extend")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"extra_days\": 30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pilot süresi uzatıldı"));
    }

    // ---------- Cancel ----------

    @Test
    void cancelSuccess() throws Exception {
        mockMvc.perform(post("/v1/pilot/cancel")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pilot iptal edildi"));
    }

    // ---------- ListAll ----------

    @Test
    void listAllSuccess() throws Exception {
        when(dsl.fetch(anyString())).thenReturn(JooqTestData.records(List.of(pilotListRow())));
        // dsl.fetch() tek argümanlı çağrı — bazı DSLContext overload'ları için fallback

        mockMvc.perform(get("/v1/pilot/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pilots.length()").value(1))
                .andExpect(jsonPath("$.pilots[0].tenant_name").value("Acme"));
    }

    @Test
    void listAllQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString()))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/pilot/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pilots").isArray());
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> pilotRow(String supportLevel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "pt-1");
        m.put("tenant_id", TENANT);
        m.put("program_name", "Kurumsal Pilot Programı");
        m.put("trial_ends_at", Instant.now().plusSeconds(90L * 24 * 3600).toString());
        m.put("max_workspaces", 10);
        m.put("max_engines", 5);
        m.put("support_level", supportLevel);
        m.put("contact_email", "pilot@example.com");
        m.put("notes", "");
        m.put("auto_convert", true);
        m.put("status", "active");
        m.put("created_at", "2026-07-01T00:00:00Z");
        return m;
    }

    private static Map<String, Object> pilotListRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "pt-1");
        m.put("tenant_id", TENANT);
        m.put("name", "Acme");
        m.put("program_name", "Kurumsal Pilot Programı");
        m.put("trial_ends_at", "2026-10-30T00:00:00Z");
        m.put("support_level", "standard");
        m.put("status", "active");
        m.put("created_at", "2026-07-01T00:00:00Z");
        return m;
    }
}
