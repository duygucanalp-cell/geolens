package dev.geolens.recommendation.persistence;

import java.time.Instant;

/** Belirli bir anda ölçülmüş skor — Go {@code scoreAtTime} karşılığı. */
public record ScoreAt(double value, String fidelity, Instant measuredAt) {
}