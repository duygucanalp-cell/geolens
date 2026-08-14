package dev.geolens.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT token işlemleri — Go {@code auth.JWTService} portu.
 * HS256 imzalama bağımlılıksız uygulanır (golang-jwt/v5 karşılığı): header + payload
 * base64url, HMAC-SHA256(secret, header.payload). TTL varsayılan 2 saat (D-28 kayan
 * süre); JWT_TOKEN_TTL üzerinden yapılandırılabilir.
 */
public final class JWTService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String ALG = "HS256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final byte[] secret;
    private final Duration tokenTtl;

    public JWTService(String secret) {
        this(secret, Duration.ofHours(2));
    }

    public JWTService(String secret, Duration tokenTtl) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenTtl = tokenTtl != null && !tokenTtl.isZero() && !tokenTtl.isNegative()
                ? tokenTtl
                : Duration.ofHours(2);
    }

    /** Oturum için imzalı JWT üretir — Go {@code GenerateToken} portu. */
    public TokenResult generateToken(String userId, String tenantId, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenTtl);
        String jti = UlidId.generate();

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("jti", jti);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("sub", userId);
        claims.put("user_id", userId);
        claims.put("tenant_id", tenantId);
        claims.put("role", role);

        String header = b64u(json(Map.of("alg", ALG, "typ", "JWT")));
        String payload = b64u(json(claims));
        String signingInput = header + "." + payload;
        String signature = b64u(hmac(signingInput));
        return new TokenResult(signingInput + "." + signature, expiresAt);
    }

    /** Token'ı doğrular ve claim'leri döner — Go {@code ValidateToken} portu. */
    public Claims validateToken(String tokenStr) {
        if (tokenStr == null || tokenStr.isBlank()) {
            throw new AuthException("jwt doğrulama: boş token");
        }
        String[] parts = tokenStr.split("\\.", -1);
        if (parts.length != 3) {
            throw new AuthException("jwt doğrulama: hatalı biçim");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new AuthException("jwt doğrulama: imza çözülemedi");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new AuthException("jwt doğrulama: imza uyuşmuyor");
        }
        if (!"HS256".equals(base64JsonHeaderAlg(parts[0]))) {
            throw new AuthException("beklenmeyen imzalama yöntemi");
        }

        Map<String, Object> payload;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            payload = MAPPER.readValue(decoded, Map.class);
        } catch (Exception e) {
            throw new AuthException("jwt doğrulama: payload çözülemedi", e);
        }

        long exp = number(payload.get("exp"));
        if (exp > 0 && Instant.now().getEpochSecond() >= exp) {
            throw new AuthException("jwt doğrulama: token süresi dolmuş");
        }

        return new Claims(
                str(payload.get("jti")),
                Instant.ofEpochSecond(number(payload.get("iat"))),
                exp > 0 ? Instant.ofEpochSecond(exp) : null,
                str(payload.get("sub")),
                str(payload.get("user_id")),
                str(payload.get("tenant_id")),
                str(payload.get("role")));
    }

    /**
     * Blacklist kontrollü token doğrulayıcı — Go {@code TokenValidator} portu.
     * {@code blacklist} null ise blacklist kontrolü atlanır.
     */
    public TokenValidator tokenValidator(TokenBlacklist blacklist) {
        return tokenStr -> {
            Claims claims = validateToken(tokenStr);
            if (blacklist != null && claims.id() != null && !claims.id().isBlank()
                    && blacklist.exists(BLACKLIST_PREFIX + claims.id())) {
                throw new AuthException("token iptal edilmiş");
            }
            return new AuthIdentity(claims.userId(), claims.tenantId(), claims.role());
        };
    }

    /** Token'ı kalan ömrü boyunca blacklist'e ekler — Go {@code BlacklistToken} portu. */
    public void blacklistToken(String tokenStr, TokenBlacklist blacklist) {
        if (blacklist == null) {
            return;
        }
        Claims claims = validateToken(tokenStr);
        if (claims.expiresAt() == null) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), claims.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        blacklist.set(BLACKLIST_PREFIX + claims.id(), remaining);
    }

    private String base64JsonHeaderAlg(String headerPart) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(headerPart);
            Map<String, Object> header = MAPPER.readValue(decoded, Map.class);
            return str(header.get("alg"));
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AuthException("jwt imzalama hatası", e);
        }
    }

    private static String b64u(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] json(Map<String, Object> obj) {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new AuthException("json serileştirme hatası", e);
        }
    }

    private static long number(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** JWT claim ID'leri için rastgele kimlik (Go uuid karşılığı). */
    private static final class UlidId {
        private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
        private static final SecureRandom RNG = new SecureRandom();

        private static String generate() {
            StringBuilder sb = new StringBuilder(26);
            long now = System.currentTimeMillis();
            for (int i = 9; i >= 0; i--) {
                sb.append(ALPHABET[(int) ((now >>> (i * 5)) & 0x1F)]);
            }
            for (int i = 0; i < 16; i++) {
                sb.append(ALPHABET[RNG.nextInt(32)]);
            }
            return sb.toString();
        }
    }
}
