package dev.geolens.recommendation.domain;

/** NG10 iddia dili kategorisi (Go: {@code ClaimLang}). */
public enum ClaimLang {
    /** İddialı / kanıtlanamaz — filtrelenir. */
    N,
    /** Nötr: veriye dayalı, ölçülebilir — gösterilir. */
    NG,
    /** Positive: eyleme yönelik, yapıcı — gösterilir. */
    P
}