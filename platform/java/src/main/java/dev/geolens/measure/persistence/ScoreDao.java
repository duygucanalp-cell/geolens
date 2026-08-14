package dev.geolens.measure.persistence;

import dev.geolens.measure.CalculationRun;
import dev.geolens.measure.Score;

/**
 * Skor kalıcılık arayüzü — Go {@code measure/service.go} içindeki {@code *db.Pool}
 * erişimlerinin karşılığı. Tenant/workspace kısıtlamaları SQL içinde zorunludur (RLS).
 */
public interface ScoreDao {

    /** Deterministik hesaplama kaydını yazar (non-fatal). Tenant çağıran bağlamdan gelir (Go ile aynı). */
    void saveCalculationRun(CalculationRun run, String tenantId);

    /** Skoru yazar (non-fatal). */
    void saveScore(Score score);

    /** ID'ye göre skor döner; kayıt yoksa {@code null}. */
    Score findById(String scoreId);
}