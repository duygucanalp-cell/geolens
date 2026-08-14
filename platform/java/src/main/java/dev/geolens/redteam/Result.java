package dev.geolens.redteam;

/** Red team test sonucu — Go {@code redteam.Result} struct portu. */
public record Result(
        String id,
        String runId,
        String caseId,
        String category,
        String payload,
        String outcome,
        String riskLevel,
        String matchedRule,
        String detail) {
}
