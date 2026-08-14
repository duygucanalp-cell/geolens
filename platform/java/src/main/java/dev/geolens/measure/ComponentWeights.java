package dev.geolens.measure;

/**
 * Skor bileşenlerinin ağırlıkları — Go {@code measure.ComponentWeights} portu.
 * v2 (A3-5, 0409 v1.3): 7 bileşen — Varlık %30, Konum %20, Kaynak %15, Rakip %15,
 * Appearance %10, Sentiment %5, CompVis %5. v1 legacy: ilk 4 alan dolu, son 3 = 0.
 */
public record ComponentWeights(
        double presenceShare,
        double positionWeight,
        double sourceShare,
        double competitorContext,
        double appearanceRate,
        double sentiment,
        double compVisibility) {

    public static final ComponentWeights V2_DEFAULT =
            new ComponentWeights(0.30, 0.20, 0.15, 0.15, 0.10, 0.05, 0.05);

    /** Eski 4 bileşenli skor profili (0409 v1.0 D-89): %35/%25/%20/%20. */
    public static final ComponentWeights V1_LEGACY =
            new ComponentWeights(0.35, 0.25, 0.20, 0.20, 0, 0, 0);

    /** Tümü sıfır boş profil — Go {@code (ComponentWeights{})} karşılığı. */
    public static final ComponentWeights EMPTY =
            new ComponentWeights(0, 0, 0, 0, 0, 0, 0);

    /** Profil 7 bileşenli mi? (v2'ye özgü alanlardan herhangi biri sıfır değilse). */
    public boolean isV2() {
        return appearanceRate != 0 || sentiment != 0 || compVisibility != 0;
    }

    public boolean isEmpty() {
        return presenceShare == 0 && positionWeight == 0 && sourceShare == 0
                && competitorContext == 0 && appearanceRate == 0 && sentiment == 0 && compVisibility == 0;
    }
}