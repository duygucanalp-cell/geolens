package dev.geolens.security;

import dev.geolens.auth.JWTService;
import dev.geolens.auth.TokenBlacklist;
import org.jooq.DSLContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Güvenlik katmanı kablolaması — Go {@code httpmw} middleware zinciri karşılığı.
 * <p>Filtre {@link FilterRegistrationBean} ile kaydedilir (doğrudan {@code @Component}
 * değil) — böylece {@code @WebMvcTest} slice'larında devreye girmez ve mevcut
 * kontrolör testleri kimlik doğrulama olmadan çalışmaya devam eder.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public AuthFilter authFilter(JWTService jwtService,
                                 ObjectProvider<TokenBlacklist> blacklist,
                                 ObjectProvider<DSLContext> dsl) {
        // Blacklist kontrolü tokenValidator üzerinden (Go TokenValidator karşılığı)
        return new AuthFilter(jwtService.tokenValidator(blacklist.getIfAvailable()), dsl.getIfAvailable());
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilterRegistration(AuthFilter filter) {
        FilterRegistrationBean<AuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.setName("authFilter");
        return reg;
    }

    /** CORS — Go httpmw.CORS karşılığı (spike: tüm origin'lere izinli). */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
