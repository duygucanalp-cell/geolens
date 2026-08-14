package dev.geolens.benchmark;

/**
 * Differansiyel gizlilik korumalı sektör benchmark istatistikleri — Go {@code AggregatedSectorStats} portu.
 * <p>{@code myScore} gürültüsüz (kiracının kendi verisi); diğer tüm istatistikler DP-noise'lı.
 */
public class AggregatedSectorStats {

    public double myScore;
    public double sectorAvg;
    public double sectorMedian;
    public double sectorMin;
    public double sectorMax;
    public double sectorStdDev;
    public double percentile25;
    public double percentile75;
    public double percentile90;
    public double difference;
    public String trend = "";
    public boolean sufficientData;
    public int tenantCount;

    public static AggregatedSectorStats empty(double myScore, int tenantCount) {
        AggregatedSectorStats s = new AggregatedSectorStats();
        s.myScore = myScore;
        s.tenantCount = tenantCount;
        s.sufficientData = false;
        return s;
    }

    public static AggregatedSectorStats of(RawSectorStats raw, DpConfig config) {
        if (raw.tenantCount() < config.minTenants) {
            return empty(raw.myScore(), raw.tenantCount());
        }

        AggregatedSectorStats stats = new AggregatedSectorStats();
        stats.myScore = raw.myScore();
        stats.sectorAvg = Privacy.addLaplaceNoise(raw.sectorAvg(), config);
        stats.sectorMedian = Privacy.addLaplaceNoise(raw.sectorMedian(), config);
        stats.sectorMin = Privacy.addLaplaceNoise(raw.sectorMin(), config);
        stats.sectorMax = Privacy.addLaplaceNoise(raw.sectorMax(), config);
        stats.sectorStdDev = Privacy.addLaplaceNoise(raw.sectorStdDev(), config);
        stats.percentile25 = Privacy.addLaplaceNoise(raw.percentile25(), config);
        stats.percentile75 = Privacy.addLaplaceNoise(raw.percentile75(), config);
        stats.percentile90 = Privacy.addLaplaceNoise(raw.percentile90(), config);
        stats.sufficientData = true;
        stats.tenantCount = raw.tenantCount();

        // Difference anonimleştirilmiş değerlerden hesaplanır
        stats.difference = stats.myScore - stats.sectorAvg;

        // DP sonrası farka göre trend
        if (stats.difference > 5.0) {
            stats.trend = "up";
        } else if (stats.difference < -5.0) {
            stats.trend = "down";
        } else {
            stats.trend = "stable";
        }
        return stats;
    }
}
