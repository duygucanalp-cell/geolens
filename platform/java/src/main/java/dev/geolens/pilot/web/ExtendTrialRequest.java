package dev.geolens.pilot.web;

/**
 * Trial süresi uzatma isteği — Go {@code ExtendTrial} input portu.
 */
public record ExtendTrialRequest(
        int extraDays) {
}
