package dev.geolens.governance;

/** Yönetişim (usage/quota/audit) hatası — Go {@code errors.Internal} portu. */
public class GovernanceException extends RuntimeException {

    public GovernanceException(String message) {
        super(message);
    }

    public GovernanceException(String message, Throwable cause) {
        super(message, cause);
    }
}