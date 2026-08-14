package dev.geolens.billing;

/** Fatura tipi — Go {@code billing.InvoiceType} portu (FR-A6). */
public enum InvoiceType {
    EFATURA("efatura"),
    EARSIV("earsiv");

    private final String value;

    InvoiceType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Bilinmeyen değerde {@code null} döner (Go tip dönüşümü davranışı). */
    public static InvoiceType from(String value) {
        for (InvoiceType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
