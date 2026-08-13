package dev.geolens.recommendation.ng10;

import dev.geolens.recommendation.domain.ClaimLang;
import dev.geolens.recommendation.domain.Recommendation;

import java.util.ArrayList;
import java.util.List;

/**
 * NG10 — İddia Dili Filtresi. Go {@code NG10Filter} portu (birebir).
 * <p>Sınıflandırma önceliği: N &gt; P &gt; NG. Hiçbir kural eşleşmezse varsayılan NG.
 */
public final class NG10Filter {

    private final List<Ng10Rule> rules;

    private static final Ng10Rule N_ABSOLUTES = new Ng10Rule("ng10-absolutes", ClaimLang.N,
            List.of("kesin", "kesinlikle", "asla", "her zaman", "tamamen", "bütünüyle"),
            List.of(), "Kesinlik ifadeleri — kanıtlanamaz iddialar");
    private static final Ng10Rule N_GUARANTEES = new Ng10Rule("ng10-guarantees", ClaimLang.N,
            List.of("garanti", "garanti eder", "kesin sonuç", "%100", "yüzde yüz"),
            List.of(), "Garanti ifadeleri — abartılı iddialar");
    private static final Ng10Rule N_UNSUBSTANTIATED = new Ng10Rule("ng10-unsubstantiated", ClaimLang.N,
            List.of("en iyi", "en büyük", "lider", "bir numara", "number one", "üstün"),
            List.of(), "Kanıtlanamaz üstünlük ifadeleri");
    private static final Ng10Rule N_SUPERLATIVES = new Ng10Rule("ng10-superlatives", ClaimLang.N,
            List.of("mükemmel", "kusursuz", "hatasız", "benzersiz", "eşsiz"),
            List.of(), "Abartılı üstünlük ifadeleri");
    private static final Ng10Rule NG_DATA_DRIVEN = new Ng10Rule("ng10-data-driven", ClaimLang.NG,
            List.of("veri", "ölçüm", "skor", "puan", "istatistik", "rapor", "analiz"),
            List.of(), "Veriye dayalı nötr ifadeler");
    private static final Ng10Rule NG_OBSERVATION = new Ng10Rule("ng10-observation", ClaimLang.NG,
            List.of("tespit", "gözlem", "bulgu", "sonuç", "değerlendirme"),
            List.of(), "Gözleme dayalı nötr ifadeler");
    private static final Ng10Rule NG_TREND = new Ng10Rule("ng10-trend", ClaimLang.NG,
            List.of("trend", "değişim", "değişiklik", "fark", "karşılaştırma"),
            List.of(), "Trend ve değişim ifadeleri");
    private static final Ng10Rule P_ACTIONABLE = new Ng10Rule("ng10-actionable", ClaimLang.P,
            List.of("öneri", "önerilir", "tavsiye", "yapabilirsiniz", "ekleyin", "iyileştirin"),
            List.of(), "Eyleme yönelik yapıcı ifadeler");
    private static final Ng10Rule P_CONSTRUCTIVE = new Ng10Rule("ng10-constructive", ClaimLang.P,
            List.of("geliştirme", "iyileştirme", "optimizasyon", "strateji", "fırsat"),
            List.of(), "Geliştirme odaklı olumlu ifadeler");
    private static final Ng10Rule P_RECOMMENDATION = new Ng10Rule("ng10-recommendation", ClaimLang.P,
            List.of("kontrol edin", "araştırın", "inceleyin", "değerlendirin", "düşünün"),
            List.of(), "Aksiyon çağrısı içeren yapıcı ifadeler");

    public NG10Filter() {
        this.rules = new ArrayList<>(List.of(
                N_ABSOLUTES, N_GUARANTEES, N_UNSUBSTANTIATED, N_SUPERLATIVES,
                NG_DATA_DRIVEN, NG_OBSERVATION, NG_TREND,
                P_ACTIONABLE, P_CONSTRUCTIVE, P_RECOMMENDATION));
    }

    public static NG10Filter withDefaults() {
        return new NG10Filter();
    }

    public List<Ng10Rule> rules() {
        return List.copyOf(rules);
    }

    public void addRule(Ng10Rule rule) {
        rules.add(rule);
    }

    /** Metni NG10 kurallarına göre sınıflandırır. Öncelik: N &gt; P &gt; NG. */
    public ClaimLang classify(String text) {
        if (text == null || text.isEmpty()) {
            return ClaimLang.NG;
        }
        String t = Turkish.toLowerCase(text);

        for (Ng10Rule rule : rules) {
            if (rule.category() == ClaimLang.N && matchKeywords(t, rule.keywords())) {
                return ClaimLang.N;
            }
        }
        for (Ng10Rule rule : rules) {
            if (rule.category() == ClaimLang.P && matchKeywords(t, rule.keywords())) {
                return ClaimLang.P;
            }
        }
        for (Ng10Rule rule : rules) {
            if (rule.category() == ClaimLang.NG && matchKeywords(t, rule.keywords())) {
                return ClaimLang.NG;
            }
        }
        return ClaimLang.NG;
    }

    public boolean isAllowed(String text) {
        return classify(text) != ClaimLang.N;
    }

    /** Sadece NG (nötr) ve P (pozitif) önerileri döndürür; null girdi → boş liste. */
    public List<Recommendation> filterRecommendations(List<Recommendation> recs) {
        if (recs == null) {
            return List.of();
        }
        if (recs.isEmpty()) {
            return recs;
        }
        return recs.stream()
                .filter(r -> classify(r.title()) != ClaimLang.N && classify(r.detail()) != ClaimLang.N)
                .toList();
    }

    private boolean matchKeywords(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (containsWord(text, kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWord(String text, String word) {
        return !word.isEmpty() && text.contains(word);
    }
}