package dev.geolens.measure.persistence;

import dev.geolens.measure.CalculationRun;
import dev.geolens.measure.Score;

/**
 * DB'siz çalışan noop implementasyon — Go'da {@code svc.pool == nil} durumunun karşılığı.
 * Skor hesaplanır ama DB'ye yazılmaz; okuma kayıt yokmuş gibi davranır.
 */
public final class NoopScoreDao implements ScoreDao {

    @Override
    public void saveCalculationRun(CalculationRun run, String tenantId) {
        // DB bağlantısı yok — atla.
    }

    @Override
    public void saveScore(Score score) {
        // DB bağlantısı yok — atla.
    }

    @Override
    public Score findById(String scoreId) {
        return null;
    }
}