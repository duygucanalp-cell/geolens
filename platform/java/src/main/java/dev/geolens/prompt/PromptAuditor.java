package dev.geolens.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prompt kalite/güvenlik denetim motoru — Go {@code auditPrompt}/{@code containsAny} portu (R9).
 * <p>Uzunluk, hedef marka varlığı, kaynak talebi, prompt injection riski ve PII
 * taraması yapar; 0-1 skor üretir ve durum (passed/flagged/failed) belirler.
 */
public final class PromptAuditor {

    private PromptAuditor() {
    }

    /**
     * Go {@code auditPrompt} karşılığı — Türkçe sorun mesajları birebir.
     */
    public static PromptAuditResult audit(String promptText) {
        List<Map<String, Object>> issues = new ArrayList<>();
        double score = 1.0;

        // Uzunluk kontrolü
        if (promptText.length() < 10) {
            issues.add(issue("length", "low", "Prompt çok kısa — daha açıklayıcı olmalı"));
            score -= 0.1;
        }
        if (promptText.length() > 2000) {
            issues.add(issue("length", "medium", "Prompt çok uzun — token limiti aşılabilir"));
            score -= 0.15;
        }

        // Hedef marka varlığı
        if (!containsAny(promptText, List.of("marka", "brand", "şirket", "company", "firma"))) {
            issues.add(issue("clarity", "medium", "Prompt'ta hedef marka/şirket belirtilmemiş"));
            score -= 0.15;
        }

        // Kaynak/atıf talebi
        if (!containsAny(promptText, List.of("kaynak", "source", "referans", "reference", "cite", "atıf"))) {
            issues.add(issue("quality", "low", "Prompt kaynak gösterme talebi içermiyor — yanıt kalitesi düşebilir"));
            score -= 0.1;
        }

        // Prompt injection riski
        if (containsAny(promptText, List.of("ignore", "ignore all", "forget", "unset", "override", "system prompt"))) {
            issues.add(issue("injection", "high", "Prompt injection riski — sistemi atlatma girişimi tespit edildi"));
            score -= 0.4;
        }

        // PII riski (basit tarama)
        List<String> piiPatterns = List.of("@", "tc kimlik", "kimlik no", "pasaport", "telefon", "phone", "email", "adres", "address");
        if (containsAny(promptText, piiPatterns)) {
            issues.add(issue("pii", "high", "Prompt kişisel veri (PII) içerebilir — KVKK/GDPR uyumu kontrol edilmeli"));
            score -= 0.3;
        }

        if (score < 0) {
            score = 0;
        }

        String status = "passed";
        if (score < 0.5) {
            status = "failed";
        } else if (!issues.isEmpty()) {
            status = "flagged";
        }

        return new PromptAuditResult(score, issues, status);
    }

    /**
     * Go {@code containsAny} karşılığı — büyük/küçük harf duyarsız alt dize kontrolü.
     */
    public static boolean containsAny(String text, List<String> patterns) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String p : patterns) {
            if (lower.contains(p.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> issue(String type, String severity, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("severity", severity);
        m.put("message", message);
        return m;
    }
}
