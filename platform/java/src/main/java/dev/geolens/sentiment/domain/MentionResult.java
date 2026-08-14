package dev.geolens.sentiment.domain;

/** Tek bir mention'ın duygu durumu — Go {@code MentionResult} portu. */
public record MentionResult(String text, String sentiment, double score) {
}