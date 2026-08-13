package dev.geolens.recommendation;

import dev.geolens.recommendation.domain.ClaimLang;
import dev.geolens.recommendation.domain.Category;
import dev.geolens.recommendation.domain.EvidenceLabel;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.Severity;
import dev.geolens.recommendation.ng10.NG10Filter;
import dev.geolens.recommendation.ng10.Turkish;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code ng10_test.go} portu. */
class NG10ClassifierTest {

    private final NG10Filter filter = NG10Filter.withDefaults();

    @Test
    void negativeAbsolutes() {
        List<String> texts = List.of(
                "Bu kesin bir sonuçtur",
                "Kesinlikle doğru bir tespit",
                "Bu asla değişmez",
                "Her zaman böyle olur",
                "Tamamen doğru bir analiz",
                "Bütünüyle yanlış bir yaklaşım");
        for (String t : texts) {
            assertEquals(ClaimLang.N, filter.classify(t), t);
        }
    }

    @Test
    void negativeGuarantees() {
        List<String> texts = List.of(
                "Bu garanti bir sonuçtur",
                "Garanti ederiz ki skor yükselir",
                "Kesin sonuç alırsınız",
                "%100 başarı oranı",
                "Yüzde yüz memnuniyet");
        for (String t : texts) {
            assertEquals(ClaimLang.N, filter.classify(t), t);
        }
    }

    @Test
    void negativeUnsubstantiated() {
        List<String> texts = List.of(
                "En iyi çözüm budur",
                "En büyük firma",
                "Sektör lideri konumunda",
                "Bir numara strateji",
                "Number one tercih");
        for (String t : texts) {
            assertEquals(ClaimLang.N, filter.classify(t), t);
        }
    }

    @Test
    void negativeSuperlatives() {
        List<String> texts = List.of(
                "Mükemmel bir sonuç",
                "Kusursuz bir strateji",
                "Hatasız bir uygulama",
                "Benzersiz bir fırsat",
                "Eşsiz bir çözüm");
        for (String t : texts) {
            assertEquals(ClaimLang.N, filter.classify(t), t);
        }
    }

    @Test
    void neutralDataDriven() {
        List<String> texts = List.of(
                "Veri analizi yapıldı",
                "Ölçüm sonuçları değerlendirildi",
                "Skor değeri 75 olarak hesaplandı",
                "Puan ortalaması yükseldi",
                "İstatistiksel olarak anlamlı",
                "Rapor sonuçları incelendi",
                "Analiz tamamlandı");
        for (String t : texts) {
            assertEquals(ClaimLang.NG, filter.classify(t), t);
        }
    }

    @Test
    void neutralObservationAndTrend() {
        assertEquals(ClaimLang.NG, filter.classify("Bu bir tespittir"));
        assertEquals(ClaimLang.NG, filter.classify("Gözlem sonucu bildirildi"));
        assertEquals(ClaimLang.NG, filter.classify("Bulgu paylaşıldı"));
        assertEquals(ClaimLang.NG, filter.classify("Son ölçüm trende işaret ediyor"));
        assertEquals(ClaimLang.NG, filter.classify("Değişim oranı hesaplandı"));
        assertEquals(ClaimLang.NG, filter.classify("Rakiplerle karşılaştırma yapıldı"));
    }

    @Test
    void positiveActionable() {
        List<String> texts = List.of(
                "Bu bir öneridir",
                "İyileştirmeniz önerilir",
                "Tavsiyemiz optimizasyondur",
                "Bunu kolayca yapabilirsiniz",
                "Schema.org ekleyin",
                "İçeriğinizi iyileştirin");
        for (String t : texts) {
            assertEquals(ClaimLang.P, filter.classify(t), t);
        }
    }

    @Test
    void positiveConstructive() {
        List<String> texts = List.of(
                "Geliştirme fırsatı mevcut",
                "İyileştirme yapılabilir",
                "Optimizasyon önerilir",
                "Strateji belirleyin",
                "Bu bir fırsattır");
        for (String t : texts) {
            assertEquals(ClaimLang.P, filter.classify(t), t);
        }
    }

    @Test
    void positiveRecommendation() {
        List<String> texts = List.of(
                "Rakiplerinizi kontrol edin",
                "Nedenini araştırın",
                "Spesifikasyonu inceleyin",
                "Stratejiyi değerlendirin",
                "Olasılıkları düşünün");
        for (String t : texts) {
            assertEquals(ClaimLang.P, filter.classify(t), t);
        }
    }

    @Test
    void defaultsToNeutralWhenNoRuleMatches() {
        assertEquals(ClaimLang.NG, filter.classify("Bugün hava çok güzel"));
        assertEquals(ClaimLang.NG, filter.classify(""));
        assertEquals(ClaimLang.NG, filter.classify("   "));
    }

    @Test
    void isAllowedOnlyExcludesNegative() {
        assertFalse(filter.isAllowed("Bu kesin bir sonuçtur"));
        assertTrue(filter.isAllowed("Veri analizi yapıldı"));
        assertTrue(filter.isAllowed("İçeriğinizi iyileştirin"));
    }

    @Test
    void filterKeepsOnlyNeutralAndPositive() {
        Recommendation n = rec("Kesin bir sonuç", "veri analizi yapıldı");
        Recommendation ng = rec("Skor değeri düştü", "veri analizi yapıldı");
        Recommendation p = rec("İçeriğinizi iyileştirin", "yeni bir fırsat");

        List<Recommendation> kept = filter.filterRecommendations(List.of(n, ng, p));
        assertEquals(2, kept.size());
        assertTrue(kept.contains(ng));
        assertTrue(kept.contains(p));
    }

    @Test
    void filterHandlesNullAsEmptyList() {
        assertEquals(0, filter.filterRecommendations(null).size());
        assertEquals(0, filter.filterRecommendations(List.of()).size());
    }

    @Test
    void turkishToLowerCaseMatchesGo() {
        assertEquals("i", Turkish.toLowerCase("İ"));
        assertEquals("ı", Turkish.toLowerCase("I"));
        assertEquals("ş", Turkish.toLowerCase("Ş"));
        assertEquals("ç", Turkish.toLowerCase("Ç"));
        assertEquals("ü", Turkish.toLowerCase("Ü"));
        assertEquals("ö", Turkish.toLowerCase("Ö"));
        assertEquals("ğ", Turkish.toLowerCase("Ğ"));
        assertEquals("abc", Turkish.toLowerCase("ABC"));
        assertEquals("istanbul", Turkish.toLowerCase("İSTANBUL"));
        assertEquals("türkçe karakterler", Turkish.toLowerCase("TÜRKÇE KARAKTERLER"));
    }

    private static Recommendation rec(String title, String detail) {
        return new Recommendation("id", "T01", "WS01", "B01", Category.VISIBILITY, Severity.HIGH,
                EvidenceLabel.CORRELATIONAL, title, detail, null, 75, false, false, Instant.now());
    }
}