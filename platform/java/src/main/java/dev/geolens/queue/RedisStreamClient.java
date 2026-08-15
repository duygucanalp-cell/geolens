package dev.geolens.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream işlemleri için ince sarmalayıcı — Go {@code go-redis} karşılığı.
 * <p>Spring Data Redis üzerinden XGROUP CREATE, XREADGROUP (BLOCK), XACK, XADD, XLEN
 * işlemlerini gerçekleştirir. BUSYGROUP/NOGROUP hatalarını Go
 * {@code isGroupAlreadyExists}/{@code isNoGroupError} gibi yutar.
 * Okunan mesajlar Spring tiplerinden bağımsız {@link StreamMessage} olarak döner.
 */
@Component
public class RedisStreamClient {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamClient.class);

    private final StringRedisTemplate redis;
    private final QueueProperties props;

    public RedisStreamClient(StringRedisTemplate redis, QueueProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** XGROUP CREATE MKSTREAM — grup yoksa oluşturur; varsa BUSYGROUP yutar (Go dispatcher ile aynı). */
    public void ensureGroup(String stream, String group) {
        try {
            redis.opsForStream().createGroup(stream, ReadOffset.latest(), group);
        } catch (RedisSystemException e) {
            String msg = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
            if (msg.contains("BUSYGROUP")) {
                return; // grup zaten var — ilk çalıştırmada sorun değil
            }
            throw e;
        }
    }

    /** Tüm stream'ler için consumer group'u güvence altına alır (Go runWorker başlangıcı karşılığı). */
    public void ensureAllGroups(String group) {
        for (String s : QueueProperties.ALL_STREAMS) {
            try {
                ensureGroup(s, group);
            } catch (RuntimeException e) {
                log.warn("redis stream grubu oluşturma", "stream", s, "error", e.getMessage());
            }
        }
    }

    /**
     * XREADGROUP (BLOCK) — gruptan mesaj okur. Go {@code XReadGroup} karşılığı.
     * Mesaj yoksa boş liste döner (redis.Nil karşılığı).
     */
    public List<StreamMessage> readGroup(String stream, String group, String consumer) {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redis.opsForStream().read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(props.getBatchSize()).block(Duration.ofMillis(props.getBlockMs())),
                    StreamOffset.create(stream, ReadOffset.lastConsumed()));
        } catch (RedisSystemException e) {
            // NOGROUP: stream veya grup silinmiş olabilir — yeniden oluştur (Go isNoGroupError karşılığı)
            String msg = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
            if (msg.contains("NOGROUP")) {
                ensureAllGroups(group);
                return List.of();
            }
            throw e;
        }
        if (records == null) {
            return List.of();
        }
        List<StreamMessage> out = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            Map<String, String> values = new LinkedHashMap<>();
            record.getValue().forEach((k, v) -> values.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
            out.add(new StreamMessage(record.getStream(), record.getId().getValue(), values));
        }
        return out;
    }

    /** XACK — mesajı gruptan onaylar (Go ackMessage karşılığı). */
    public void ack(String stream, String group, String messageId) {
        try {
            redis.opsForStream().acknowledge(stream, group, messageId);
        } catch (RuntimeException e) {
            log.warn("worker: XAck hatası", "stream", stream, "error", e.getMessage());
        }
    }

    /** XADD — stream'e değer haritası ekler ve mesaj ID'sini döner (Go XAdd karşılığı). */
    public String add(String stream, Map<String, String> values) {
        MapRecord<String, String, String> record = MapRecord.create(stream, values);
        RecordId id = redis.opsForStream().add(record);
        return id == null ? "" : id.getValue();
    }

    /** XLEN — stream uzunluğu (kuyruk derinliği). */
    public Long len(String stream) {
        return redis.opsForStream().size(stream);
    }

    /**
     * Stream'den okunan tek mesaj — Spring MapRecord'dan bağımsız taşıyıcı
     * (Go {@code redis.XMessage} karşılığı).
     */
    public record StreamMessage(String stream, String id, Map<String, String> values) {
    }
}
