package dev.geolens.audit;

import java.util.List;

/** robots.txt'in AI botlarına izin verip vermediği — Go {@code audit.RobotsTxtCheck} portu. */
public record RobotsTxtCheck(boolean exists, boolean allowsAIBots, List<String> blockedPaths, boolean disallowedAll) {

    public RobotsTxtCheck {
        if (blockedPaths == null) {
            blockedPaths = List.of();
        }
    }
}