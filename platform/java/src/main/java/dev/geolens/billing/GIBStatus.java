package dev.geolens.billing;

import com.fasterxml.jackson.annotation.JsonValue;

/** GİB (Gelir İdaresi Başkanlığı) entegrasyon durumu — Go {@code billing.GIBStatus} portu. */
public enum GIBStatus {
    NONE("none"),
    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected");

    private final String value;

    GIBStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Bilinmeyen değerde {@code null} döner. */
    public static GIBStatus from(String value) {
        for (GIBStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
