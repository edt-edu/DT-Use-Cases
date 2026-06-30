package monitoringdt.service;

import de.se_rwth.commons.logging.Log;
import functionstructure4fenix.FunctionStructure4FenixManager;
import monitoringdt.MonitoringDTManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SmartEnergyGridPollingService {
  private final RestTemplate restTemplate;
  private final String availableEnergyUrl;

  public SmartEnergyGridPollingService(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${smart-grid.polling.url:http://localhost:8090/available-energy}") String availableEnergyUrl,
      @Value("${smart-grid.polling.timeout-ms:2000}") int timeoutMillis
  ) {
    this.availableEnergyUrl = availableEnergyUrl;
    Duration timeout = Duration.ofMillis(timeoutMillis);
    this.restTemplate = restTemplateBuilder
        .connectTimeout(timeout)
        .readTimeout(timeout)
        .build();
  }

  @Scheduled(fixedDelayString = "${smart-grid.polling.interval-ms:5000}")
  public void pollAvailableEnergy() {
    try {
      AvailableEnergySnapshot snapshot =
          restTemplate.getForObject(availableEnergyUrl, AvailableEnergySnapshot.class);
      if (snapshot == null) {
        return;
      }
      MonitoringDTManager.getEnergyShadow()
          .getAvailableEnergy()
          .addDoubleValue(
              FunctionStructure4FenixManager.doubleValueBuilder()
                  .timestamp(LocalDateTime.now())
                  .content(snapshot.availableEnergy)
                  .build().get()
          );
      Log.info("Got available energy from grid: " + snapshot.availableEnergy, "SmartEnergyGridPolling");
    }
    catch (RestClientException e) {
      Log.warn("Could not poll smart energy grid at " + availableEnergyUrl + ": " + e.getMessage());
    }
  }

  public record AvailableEnergySnapshot(
      double availableEnergy,
      String unit,
      LocalDateTime simulatedTime,
      double baselineEnergy,
      double solarEnergy,
      double noise,
      double speedMultiplier
  ) {
  }
}
