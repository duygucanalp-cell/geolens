package dev.geolens.engine;

/** AI motoru erişim kademesi — Go {@code engine.Tier} portu. */
public enum Tier {

    DIRECT(1),
    OFFICIAL_PROXY(2),
    DIRECTIONAL(3);

    private final int code;

    Tier(int code) {
        this.code = code;
    }

    /** Sayısal kademe kodu (1=direct, 2=official proxy, 3=directional). */
    public int code() {
        return code;
    }

    public static Tier fromCode(int code) {
        return switch (code) {
            case 1 -> DIRECT;
            case 2 -> OFFICIAL_PROXY;
            case 3 -> DIRECTIONAL;
            default -> throw new IllegalArgumentException("bilinmeyen kademe: " + code);
        };
    }
}