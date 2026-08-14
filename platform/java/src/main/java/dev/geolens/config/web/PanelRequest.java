package dev.geolens.config.web;

import java.util.List;

/** Panel isteği — Go {@code config.PanelRequest} portu. */
public record PanelRequest(String name, String description, String promptSetId,
                           String scheduleCron, List<String> brandIds) {
}
