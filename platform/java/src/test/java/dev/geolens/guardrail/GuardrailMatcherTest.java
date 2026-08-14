package dev.geolens.guardrail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go guardrail/handler_test.go parity testleri — pattern eşleştirme + idempotency anahtarı. */
class GuardrailMatcherTest {

    @Test
    void evaluateRulePatternMatch() {
        // Go TestEvaluate_PatternMatch tablosu birebir
        assertTrue(evaluate("test", "this is a test prompt", ""));
        assertFalse(evaluate("xyz", "hello world", ""));
        assertFalse(evaluate("", "anything", ""));
        assertTrue(evaluate("/hello/", "say hello world", ""));
        assertTrue(evaluate("/hello/i", "HELLO world", ""));
    }

    @Test
    void evaluateRuleCombinesPromptAndResponse() {
        // prompt ve response birleşik metinde eşleşme
        assertTrue(evaluate("secret", "", "the secret is out"));
        assertTrue(evaluate("combined", "check this combined text", ""));
        assertFalse(evaluate("nope", "prompt only", "response only"));
    }

    @Test
    void compilePatternCaseInsensitiveRegex() {
        GuardrailMatcher.CompiledPattern cp = GuardrailMatcher.compilePattern("/test/i");
        assertTrue(cp.regex());
        assertNotNull(cp.pattern());
        assertTrue(cp.pattern().matcher("TEST").find());
    }

    @Test
    void compilePatternRegex() {
        GuardrailMatcher.CompiledPattern cp = GuardrailMatcher.compilePattern("/test/");
        assertTrue(cp.regex());
        assertNotNull(cp.pattern());
        assertTrue(cp.pattern().matcher("test").find());
    }

    @Test
    void compilePatternPlainIsNotRegex() {
        GuardrailMatcher.CompiledPattern cp = GuardrailMatcher.compilePattern("plain");
        assertFalse(cp.regex());
    }

    @Test
    void compilePatternShortOrEmptyNotRegex() {
        assertFalse(GuardrailMatcher.compilePattern("").regex());
        assertFalse(GuardrailMatcher.compilePattern("ab").regex());
        assertFalse(GuardrailMatcher.compilePattern(null).regex());
    }

    @Test
    void invalidPatternReturnsNoMatch() {
        // Go'da derleme hatası → evaluateRule false döner
        assertFalse(evaluate("/[/", "anything", ""));
    }

    @Test
    void idempotencyKeyDeterministic() {
        String k1 = GuardrailMatcher.idempotencyKey("T01", "R1", "prompt a", "response b");
        String k2 = GuardrailMatcher.idempotencyKey("T01", "R1", "prompt a", "response b");
        assertEquals(k1, k2);

        String k3 = GuardrailMatcher.idempotencyKey("T02", "R1", "prompt a", "response b");
        String k4 = GuardrailMatcher.idempotencyKey("T01", "R2", "prompt a", "response b");
        String k5 = GuardrailMatcher.idempotencyKey("T01", "R1", "prompt b", "response b");
        assertNotEquals(k1, k3);
        assertNotEquals(k1, k4);
        assertNotEquals(k1, k5);

        assertTrue(k1.startsWith("guardrail:"));
        assertFalse(k1.isEmpty());
    }

    private static boolean evaluate(String pattern, String prompt, String response) {
        return GuardrailMatcher.evaluateRule(new GuardRule("R1", "Rule", "custom", pattern, "block", "high"),
                prompt, response);
    }
}
