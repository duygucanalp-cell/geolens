package dev.geolens.recommendation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Öneri kategorisi (Go: {@code Category}). */
public enum Category {
    VISIBILITY("visibility"),
    CONTENT("content"),
    TECHNICAL("technical"),
    COMPETITOR("competitor");

    private final String json;

    Category(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static Category from(String value) {
        for (Category c : values()) {
            if (c.json.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Bilinmeyen category: " + value);
    }
}