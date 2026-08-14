package dev.geolens.gate;

/** Gate değerlendirmesindeki tek bir governance check sonucu — Go {@code gate.CheckResult} portu. */
public record CheckResult(
        String name,
        boolean passed,
        String details) {
}
