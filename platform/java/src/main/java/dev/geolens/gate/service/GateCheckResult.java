package dev.geolens.gate.service;

import dev.geolens.gate.CheckResult;

import java.time.Instant;
import java.util.List;

/** Gate check sonucu — controller'ın HTTP gövdesine dönüştürdüğü iş mantığı çıktısı. */
public record GateCheckResult(
        String checkId,
        String entityId,
        String decision,
        int passed,
        List<CheckResult> checks,
        Instant checkedAt) {
}
