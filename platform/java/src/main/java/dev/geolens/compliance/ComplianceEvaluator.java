package dev.geolens.compliance;

import java.util.ArrayList;
import java.util.List;

/**
 * Uyumluluk değerlendirme yardımcıları — Go {@code boolToStatus}/{@code boolToEvidence}/
 * {@code generateRecommendations} portu.
 */
public final class ComplianceEvaluator {

    private ComplianceEvaluator() {
    }

    /** Go {@code boolToStatus} karşılığı. */
    public static String boolToStatus(boolean b) {
        return b ? "passed" : "failed";
    }

    /** Go {@code boolToEvidence} karşılığı. */
    public static String boolToEvidence(boolean b, String passed, String failed) {
        return b ? passed : failed;
    }

    /** Go {@code generateRecommendations} karşılığı — başarısız kontrollerin başlık+açıklaması. */
    public static List<String> generateRecommendations(List<Control> controls) {
        List<String> recs = new ArrayList<>();
        for (Control c : controls) {
            if ("failed".equals(c.status())) {
                recs.add(c.title() + ": " + c.description());
            }
        }
        if (recs.isEmpty()) {
            recs.add("Tüm kontroller başarılı — SOC 2 Tip 1 için hazırsınız");
        }
        return recs;
    }
}
