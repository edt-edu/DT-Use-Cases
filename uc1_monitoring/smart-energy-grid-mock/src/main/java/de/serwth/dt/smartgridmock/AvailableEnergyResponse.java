package de.serwth.dt.smartgridmock;

import java.time.LocalDateTime;

public record AvailableEnergyResponse(
    double availableEnergy,
    String unit,
    LocalDateTime simulatedTime,
    double baselineEnergy,
    double solarEnergy,
    double noise,
    double speedMultiplier) {
}
