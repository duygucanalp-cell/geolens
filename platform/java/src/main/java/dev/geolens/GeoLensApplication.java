package dev.geolens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GeoLens tek giriş noktası — Go {@code cmd/api} karşılığı. Modüler monolit:
 * tüm bağlam paketleri ({@code dev.geolens.recommendation}, {@code dev.geolens.sentiment})
 * aynı ikiliden derlenen tek süreç içinde çalışır (0501 P1).
 */
@SpringBootApplication
@EnableScheduling
public class GeoLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeoLensApplication.class, args);
    }
}