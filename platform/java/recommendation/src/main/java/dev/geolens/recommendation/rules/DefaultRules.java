package dev.geolens.recommendation.rules;

import dev.geolens.recommendation.domain.Category;
import dev.geolens.recommendation.domain.Condition;
import dev.geolens.recommendation.domain.EvidenceLabel;
import dev.geolens.recommendation.domain.Rule;
import dev.geolens.recommendation.domain.Severity;

import java.util.List;

/** Varsayılan öneri kural seti (Go {@code defaultRules} portu — birebir). */
public final class DefaultRules {

    private DefaultRules() {
    }

    private static Rule rule(String id, String name, String description, Category category, Severity severity,
                             EvidenceLabel evidence, List<Condition> conditions, String title, String detail,
                             String actionUrl) {
        return new Rule(id, name, description, category, severity, evidence, conditions, title, detail, actionUrl,
                true, null);
    }

    public static final List<Rule> RULES = List.of(
            rule("rule-score-drop", "Skor Düşüşü Tespiti", "Görünürlük skoru 10 puandan fazla düştüğünde uyar",
                    Category.VISIBILITY, Severity.HIGH, EvidenceLabel.CORRELATIONAL,
                    List.of(new Condition("score.drop", "gt", 10.0)),
                    "Görünürlük skorunuz düşüyor",
                    "Markanızın AI görünürlük skoru son ölçümde önemli ölçüde düştü. Rakiplerinizin AI motorlarındaki görünürlüğünü kontrol edin.",
                    null),

            rule("rule-trend-decline", "Trend Gerilemesi", "Son iki ölçüm arasında sürekli düşüş varsa uyar",
                    Category.VISIBILITY, Severity.MEDIUM, EvidenceLabel.CORRELATIONAL,
                    List.of(new Condition("score.trend", "eq", "declining")),
                    "Görünürlük trendiniz geriliyor",
                    "Markanızın AI görünürlük skoru son iki ölçümde de düşüş gösteriyor. Bu, rakiplerinizin sizi geçtiği anlamına gelebilir.",
                    null),

            rule("rule-engine-gap", "Motor Bazında Performans Farkı", "Bir motorda düşük, diğerinde yüksek skor varsa uyar",
                    Category.VISIBILITY, Severity.MEDIUM, EvidenceLabel.EXPERIMENTAL,
                    List.of(new Condition("score.engine_gap", "gt", 30.0)),
                    "Motorlar arasında büyük performans farkı var",
                    "Markanız bazı AI motorlarında yüksek görünürlüğe sahipken bazılarında düşük. Farkın nedenini araştırmanız önerilir.",
                    null),

            rule("rule-engine-citation-gap", "Citation Eksikliği", "Engine breakdown'da tek motor baskınsa uyar",
                    Category.CONTENT, Severity.LOW, EvidenceLabel.EXPERIMENTAL,
                    List.of(new Condition("score.engine_gap", "lt", 5.0)),
                    "Motor çeşitliliği düşük",
                    "Markanız yalnızca bir AI motorunda görünür durumda. Diğer motorlarda da görünürlük kazanmak için içerik stratejinizi çeşitlendirin.",
                    null),

            rule("rule-competitor-gain", "Rakip Yükselişi", "Skor düşüş trendi varsa ve önceki skor varsa uyar",
                    Category.COMPETITOR, Severity.HIGH, EvidenceLabel.CORRELATIONAL,
                    List.of(new Condition("score.trend", "eq", "declining")),
                    "Rakibiniz öne geçiyor olabilir",
                    "Markanızın AI görünürlük skoru düşüş trendinde. Rakiplerinizin AI stratejisini analiz etmeniz önerilir.",
                    null),

            rule("rule-robots-blocked", "robots.txt AI Engel Tespiti", "robots.txt AI botlarını engelliyorsa uyar",
                    Category.TECHNICAL, Severity.CRITICAL, EvidenceLabel.TESTABLE,
                    List.of(new Condition("audit.robots_txt.disallowed_all", "eq", true)),
                    "AI botları robots.txt tarafından engelleniyor",
                    "Sitenizin robots.txt dosyası AI botlarının sitenizi taramasını engelliyor. Bu, AI görünürlük ölçümlerinizi doğrudan etkiler.",
                    "/audit"),

            rule("rule-no-structured-data", "Yapılandırılmış Veri Eksik", "Sitede JSON-LD veya Schema.org yoksa öner",
                    Category.CONTENT, Severity.MEDIUM, EvidenceLabel.TESTABLE,
                    List.of(new Condition("audit.ssr.has_structured_data", "eq", false)),
                    "Yapılandırılmış veri ekleyin",
                    "Sitenizde JSON-LD veya Schema.org yapılandırılmış verisi bulunamadı. AI motorları içeriğinizi daha iyi anlamak için yapılandırılmış veri kullanır.",
                    "/audit"),

            rule("rule-bot-inaccessible", "AI Bot Erişim Engeli", "AI botları sitenize erişemiyorsa uyar",
                    Category.TECHNICAL, Severity.CRITICAL, EvidenceLabel.TESTABLE,
                    List.of(new Condition("audit.bot_access.accessible", "eq", false)),
                    "AI botları sitenize erişemiyor",
                    "AI botları sitenize erişim sağlayamıyor. Sunucu yapılandırmanızı ve güvenlik duvarı ayarlarınızı kontrol edin.",
                    "/audit")
    );
}