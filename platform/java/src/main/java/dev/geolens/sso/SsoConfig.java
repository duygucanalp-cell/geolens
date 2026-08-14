package dev.geolens.sso;

/**
 * SSO/SAML yapılandırması — Go {@code SSOConfig} struct portu (K1).
 */
public record SsoConfig(
        String id,
        String tenantId,
        String idpEntityId,
        String idpSsoUrl,
        String idpCert,
        String spEntityId,
        String spAcsUrl,
        boolean enabled,
        String createdAt,
        String updatedAt) {
}
