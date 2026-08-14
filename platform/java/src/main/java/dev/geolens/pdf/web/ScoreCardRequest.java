package dev.geolens.pdf.web;

/** Skor kartı / denetim raporu isteği — Go {@code pdf} handler req portu. */
public record ScoreCardRequest(String brandId, String brandName) {
}
