package dev.geolens.benchmark;

/**
 * Differansiyel gizlilik yapılandırması — Go {@code benchmark.DPConfig} portu (FR-D5, NFR-13).
 * <p>Laplace mekanizması: düşük {@code epsilon} = güçlü gizlilik, daha fazla gürültü.
 */
public class DpConfig {

    /** Gizlilik bütçesi — Go varsayılanı 1.0 (0.1 yüksek gizlilik — 2.0 düşük). */
    public double epsilon = 1.0;

    /** Tek bir kiracının verisinin yol açabileceği maksimum değişim — skor aralığı [0,100] → 100. */
    public double sensitivity = 100.0;

    /** Gürültü sonrası izin verilen değer aralığı. */
    public double clampMin = 0.0;
    public double clampMax = 100.0;

    /** Veri yayınlanmadan önce gereken minimum kiracı sayısı (NFR-13: ≥5). */
    public int minTenants = 5;

    public static DpConfig defaults() {
        return new DpConfig();
    }

    /**
     * Kısmi yapılandırma varsayılanlarla birleştirilir — yalnızca sıfır olmayan alanlar
     * üzerine yazılır (Go {@code NewAggregator} merge davranışı; Epsilon/Clamp'ı sıfırlamaz).
     */
    public static DpConfig merge(DpConfig overrides) {
        DpConfig cfg = defaults();
        if (overrides != null) {
            if (overrides.epsilon != 0) {
                cfg.epsilon = overrides.epsilon;
            }
            if (overrides.sensitivity != 0) {
                cfg.sensitivity = overrides.sensitivity;
            }
            if (overrides.clampMin != 0 || overrides.clampMax != 0) {
                cfg.clampMin = overrides.clampMin;
                cfg.clampMax = overrides.clampMax;
            }
            if (overrides.minTenants != 0) {
                cfg.minTenants = overrides.minTenants;
            }
        }
        return cfg;
    }
}
