package dev.geolens.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Benchmark sektör istatistiği toplayıcı — Go {@code benchmark.Collector} portu (FR-D5).
 * <p>Periyodik toplulaştırmayı {@code BenchmarkAggregator} üzerinden çalıştırır.
 * Go'da {@code go collector.Run(ctx)} ile worker'da başlatılır; spike'ta {@code @Scheduled}
 * karşılığıdır. Varsayılan kapalı; {@code benchmark.collector.enabled=true} ile açılır.
 */
@Component
public class BenchmarkCollector {

    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkCollector.class);

    private final BenchmarkAggregator aggregator;
    private final boolean enabled;

    public BenchmarkCollector(BenchmarkAggregator aggregator,
                              @Value("${benchmark.collector.enabled:false}") boolean enabled) {
        this.aggregator = aggregator;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${benchmark.collector.interval-ms:3600000}",
               initialDelayString = "${benchmark.collector.initial-delay-ms:60000}")
    public void run() {
        if (!enabled) {
            LOG.debug("benchmark.collector.enabled=false; toplulaştırma çalıştırılmadı");
            return;
        }
        String id = aggregator.aggregate();
        if (id != null && !id.isEmpty()) {
            LOG.debug("benchmark toplulaştırma tamam stats_id={}", id);
        }
    }
}
