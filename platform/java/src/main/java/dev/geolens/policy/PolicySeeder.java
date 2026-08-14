package dev.geolens.policy;

import java.util.List;

/**
 * Varsayılan policy pack ve control tanımları — Go {@code frameworkControls} +
 * {@code SeedDefaultPacks} tanım portu (R4). EU AI Act, NIST AI RMF, KVKK, ISO 42001.
 */
public final class PolicySeeder {

    /** Varsayılan framework pack'leri. */
    public record Framework(String name, String framework, String description) {
    }

    private PolicySeeder() {
    }

    public static List<Framework> defaultFrameworks() {
        return List.of(
                new Framework("EU AI Act Compliance", "eu_ai_act", "Avrupa Birliği Yapay Zeka Yasası uyum paketi"),
                new Framework("NIST AI RMF", "nist_ai_rmf", "NIST AI Risk Management Framework uyum paketi"),
                new Framework("KVKK Uyum Paketi", "kvkk", "Kişisel Verilerin Korunması Kanunu uyum paketi"),
                new Framework("ISO 42001 AI Management", "iso_42001", "ISO 42001 Yapay Zeka Yönetim Sistemi uyum paketi"));
    }

    /** Go {@code frameworkControls} karşılığı — framework'e göre kontrol tanımları. */
    public static List<ControlDef> frameworkControls(String framework) {
        return switch (framework) {
            case "eu_ai_act" -> List.of(
                    new ControlDef("Art.9", "Risk Yönetim Sistemi", "Sürekli, yinelemeli risk yönetim süreci", "Risk Management"),
                    new ControlDef("Art.10", "Eğitim Verisi Yönetimi", "Eğitim verisi kalitesi, bias analizi, temsiliyet", "Data Governance"),
                    new ControlDef("Art.11", "Teknik Dokümantasyon", "Model mimarisi, eğitim yöntemi, performans metrikleri", "Documentation"),
                    new ControlDef("Art.12", "Kayıt Tutma", "Olay günlükleri, otomatik loglama, saklama süresi", "Monitoring"),
                    new ControlDef("Art.13", "Şeffaflık ve Bilgilendirme", "Kullanıcılara AI sistemi bildirimi, açıklanabilirlik", "Transparency"),
                    new ControlDef("Art.14", "İnsan Gözetimi", "İnsan müdahale mekanizmaları, override yetkisi", "Oversight"),
                    new ControlDef("Art.15", "Doğruluk ve Dayanıklılık", "Doğruluk metrikleri, hata toleransı, güvenilirlik", "Performance"));
            case "nist_ai_rmf" -> List.of(
                    new ControlDef("GOV-1", "Yönetişim Yapısı", "AI risk yönetimi için organizasyonel yapı", "Govern"),
                    new ControlDef("GOV-2", "Politika ve Prosedürler", "AI kullanım politikaları, etik kurallar", "Govern"),
                    new ControlDef("MAP-1", "AI Sistemi Envanteri", "Tüm AI sistemlerinin tanımlanması ve sınıflandırılması", "Map"),
                    new ControlDef("MAP-2", "Risk Değerlendirmesi", "AI sistemlerinin risk seviyesinin belirlenmesi", "Map"),
                    new ControlDef("MEA-1", "Performans İzleme", "Sürekli model performans ve drift izleme", "Measure"),
                    new ControlDef("MEA-2", "Bias ve Adillik", "Demoğrafik parite, eşitlik metrikleri", "Measure"),
                    new ControlDef("MAN-1", "Risk Azaltma", "Tesbit edilen risklerin azaltılması ve yönetimi", "Manage"));
            case "kvkk" -> List.of(
                    new ControlDef("KVKK-4", "Açık Rıza", "Veri sahibinin açık rızasının alınması", "Consent"),
                    new ControlDef("KVKK-5", "Veri Envanteri", "Kişisel veri işleme envanteri", "Data Inventory"),
                    new ControlDef("KVKK-6", "Aydınlatma Yükümlülüğü", "Veri sahibinin bilgilendirilmesi", "Transparency"),
                    new ControlDef("KVKK-7", "Veri Güvenliği", "Teknik ve idari tedbirler, şifreleme", "Security"),
                    new ControlDef("KVKK-8", "Silme ve Yok Etme", "Veri saklama süreleri, periyodik imha", "Retention"),
                    new ControlDef("KVKK-9", "Veri Sorumlusu Kayıt", "VERBIS kaydı ve güncellemesi", "Compliance"));
            case "iso_42001" -> List.of(
                    new ControlDef("6.1", "Risk Değerlendirmesi", "AI risk değerlendirme ve tedavi planı", "Planning"),
                    new ControlDef("7.2", "Yetkinlik", "AI personel yetkinlik ve eğitim gereksinimleri", "Support"),
                    new ControlDef("7.4", "İletişim", "AI sistemi kullanımı hakkında paydaş iletişimi", "Support"),
                    new ControlDef("8.1", "Operasyonel Planlama", "AI sistemi geliştirme ve işletme kontrolleri", "Operation"),
                    new ControlDef("8.2", "AI Sistem Değerlendirmesi", "AI sistem etki değerlendirmesi", "Operation"),
                    new ControlDef("9.1", "Performans İzleme", "AI sistemi performans ve uygunluk izleme", "Evaluation"));
            default -> List.of(new ControlDef("C001", "Özel Kontrol 1", "Tanımlanmış özel kontrol", "Custom"));
        };
    }
}
