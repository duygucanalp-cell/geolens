package dev.geolens.optimize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optimizasyon öneri motoru — Go {@code optimize} paketi portu (R13, A3-4/İP-07).
 * <p>OpportunityScore formülü: {@code Impact × Urgency × Confidence} (0-100).
 * Impact 1-10, Urgency 1-10, Confidence 0-1.
 */
public final class OpportunityAnalyzer {

    private OpportunityAnalyzer() {
    }

    /**
     * Go {@code ImpactInt} karşılığı: impact string'ini 1-10 ölçeğine eşler.
     */
    public static int impactInt(String impact) {
        return switch (impact == null ? "" : impact) {
            case "critical" -> 10;
            case "high" -> 9;
            case "medium" -> 6;
            case "low" -> 3;
            default -> 5;
        };
    }

    /**
     * Go {@code UrgencyFromEffort} karşılığı: effort string'inden 1-10 aciliyet türetir.
     */
    public static int urgencyFromEffort(String effort) {
        return switch (effort == null ? "" : effort) {
            case "high" -> 9;
            case "medium" -> 7;
            case "low" -> 4;
            default -> 5;
        };
    }

    /**
     * Go {@code OpportunityScore} karşılığı: normalleştirilmiş fırsat skoru (0-100).
     */
    public static double opportunityScore(int impact, int urgency, double confidence) {
        double score = (double) impact * urgency * confidence;
        return Math.round(score * 100) / 100.0;
    }

    /**
     * Go {@code analyze} karşılığı: skor sayısına göre öneri listesi üretir.
     * <p>scoreCount &lt; 5 ise ölçüm sıklığı önerisi de dahil edilir (4 öneri),
     * aksi halde 3 öneri.
     */
    public static List<Map<String, Object>> analyze(int scoreCount) {
        List<Map<String, Object>> recs = new ArrayList<>();

        // Öneri 1: Daha sık ölçüm
        if (scoreCount < 5) {
            recs.add(rec("measurement", "Ölçüm sıklığını artırın",
                    "Daha sık ölçüm, trend verisi ve erken uyarı sağlar. Haftada en az 1 ölçüm önerilir.",
                    "high", "low", 0.8));
        }

        // Öneri 2: Çoklu engine kullanımı
        recs.add(rec("engine", "Çoklu AI motoru kullanın",
                "Farklı motorlardan (Perplexity, ChatGPT, Gemini) veri almak görünürlük skorunun güvenilirliğini artırır.",
                "high", "medium", 0.85));

        // Öneri 3: Prompt optimizasyonu
        recs.add(rec("prompt", "Prompt'ları optimize edin",
                "Marka adı, sektör ve kaynak talebi içeren prompt'lar daha doğru sonuçlar üretir.",
                "medium", "medium", 0.7));

        // Öneri 4: Kaynak çeşitliliği
        recs.add(rec("citation", "Kaynak çeşitliliğini artırın",
                "Web sitesi, sosyal medya ve haber kaynaklarında marka varlığınızı güçlendirin.",
                "medium", "high", 0.75));

        return recs;
    }

    private static Map<String, Object> rec(String category, String title, String description,
                                           String impact, String effort, double confidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("title", title);
        m.put("description", description);
        m.put("impact", impact);
        m.put("effort", effort);
        m.put("score_potential", opportunityScore(impactInt(impact), urgencyFromEffort(effort), confidence));
        return m;
    }
}
