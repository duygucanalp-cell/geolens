package dev.geolens.pdf;

/** PDF rapor tipi — Go {@code pdf.ReportType} portu. */
public enum ReportType {
    SCORE_CARD("score_card"),
    WEEKLY_DIGEST("weekly_digest"),
    AUDIT("audit");

    private final String value;

    ReportType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ReportType fromValue(String value) {
        for (ReportType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("bilinmeyen rapor tipi: " + value);
    }
}
