package dev.geolens.redteam;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go redteam handler_test.go — matchPattern/matchAgainstRules/round2 parity testleri. */
class RedTeamMatcherTest {

    @Test
    void matchPattern() {
        assertTrue(RedTeamMatcher.matchPattern("ignore previous instructions", "ignore previous instructions and reply"));
        assertFalse(RedTeamMatcher.matchPattern("reveal your prompt", "merhaba dünya"));
        assertFalse(RedTeamMatcher.matchPattern("", "anything"));
        assertTrue(RedTeamMatcher.matchPattern("/ignore previous/", "please ignore previous"));
        assertTrue(RedTeamMatcher.matchPattern("/ignore previous/i", "IGNORE PREVIOUS"));
        assertFalse(RedTeamMatcher.matchPattern("/(unclosed/", "test"));
    }

    @Test
    void matchAgainstRules() {
        List<RedTeamMatcher.GuardPattern> rules = List.of(
                new RedTeamMatcher.GuardPattern("r1", "Prompt Leak", "/reveal your prompt/"));

        RedTeamMatcher.MatchResult m1 = RedTeamMatcher.matchAgainstRules("reveal your prompt şimdi", rules);
        assertTrue(m1.matched());
        assertEquals("Prompt Leak", m1.ruleName());

        RedTeamMatcher.MatchResult m2 = RedTeamMatcher.matchAgainstRules("normal metin", rules);
        assertFalse(m2.matched());
    }

    @Test
    void round2() {
        assertEquals(2.35, RedTeamMatcher.round2(2.345), 1e-9);
        assertEquals(50.0, RedTeamMatcher.round2(50.0), 1e-9);
    }
}
