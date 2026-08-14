package dev.geolens.gate;

/** Gate yardımcı metin fonksiyonları — Go {@code aktifPackStr}/{@code guardrailSayisi}/{@code controlPct} portu. */
public final class GateText {

    private GateText() {
    }

    public static String packCount(int n) {
        if (n == 1) {
            return "1 pack";
        }
        return n + " pack";
    }

    public static String guardrailCount(int n) {
        if (n == 1) {
            return "1 guardrail";
        }
        return n + " guardrail";
    }

    public static String controlPct(int passed, int total) {
        if (total == 0) {
            return "%0 geçti";
        }
        return "%" + (passed * 100 / total) + " geçti";
    }
}
