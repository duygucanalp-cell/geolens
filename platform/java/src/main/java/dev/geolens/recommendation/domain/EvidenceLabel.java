package dev.geolens.recommendation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Öneri arkasındaki kanıt türü (Go: {@code EvidenceLabel}). */
public enum EvidenceLabel {
    EXPERIMENTAL("deneysel"),
    CORRELATIONAL("korelasyonel"),
    TESTABLE("denenebilir");

    private final String json;

    EvidenceLabel(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static EvidenceLabel from(String value) {
        for (EvidenceLabel e : values()) {
            if (e.json.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Bilinmeyen evidence: " + value);
    }
}