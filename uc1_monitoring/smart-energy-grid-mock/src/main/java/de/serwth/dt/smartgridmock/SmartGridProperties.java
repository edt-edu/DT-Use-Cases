package de.serwth.dt.smartgridmock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "smart-grid")
public record SmartGridProperties(
    @DecimalMin("0.0") double baselineWatts,
    @DecimalMin("0.0") double solarPeakWatts,
    @DecimalMin("0.0") double noiseWatts,
    @DecimalMin(value = "0.0", inclusive = false) double speedMultiplier,
    LocalDateTime startTime,
    @Min(0) long seed) {
}
