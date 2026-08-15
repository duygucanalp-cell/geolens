package dev.geolens.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Route eşleştirme yapılandırması — Go chi router davranışı.
 * <p>Spring 6 varsayılan olarak trailing slash'ı opsiyonel sayar ({@code /archive}
 * ile {@code /archive/} aynı kabul edilir). chi ise katıdır: {@code POST /archive}
 * (workspace arşivleme) ile {@code POST /archive/} (yanıt arşivleme) Go'da farklı
 * route'lardır. Bu ayar, trailing separator'ı opsiyonel olmaktan çıkararak Go ile
 * birebir eşleştirme sağlar.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        PathPatternParser parser = new PathPatternParser();
        // chi gibi: /archive ≠ /archive/ (opsiyonel trailing slash kapalı)
        parser.setMatchOptionalTrailingSeparator(false);
        configurer.setPatternParser(parser);
    }
}
