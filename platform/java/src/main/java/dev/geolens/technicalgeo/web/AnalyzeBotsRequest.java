package dev.geolens.technicalgeo.web;

/**
 * Bot erişim analiz isteği — Go {@code AnalyzeBots} input portu.
 */
public record AnalyzeBotsRequest(
        String brandId,
        String url) {
}
