package dev.geolens.audit;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code audit/service_test.go} portu. */
class AuditServiceTest {

    // ---- computeOverallScore ----

    private static AuditResult result(RobotsTxtCheck robots, BotAccessCheck bot, SSRCheck ssr, SSRFCheck ssrf) {
        return new AuditResult("", "", "", "", "", "", robots, bot, ssr, ssrf, 0, List.of(), null);
    }

    @Test
    void computeOverallScorePerfect() {
        AuditResult r = result(
                new RobotsTxtCheck(true, true, List.of(), false),
                new BotAccessCheck(true, 200, 5, List.of()),
                new SSRCheck(true, true, true, 100),
                new SSRFCheck(true, false, false, true));
        assertEquals(100.0, AuditService.computeOverallScore(r));
    }

    @Test
    void computeOverallScoreRobotsDisallowedAll() {
        AuditResult r = result(
                new RobotsTxtCheck(true, false, List.of("/"), true),
                new BotAccessCheck(true, 200, 5, List.of()),
                new SSRCheck(true, true, true, 100),
                new SSRFCheck(true, false, false, true));
        assertEquals(60.0, AuditService.computeOverallScore(r));
    }

    @Test
    void computeOverallScoreBotInaccessible() {
        AuditResult r = result(
                new RobotsTxtCheck(true, true, List.of(), false),
                new BotAccessCheck(false, 403, 5, List.of()),
                new SSRCheck(true, true, true, 100),
                new SSRFCheck(true, false, false, true));
        assertEquals(80.0, AuditService.computeOverallScore(r));
    }

    @Test
    void computeOverallScoreAllSsrMissing() {
        AuditResult r = result(
                new RobotsTxtCheck(true, true, List.of(), false),
                new BotAccessCheck(true, 200, 5, List.of()),
                new SSRCheck(false, false, false, 100),
                new SSRFCheck(true, false, false, true));
        assertEquals(75.0, AuditService.computeOverallScore(r));
    }

    @Test
    void computeOverallScoreNoSecurity() {
        AuditResult r = result(
                new RobotsTxtCheck(true, true, List.of(), false),
                new BotAccessCheck(true, 200, 5, List.of()),
                new SSRCheck(true, true, true, 100),
                new SSRFCheck(false, false, false, false));
        assertEquals(90.0, AuditService.computeOverallScore(r));
    }

    @Test
    void computeOverallScoreNegativeProtection() {
        AuditResult r = result(
                new RobotsTxtCheck(true, false, List.of("/"), true),
                new BotAccessCheck(false, 403, 5, List.of()),
                new SSRCheck(false, false, false, 100),
                new SSRFCheck(false, false, false, false));
        assertEquals(5.0, AuditService.computeOverallScore(r));
    }

    @Test
    void computeOverallScoreNilChecks() {
        assertEquals(100.0, AuditService.computeOverallScore(result(null, null, null, null)));
    }

    // ---- normalizeURL ----

    @Test
    void normalizeUrlHttps() {
        assertEquals("https://example.com", AuditService.normalizeUrl("https://example.com"));
    }

    @Test
    void normalizeUrlWithPath() {
        assertEquals("https://example.com", AuditService.normalizeUrl("https://example.com/path/to/page"));
    }

    @Test
    void normalizeUrlHttp() {
        assertEquals("http://test.org", AuditService.normalizeUrl("http://test.org/page"));
    }

    @Test
    void normalizeUrlNoProtocol() {
        assertEquals("https://example.com", AuditService.normalizeUrl("example.com"));
    }

    @Test
    void normalizeUrlSubdomain() {
        assertEquals("https://sub.domain.co.uk", AuditService.normalizeUrl("https://sub.domain.co.uk/path"));
    }

    @Test
    void normalizeUrlEmptyString() {
        assertEquals("https://", AuditService.normalizeUrl(""));
    }

    // ---- parseRobotsTxtContent ----

    private static RobotsTxtCheck parse(String content, boolean exists) {
        return AuditService.parseRobotsTxtContent(content.getBytes(StandardCharsets.UTF_8), exists);
    }

    @Test
    void parseRobotsTxtEmpty() {
        RobotsTxtCheck check = parse("", true);
        assertTrue(check.exists());
        assertTrue(check.allowsAIBots());
        assertFalse(check.disallowedAll());
    }

    @Test
    void parseRobotsTxtDisallowAll() {
        RobotsTxtCheck check = parse("User-agent: *\nDisallow: /", true);
        assertTrue(check.disallowedAll());
        assertFalse(check.allowsAIBots());
    }

    @Test
    void parseRobotsTxtPartialBlock() {
        RobotsTxtCheck check = parse("User-agent: *\nDisallow: /admin/\nDisallow: /private/", true);
        assertFalse(check.disallowedAll());
        assertTrue(check.allowsAIBots());
        assertEquals(2, check.blockedPaths().size());
    }

    @Test
    void parseRobotsTxtAiBotSpecificBlock() {
        RobotsTxtCheck check = parse("User-agent: ChatGPT-User\nDisallow: /\nUser-agent: *\nAllow: /", true);
        assertFalse(check.allowsAIBots());
    }

    @Test
    void parseRobotsTxtNotFound() {
        RobotsTxtCheck check = parse("", false);
        assertFalse(check.exists());
        assertTrue(check.allowsAIBots());
    }

    @Test
    void parseRobotsTxtMultipleAiBots() {
        RobotsTxtCheck check = parse(
                "User-agent: Google-Extended\nDisallow: /\nUser-agent: ChatGPT-User\nDisallow: /\nUser-agent: *\nAllow: /",
                true);
        assertFalse(check.disallowedAll());
        assertFalse(check.allowsAIBots());
    }

    @Test
    void parseRobotsTxtNoUserAgent() {
        RobotsTxtCheck check = parse("Disallow: /", true);
        assertTrue(check.disallowedAll());
    }

    @Test
    void parseRobotsTxtCaseInsensitive() {
        RobotsTxtCheck check = parse("USER-AGENT: *\nDISALLOW: /", true);
        assertTrue(check.disallowedAll());
    }
}