package de.serwth.dt.energyusagemock;

import java.util.concurrent.atomic.AtomicBoolean;

public class EnergyUsageMockApplication {
  public static void main(String[] args) throws Exception {
    EnergyUsageMockConfig config = EnergyUsageMockConfig.from(args);
    AtomicBoolean running = new AtomicBoolean(true);

    EnergyUsageMockService service = new EnergyUsageMockService(config);
    EnergyUsageMqttClient mqttClient = new EnergyUsageMqttClient(config, service);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      running.set(false);
      mqttClient.close();
    }, "energy-usage-mock-shutdown"));

    mqttClient.start();
    try {
      while (running.get()) {
        mqttClient.publishAllStates();
        Thread.sleep(config.publishIntervalMillis());
      }
    }
    finally {
      mqttClient.close();
    }
  }
}
