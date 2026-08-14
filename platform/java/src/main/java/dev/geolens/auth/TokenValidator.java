package dev.geolens.auth;

/** JWT doğrulayıcı — Go {@code httpmw.TokenValidator} karşılığı. */
@FunctionalInterface
public interface TokenValidator {
    AuthIdentity validate(String token) throws AuthException;
}
