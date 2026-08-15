package dev.geolens.scheduler;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * Scheduler profil keep-alive — Go {@code signal.Wait} karşılığı.
 * <p>{@code web-application-type=none} olduğundan Spring başlangıcı sonrası main thread döner ve
 * non-daemon thread kalmayınca JVM kapanır. Bu bileşen latch ile main thread'i açık tutar;
 * {@code @PreDestroy} ile kapanışta serbest bırakır (Go context cancel karşılığı).
 */
@Component
@Profile("scheduler")
public class SchedulerKeepAlive implements ApplicationRunner {

    private final CountDownLatch stop = new CountDownLatch(1);

    @Override
    public void run(ApplicationArguments args) {
        try {
            stop.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Kapanış — latch'i serbest bırakır. */
    @PreDestroy
    public void shutdown() {
        stop.countDown();
    }
}
