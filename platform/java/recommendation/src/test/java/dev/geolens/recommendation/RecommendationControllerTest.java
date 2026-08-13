package dev.geolens.recommendation;

import dev.geolens.recommendation.domain.Category;
import dev.geolens.recommendation.domain.Condition;
import dev.geolens.recommendation.domain.EvidenceLabel;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.Rule;
import dev.geolens.recommendation.domain.Severity;
import dev.geolens.recommendation.persistence.AppliedRecommendation;
import dev.geolens.recommendation.persistence.RecommendationDao;
import dev.geolens.recommendation.persistence.RecommendationNotFoundException;
import dev.geolens.recommendation.persistence.ScoreAt;
import dev.geolens.recommendation.service.RecommendationService;
import dev.geolens.recommendation.web.RecommendationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go handler davranışı ile route/response parity testleri. */
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService service;

    @MockBean
    private RecommendationDao dao;

    private static final String TENANT = "T01";
    private static final String WS = "WS01";

    private static Recommendation rec() {
        return new Recommendation("R1", "T01", "WS01", "B01", Category.VISIBILITY, Severity.HIGH,
                EvidenceLabel.CORRELATIONAL, "Görünürlük skorunuz düşüyor", "detay", "/audit", 85,
                false, false, Instant.parse("2026-08-13T10:00:00Z"));
    }

    @Test
    void listReturnsRecommendations() throws Exception {
        when(service.evaluate(isNull(), eq(WS), eq(TENANT))).thenReturn(List.of(rec()));

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations", WS).header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("R1"))
                .andExpect(jsonPath("$[0].tenant_id").value("T01"))
                .andExpect(jsonPath("$[0].brand_id").value("B01"))
                .andExpect(jsonPath("$[0].category").value("visibility"))
                .andExpect(jsonPath("$[0].severity").value("high"))
                .andExpect(jsonPath("$[0].evidence").value("korelasyonel"))
                .andExpect(jsonPath("$[0].title").value("Görünürlük skorunuz düşüyor"));
    }

    @Test
    void listPassesBrandIdQueryParam() throws Exception {
        when(service.evaluate(eq("B01"), eq(WS), eq(TENANT))).thenReturn(List.of());

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations", WS)
                        .header("X-Tenant-ID", TENANT)
                        .queryParam("brand_id", "B01"))
                .andExpect(status().isOk());

        verify(service).evaluate("B01", WS, TENANT);
    }

    @Test
    void listReturnsEmptyOnServiceError() throws Exception {
        when(service.evaluate(isNull(), eq(WS), eq(TENANT)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations", WS).header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("öneriler alınamadı"));
    }

    @Test
    void markAppliedReturnsAppliedStatus() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/recommendations/{id}/apply", WS, "R1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("applied"));

        verify(service).markApplied("R1", TENANT, WS);
    }

    @Test
    void markAppliedNotFoundReturns500LikeGo() throws Exception {
        doThrow(new RecommendationNotFoundException("yok")).when(service).markApplied("YOK", TENANT, WS);

        mockMvc.perform(post("/v1/workspaces/{ws}/recommendations/{id}/apply", WS, "YOK")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("öneri uygulanamadı"));
    }

    @Test
    void markDismissedReturnsDismissedStatus() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/recommendations/{id}/dismiss", WS, "R1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("dismissed"));

        verify(service).markDismissed("R1", TENANT, WS);
    }

    @Test
    void impactReturnsBeforeAfterAndChange() throws Exception {
        Instant appliedAt = Instant.parse("2026-08-10T08:00:00Z");
        when(dao.loadApplied(eq("R1"), eq(WS), eq(TENANT)))
                .thenReturn(AppliedRecommendation.of("B01", appliedAt));
        when(dao.loadScoreAt(eq("B01"), eq(WS), eq(TENANT), eq(appliedAt), eq(true)))
                .thenReturn(new ScoreAt(30, "low", appliedAt.minusSeconds(86400)));
        when(dao.loadScoreAt(eq("B01"), eq(WS), eq(TENANT), eq(appliedAt), eq(false)))
                .thenReturn(new ScoreAt(55, "full", appliedAt.plusSeconds(86400)));

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations/{id}/impact", WS, "R1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation_id").value("R1"))
                .andExpect(jsonPath("$.brand_id").value("B01"))
                .andExpect(jsonPath("$.applied_at").value("2026-08-10T08:00:00Z"))
                .andExpect(jsonPath("$.before.value").value(30.0))
                .andExpect(jsonPath("$.before.fidelity").value("low"))
                .andExpect(jsonPath("$.after.value").value(55.0))
                .andExpect(jsonPath("$.after.fidelity").value("full"))
                .andExpect(jsonPath("$.change").value(25.0));
    }

    @Test
    void impactNotFoundReturns404() throws Exception {
        when(dao.loadApplied(eq("YOK"), eq(WS), eq(TENANT))).thenReturn(null);

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations/{id}/impact", WS, "YOK")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("öneri bulunamadı veya henüz uygulanmamış"));
    }

    @Test
    void impactWithoutAppliedAtReturns409() throws Exception {
        when(dao.loadApplied(eq("R1"), eq(WS), eq(TENANT))).thenReturn(AppliedRecommendation.of("B01", null));

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations/{id}/impact", WS, "R1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("öneri uygulanma tarihi bulunamadı"));
    }

    @Test
    void impactWithoutScoresReturnsZeroShapes() throws Exception {
        when(dao.loadApplied(eq("R1"), eq(WS), eq(TENANT)))
                .thenReturn(AppliedRecommendation.of("B01", Instant.parse("2026-08-10T08:00:00Z")));
        when(dao.loadScoreAt(org.mockito.Mockito.any(), eq(WS), eq(TENANT), org.mockito.Mockito.any(), org.mockito.Mockito.anyBoolean()))
                .thenReturn(null);

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations/{id}/impact", WS, "R1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.before.value").value(0.0))
                .andExpect(jsonPath("$.before.measured_at").value(""))
                .andExpect(jsonPath("$.change").value(0.0));
    }

    @Test
    void rulesReturnsLibraryAndCount() throws Exception {
        when(service.getRules()).thenReturn(List.of(
                new Rule("rule-score-drop", "Skor Düşüşü Tespiti", "desc", Category.VISIBILITY, Severity.HIGH,
                        EvidenceLabel.CORRELATIONAL, List.of(new Condition("score.drop", "gt", 10.0)),
                        "Görünürlük skorunuz düşüyor", "detay", null, true, null)));

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations/rules", WS).header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].id").value("rule-score-drop"))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void rulesBySectorReturnsPackage() throws Exception {
        when(service.getRulesBySector("finans")).thenReturn(List.of(
                new Rule("rule-finance-trust", "Güven Sinyali Eksik", "desc", Category.TECHNICAL, Severity.HIGH,
                        EvidenceLabel.CORRELATIONAL, List.of(new Condition("score.value", "lt", 70.0)),
                        "Güven sinyalleriniz AI motorları için yetersiz", "detay", "/audit", true, null)));

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations/rules/{sector}", WS, "finans")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector").value("finans"))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void rulesBySectorUnknownReturnsEmpty() throws Exception {
        when(service.getRulesBySector("bilinmeyen")).thenReturn(List.of());

        mockMvc.perform(get("/v1/workspaces/{ws}/recommendations/rules/{sector}", WS, "bilinmeyen")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}