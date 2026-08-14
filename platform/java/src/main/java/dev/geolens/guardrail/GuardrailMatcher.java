package dev.geolens.guardrail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Guardrail pattern eşleştirme motoru — Go {@code compilePattern}/{@code evaluateRule} portu (R3).
 * <p>Pattern sözdizimi: {@code /.../i} → büyük/küçük harf duyarsız regex, {@code /.../} → regex,
 * diğer → düz alt dize eşleşmesi.
 */
public final class GuardrailMatcher {

    private GuardrailMatcher() {
    }

    /**
     * Go {@code compilePattern} karşılığı: deseni regex'e derler.
     *
     * @return derlenmiş regex veya {@code null} (regex değilse), ikinci eleman regex mi
     * @throws PatternSyntaxException desen geçersizse (Go'da derleme hatası → evaluateRule false döner)
     */
    public static CompiledPattern compilePattern(String pattern) {
        if (pattern == null || pattern.length() <= 2) {
            return new CompiledPattern(null, false);
        }

        // Büyük/küçük harf duyarsız regex: /pattern/i
        if (pattern.endsWith("/i") && pattern.charAt(0) == '/') {
            return new CompiledPattern(Pattern.compile("(?i)" + pattern.substring(1, pattern.length() - 2)), true);
        }

        // Regex pattern: /pattern/
        if (pattern.charAt(0) == '/' && pattern.charAt(pattern.length() - 1) == '/') {
            return new CompiledPattern(Pattern.compile(pattern.substring(1, pattern.length() - 1)), true);
        }

        return new CompiledPattern(null, false);
    }

    /**
     * Go {@code evaluateRule} karşılığı: prompt/response birleşik metnini kuralla eşleştirir.
     */
    public static boolean evaluateRule(GuardRule rule, String prompt, String response) {
        if (rule.pattern() == null || rule.pattern().isEmpty()) {
            return false;
        }

        CompiledPattern cp;
        try {
            cp = compilePattern(rule.pattern());
        } catch (PatternSyntaxException e) {
            return false;
        }

        StringBuilder text = new StringBuilder();
        if (prompt != null && !prompt.isEmpty()) {
            text.append(prompt);
        }
        if (response != null && !response.isEmpty()) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(response);
        }
        String t = text.toString();

        if (cp.regex() && cp.pattern() != null) {
            return cp.pattern().matcher(t).find();
        }

        // Düz alt dize eşleşmesi
        return t.contains(rule.pattern());
    }

    /**
     * Deterministik outbox idempotency anahtarı — Go {@code guardrailIdempotencyKey} portu.
     * Aynı (tenant, rule, prompt, response) ikilisi her zaman aynı anahtarı verir; tekrarlanan
     * evaluate çağrıları event_outbox unique index'i sayesinde tek olaya indirgenir.
     */
    public static String idempotencyKey(String tenantId, String ruleId, String prompt, String response) {
        String input = tenantId + "|" + ruleId + "|" + prompt + "|" + response;
        byte[] hash = sha256(input);
        return "guardrail:" + ruleId + ":" + HexFormat.of().formatHex(hash, 0, 12);
    }

    private static byte[] sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Derlenmiş pattern sonucu. */
    public record CompiledPattern(Pattern pattern, boolean regex) {
    }
}
