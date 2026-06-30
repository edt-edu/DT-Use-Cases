package de.serwth.dt.smartgridmock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SmartEnergyGridService {

  private static final double TWO_PI = 2.0 * Math.PI;

  private final SmartGridProperties properties;
  private final Instant realStart;
  private final Clock clock;

  @Autowired
  public SmartEnergyGridService(SmartGridProperties properties) {
    this(properties, Clock.systemUTC());
  }

  SmartEnergyGridService(SmartGridProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.realStart = clock.instant();
  }

  public AvailableEnergyResponse currentAvailableEnergy() {
    LocalDateTime simulatedTime = simulatedTime();
    double baseline = properties.baselineWatts();
    double solar = solarEnergy(simulatedTime.toLocalTime());
    double noise = noise(simulatedTime);
    double availableEnergy = Math.max(0.0, baseline + solar + noise);

    return new AvailableEnergyResponse(
        round(availableEnergy),
        "W",
        simulatedTime,
        round(baseline),
        round(solar),
        round(noise),
        properties.speedMultiplier());
  }

  private LocalDateTime simulatedTime() {
    long elapsedMillis = Duration.between(realStart, clock.instant()).toMillis();
    long simulatedMillis = Math.round(elapsedMillis * properties.speedMultiplier());
    return properties.startTime().plus(Duration.ofMillis(simulatedMillis));
  }

  private double solarEnergy(LocalTime time) {
    double hour = time.toSecondOfDay() / 3600.0;
    double daylightProgress = (hour - 6.0) / 12.0;
    if (daylightProgress <= 0.0 || daylightProgress >= 1.0) {
      return 0.0;
    }

    return properties.solarPeakWatts() * Math.sin(Math.PI * daylightProgress);
  }

  private double noise(LocalDateTime simulatedTime) {
    long minute = simulatedTime.toEpochSecond(ZoneOffset.UTC) / 60L;
    double x = Math.sin((minute + properties.seed()) * 12.9898) * 43758.5453;
    double normalized = 2.0 * (x - Math.floor(x)) - 1.0;
    double slowVariation = Math.sin(TWO_PI * minute / 97.0 + properties.seed()) * 0.35;
    return properties.noiseWatts() * clamp(normalized * 0.65 + slowVariation, -1.0, 1.0);
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
