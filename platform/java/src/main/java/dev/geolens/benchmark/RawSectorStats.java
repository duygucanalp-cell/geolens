package dev.geolens.benchmark;

/** DP uygulanmadan önceki ham sektör istatistikleri — Go {@code RawSectorStats} portu. */
public record RawSectorStats(
        double myScore,
        double sectorAvg,
        double sectorMedian,
        double sectorMin,
        double sectorMax,
        double sectorStdDev,
        double percentile25,
        double percentile75,
        double percentile90,
        int tenantCount) {
}
