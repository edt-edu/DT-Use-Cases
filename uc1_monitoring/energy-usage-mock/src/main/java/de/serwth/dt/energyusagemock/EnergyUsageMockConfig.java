package de.serwth.dt.energyusagemock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

record EnergyUsageMockConfig(
    String brokerHost,
    int brokerPort,
    String clientId,
    long publishIntervalMillis,
    long publishTimeoutMillis,
    String island
) {
  static EnergyUsageMockConfig from(String[] args) {
    Map<String, String> arguments = parseArgs(args);
    String island = get(arguments, "island", "energyUsageMock.island", "ENERGY_USAGE_MOCK_ISLAND", "Island 1");
    return new EnergyUsageMockConfig(
        get(arguments, "broker-host", "energyUsageMock.brokerHost", "ENERGY_USAGE_MOCK_BROKER_HOST", "localhost"),
        getInt(arguments, "broker-port", "energyUsageMock.brokerPort", "ENERGY_USAGE_MOCK_BROKER_PORT", 1883),
        get(arguments, "client-id", "energyUsageMock.clientId", "ENERGY_USAGE_MOCK_CLIENT_ID",
            "energy-usage-mock-" + UUID.randomUUID()),
        getLong(arguments, "publish-interval-ms", "energyUsageMock.publishIntervalMs",
            "ENERGY_USAGE_MOCK_PUBLISH_INTERVAL_MS", 1000),
        getLong(arguments, "publish-timeout-ms", "energyUsageMock.publishTimeoutMs",
            "ENERGY_USAGE_MOCK_PUBLISH_TIMEOUT_MS", 2000),
        island
    );
  }

  String brokerUri() {
    return "tcp://" + brokerHost + ":" + brokerPort;
  }

  String commandTopic() {
    return "PLC/" + island + "/+/+/events/received/command";
  }

  String feedbackTopic() {
    return "PLC/" + island + "/+/+/events/emitted/command_feedback";
  }

  String energyMeasurementTopic(DeviceState state) {
    EnergyOperation operation = state.operation();
    String machineKind = operation == null ? state.deviceClass() : operation.machineKind();
    String machine = operation == null ? state.deviceId() : operation.machine();
    return "PLC/" + island + "/" + machineKind + "/" + machine
        + "/events/emitted/energy_measurement";
  }

  String energyMeasurementTopic(EnergyOperation operation) {
    return "PLC/" + island + "/" + operation.machineKind() + "/" + operation.machine()
        + "/events/emitted/energy_measurement";
  }

  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> parsed = new HashMap<>();
    for (String arg : args) {
      if (!arg.startsWith("--")) {
        continue;
      }
      int separator = arg.indexOf('=');
      if (separator > 2) {
        parsed.put(arg.substring(2, separator), arg.substring(separator + 1));
      }
    }
    return parsed;
  }

  private static String get(
      Map<String, String> args,
      String argName,
      String propertyName,
      String environmentName,
      String defaultValue
  ) {
    String argValue = args.get(argName);
    if (argValue != null && !argValue.isBlank()) {
      return argValue;
    }

    String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }

    String environmentValue = System.getenv(environmentName);
    if (environmentValue != null && !environmentValue.isBlank()) {
      return environmentValue;
    }

    return defaultValue;
  }

  private static int getInt(
      Map<String, String> args,
      String argName,
      String propertyName,
      String environmentName,
      int defaultValue
  ) {
    return Integer.parseInt(get(args, argName, propertyName, environmentName, Integer.toString(defaultValue)));
  }

  private static long getLong(
      Map<String, String> args,
      String argName,
      String propertyName,
      String environmentName,
      long defaultValue
  ) {
    return Long.parseLong(get(args, argName, propertyName, environmentName, Long.toString(defaultValue)));
  }
}
