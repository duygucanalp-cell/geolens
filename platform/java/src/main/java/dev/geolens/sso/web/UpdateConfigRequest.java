package dev.geolens.sso.web;

/**
 * SSO yapılandırma güncelleme isteği — Go {@code UpdateConfig} input portu.
 */
public record UpdateConfigRequest(
        String idpEntityId,
        String idpSsoUrl,
        String idpCert,
        String spEntityId,
        String spAcsUrl,
        boolean enabled) {
}
