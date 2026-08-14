package dev.geolens.redteam;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Guardrail pattern eşleştirme — Go {@code matchPattern}/{@code matchAgainstRules}/{@code round2} portu.
 * <p>Pattern sözdizimi: {@code /regex/i} → büyük/küçük harf duyarsız regex,
 * {@code /regex/} → regex, diğer → alt dize eşleşmesi.
 */
public final class RedTeamMatcher {

    private RedTeamMatcher() {
    }

    /** Guardrail kuralı — Go {@code guardPattern} struct portu. */
    public record GuardPattern(String id, String name, String pattern) {
    }

    /** Payload'ın herhangi bir aktif kural pattern'iyle eşleşip eşleşmediğini kontrol eder; ilk eşleşenin adını döndürür. */
    public static MatchResult matchAgainstRules(String payload, List<GuardPattern> rules) {
        for (GuardPattern g : rules) {
            if (matchPattern(g.pattern(), payload)) {
                return new MatchResult(g.name(), true);
            }
        }
        return new MatchResult("", false);
    }

    /** Guardrail pattern sözdizimini uygular — Go {@code matchPattern} portu. */
    public static boolean matchPattern(String pattern, String text) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // Case-insensitive regex: /pattern/i
        if (pattern.length() > 2 && pattern.endsWith("/i") && pattern.startsWith("/")) {
            try {
                return Pattern.compile("(?i)" + pattern.substring(1, pattern.length() - 2)).matcher(text).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        // Regex pattern: /pattern/
        if (pattern.length() > 1 && pattern.startsWith("/") && pattern.endsWith("/")) {
            try {
                return Pattern.compile(pattern.substring(1, pattern.length() - 1)).matcher(text).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        return text != null && text.contains(pattern);
    }

    /** 2 ondalığa yuvarla — Go {@code round2} portu. */
    public static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** Eşleşme sonucu — Go çoklu dönüş (name, ok) karşılığı. */
    public record MatchResult(String ruleName, boolean matched) {
    }
}
