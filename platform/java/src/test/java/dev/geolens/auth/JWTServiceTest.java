package dev.geolens.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go jwt_test.go davranış parity testleri. */
class JWTServiceTest {

    @Test
    void generateTokenSuccess() {
        JWTService svc = new JWTService("test-secret");
        TokenResult result = svc.generateToken("user-1", "tenant-1", "admin");
        assertFalse(result.token().isBlank());
        assertNotNull(result.expiresAt());
        assertTrue(Duration.between(Instant.now(), result.expiresAt()).toMinutes() < 3 * 60,
                "token süresi ~2 saat olmalı");
    }

    @Test
    void validateTokenValid() {
        JWTService svc = new JWTService("test-secret");
        TokenResult result = svc.generateToken("user-1", "tenant-1", "admin");

        Claims claims = svc.validateToken(result.token());
        assertEquals("user-1", claims.userId());
        assertEquals("tenant-1", claims.tenantId());
        assertEquals("admin", claims.role());
    }

    @Test
    void validateTokenInvalidSignature() {
        JWTService svc = new JWTService("test-secret");
        TokenResult result = new JWTService("other-secret")
                .generateToken("user-1", "tenant-1", "admin");

        assertThrows(AuthException.class, () -> svc.validateToken(result.token()));
    }

    @Test
    void validateTokenMalformed() {
        JWTService svc = new JWTService("test-secret");
        assertThrows(AuthException.class, () -> svc.validateToken("not-a-jwt-token"));
    }

    @Test
    void validateTokenEmpty() {
        JWTService svc = new JWTService("test-secret");
        assertThrows(AuthException.class, () -> svc.validateToken(""));
    }

    @Test
    void tokenValidatorReturnsIdentity() {
        JWTService svc = new JWTService("test-secret");
        TokenValidator validator = svc.tokenValidator(null);

        TokenResult result = svc.generateToken("user-1", "tenant-1", "editor");
        AuthIdentity identity = validator.validate(result.token());
        assertEquals("user-1", identity.userId());
        assertEquals("tenant-1", identity.tenantId());
        assertEquals("editor", identity.role());
    }

    @Test
    void tokenValidatorInvalid() {
        JWTService svc = new JWTService("test-secret");
        TokenValidator validator = svc.tokenValidator(null);
        assertThrows(AuthException.class, () -> validator.validate("invalid-token"));
    }

    @Test
    void generateTokenDifferentSecrets() {
        JWTService svc1 = new JWTService("secret-1");
        JWTService svc2 = new JWTService("secret-2");
        TokenResult result = svc1.generateToken("user-1", "tenant-1", "admin");
        assertThrows(AuthException.class, () -> svc2.validateToken(result.token()));
    }

    @Test
    void generateTokenUniqueIds() {
        JWTService svc = new JWTService("test-secret");
        TokenResult t1 = svc.generateToken("user-1", "tenant-1", "admin");
        TokenResult t2 = svc.generateToken("user-1", "tenant-1", "admin");
        assertNotEquals(t1.token(), t2.token(), "farklı JWT ID'leriyle token'lar benzersiz olmalı");
    }

    @Test
    void blacklistRejectsToken() {
        JWTService svc = new JWTService("test-secret");
        TokenBlacklist blacklist = new InMemoryBlacklist();
        TokenResult result = svc.generateToken("user-1", "tenant-1", "admin");

        TokenValidator validator = svc.tokenValidator(blacklist);
        assertNotNull(validator.validate(result.token()));

        svc.blacklistToken(result.token(), blacklist);
        assertThrows(AuthException.class, () -> validator.validate(result.token()));
    }

    @Test
    void blacklistNullSkips() {
        JWTService svc = new JWTService("test-secret");
        TokenResult result = svc.generateToken("user-1", "tenant-1", "admin");
        svc.blacklistToken(result.token(), null);
        assertNotNull(svc.tokenValidator(null).validate(result.token()));
    }

    @Test
    void expiredTokenRejected() {
        JWTService svc = new JWTService("test-secret", Duration.ofNanos(1));
        TokenResult result = svc.generateToken("user-1", "tenant-1", "admin");
        assertThrows(AuthException.class, () -> svc.validateToken(result.token()));
    }

    /** Test için basit bellek içi blacklist. */
    public static final class InMemoryBlacklist implements TokenBlacklist {
        private final java.util.Map<String, Long> store = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public boolean exists(String jti) {
            Long until = store.get(jti);
            if (until == null) {
                return false;
            }
            if (System.currentTimeMillis() > until) {
                store.remove(jti);
                return false;
            }
            return true;
        }

        @Override
        public void set(String jti, Duration ttl) {
            store.put(jti, System.currentTimeMillis() + ttl.toMillis());
        }
    }
}