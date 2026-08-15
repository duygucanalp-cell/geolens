package dev.geolens.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Redis Stream kuyruk yapılandırması — Go {@code queue} + {@code config.LoadFromEnv} karşılığı. */
@Component
@ConfigurationProperties(prefix = "queue")
public class QueueProperties {

    /** Stream sabitleri — Go {@code queue.Stream*} karşılığı. */
    public static final String STREAM_MEASURE = "q:measure";
    public static final String STREAM_AUDIT = "q:audit";
    public static final String STREAM_REPORT = "q:report";
    public static final String STREAM_NOTIFY = "q:notify";
    public static final String STREAM_DEAD = "q:dead";
    public static final String STREAM_SENTIMENT = "q:sentiment";
    public static final String STREAM_REPLAY = "q:replay";
    public static final String STREAM_ARCHIVE = "q:archive";
    public static final String STREAM_GAP = "q:gap";
    public static final String STREAM_TECHNICAL_GEO = "q:technical-geo";
    public static final String STREAM_CONTENT_GEO = "q:content-geo";
    public static final String STREAM_GOVERNANCE = "q:governance";

    /** Worker'ın grup oluşturduğu tüm stream'ler (Go runWorker + dispatcher listesi). */
    public static final String[] ALL_STREAMS = {
            STREAM_MEASURE, STREAM_AUDIT, STREAM_REPORT, STREAM_NOTIFY, STREAM_DEAD,
            STREAM_SENTIMENT, STREAM_REPLAY, STREAM_ARCHIVE, STREAM_GAP,
            STREAM_TECHNICAL_GEO, STREAM_CONTENT_GEO, STREAM_GOVERNANCE
    };

    /** Outbox dispatcher tarama aralığı (ms) — Go POLL_INTERVAL (varsayılan 30s). */
    private long pollMs = 30_000;
    /** XREADGROUP BLOCK süresi (ms). */
    private long blockMs = 5_000;
    /** Her okumada alınacak maksimum mesaj sayısı. */
    private int batchSize = 10;
    /** Redis Stream consumer group adı — Go CONSUMER_GROUP (varsayılan cg:measure). */
    private String consumerGroup = "cg:measure";
    /** Consumer adı — Go worker consumerName. */
    private String consumerName = "worker-1";
    /** Scheduler panel cron tarama aralığı (ms). */
    private long panelScanMs = 60_000;
    /** Outbox dispatcher açık/kapalı. */
    private boolean dispatcherEnabled = true;
    /** Worker tüketicileri açık/kapalı. */
    private boolean workersEnabled = true;

    public long getPollMs() {
        return pollMs;
    }

    public void setPollMs(long pollMs) {
        this.pollMs = pollMs;
    }

    public long getBlockMs() {
        return blockMs;
    }

    public void setBlockMs(long blockMs) {
        this.blockMs = blockMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public long getPanelScanMs() {
        return panelScanMs;
    }

    public void setPanelScanMs(long panelScanMs) {
        this.panelScanMs = panelScanMs;
    }

    public boolean isDispatcherEnabled() {
        return dispatcherEnabled;
    }

    public void setDispatcherEnabled(boolean dispatcherEnabled) {
        this.dispatcherEnabled = dispatcherEnabled;
    }

    public boolean isWorkersEnabled() {
        return workersEnabled;
    }

    public void setWorkersEnabled(boolean workersEnabled) {
        this.workersEnabled = workersEnabled;
    }
}
