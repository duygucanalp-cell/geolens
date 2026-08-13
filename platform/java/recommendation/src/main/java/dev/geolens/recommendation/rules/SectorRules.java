package dev.geolens.recommendation.rules;

import dev.geolens.recommendation.domain.Category;
import dev.geolens.recommendation.domain.Condition;
import dev.geolens.recommendation.domain.EvidenceLabel;
import dev.geolens.recommendation.domain.Rule;
import dev.geolens.recommendation.domain.Severity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sektör bazında kural paketleri (Go {@code sectorRules} portu — birebir).
 * <p>Not: Bu kurallar yalnızca {@code GetRulesBySector} ile sunulur; değerlendirme
 * (evaluate) Go ile aynı şekilde yalnızca {@link DefaultRules} üzerinden çalışır.
 */
public final class SectorRules {

    private SectorRules() {
    }

    private static Rule rule(String id, String name, String description, Category category, Severity severity,
                             EvidenceLabel evidence, List<Condition> conditions, String title, String detail,
                             String actionUrl) {
        return new Rule(id, name, description, category, severity, evidence, conditions, title, detail, actionUrl,
                true, null);
    }

    public static final Map<String, List<Rule>> RULES;

    static {
        Map<String, List<Rule>> m = new LinkedHashMap<>();
        m.put("e-ticaret", List.of(
                rule("rule-ecom-product-visibility", "Ürün Görünürlüğü",
                        "E-ticaret sitelerinde ürün sayfalarının AI görünürlüğü",
                        Category.VISIBILITY, Severity.HIGH, EvidenceLabel.CORRELATIONAL,
                        List.of(new Condition("score.value", "lt", 60.0)),
                        "Ürün sayfalarınız AI motorlarında düşük görünürlüğe sahip",
                        "E-ticaret sitenizin ürün sayfaları AI motorları tarafından yeterince taranmıyor. Yapılandırılmış veri (Product schema) ekleyerek ürünlerinizin AI yanıtlarında yer almasını sağlayabilirsiniz.",
                        "/audit"),
                rule("rule-ecom-review-data", "Müşteri Yorumu Eksikliği",
                        "AI motorları müşteri yorumlarını kaynak olarak kullanır",
                        Category.CONTENT, Severity.MEDIUM, EvidenceLabel.EXPERIMENTAL,
                        List.of(new Condition("score.value", "lt", 70.0)),
                        "Müşteri yorumlarınız AI kaynaklarında yer almıyor",
                        "Yapılandırılmış yorum verisi (Review schema) ekleyerek müşteri deneyimlerinizin AI yanıtlarına kaynak olmasını sağlayabilirsiniz.",
                        null)
        ));
        m.put("saglik", List.of(
                rule("rule-health-authority", "Otorite Sinyali Eksik",
                        "Sağlık sektöründe otorite sinyalleri kritiktir",
                        Category.CONTENT, Severity.HIGH, EvidenceLabel.CORRELATIONAL,
                        List.of(new Condition("score.value", "lt", 65.0)),
                        "Tıbbi otorite sinyalleriniz zayıf",
                        "Sağlık sektöründe AI motorları, akademik atıflar ve resmi sağlık kuruluşu bağlantılarına öncelik verir. Sektörel otorite bağlantılarınızı artırmanız önerilir.",
                        null)
        ));
        m.put("finans", List.of(
                rule("rule-finance-trust", "Güven Sinyali Eksik",
                        "Finans sektöründe güven sinyalleri (SSL, lisans, güvenlik) önemlidir",
                        Category.TECHNICAL, Severity.HIGH, EvidenceLabel.CORRELATIONAL,
                        List.of(new Condition("score.value", "lt", 70.0)),
                        "Güven sinyalleriniz AI motorları için yetersiz",
                        "Finans sektöründe faaliyet gösteren siteler için SSL sertifikası, lisans bilgileri ve güvenlik sertifikaları AI güvenilirlik değerlendirmesinde kritik rol oynar.",
                        "/audit")
        ));
        RULES = m;
    }
}