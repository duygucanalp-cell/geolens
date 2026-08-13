package dev.geolens.recommendation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Öneri önem düzeyi (Go: {@code Severity}). */
public enum Severity {
    CRITICAL("critical"),
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String json;

    Severity(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static Severity from(String value) {
        for (Severity s : values()) {
            if (s.json.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Bilinmeyen severity: " + value);
    }
}