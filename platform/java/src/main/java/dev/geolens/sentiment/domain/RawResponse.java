package dev.geolens.sentiment.domain;

/** Ham AI yanıtı — Go {@code rawResp} portu. */
public record RawResponse(String id, String engineName, String content, java.time.Instant createdAt) {
}