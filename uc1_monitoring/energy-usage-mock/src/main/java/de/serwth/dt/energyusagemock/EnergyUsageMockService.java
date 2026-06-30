package de.serwth.dt.energyusagemock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EnergyUsageMockService {
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^COMMAND\\s+(\\w+)\\s+(\\d+)\\s+(\\w+)\\s*(.*)$");

  private static final Map<EnergyOperation, Double> POWER_MODEL_W = Map.of(
      EnergyOperation.DRILLING, 850.0,
      EnergyOperation.MILLING, 950.0,
      EnergyOperation.HEATING, 1950.0,
      EnergyOperation.GRINDING, 1650.0,
      EnergyOperation.POLISHING, 750.0
  );

  private final EnergyUsageMockConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ConcurrentMap<String, DeviceState> states = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<EnergyMeasurement> pendingMeasurements = new ConcurrentLinkedQueue<>();

  EnergyUsageMockService(EnergyUsageMockConfig config) {
    this.config = config;
  }

  List<EnergyMeasurement> energyMeasurements() {
    ArrayList<EnergyMeasurement> measurements = new ArrayList<>();
    EnergyMeasurement pending;
    while ((pending = pendingMeasurements.poll()) != null) {
      measurements.add(pending);
    }
    for (DeviceState state : new ArrayList<>(states.values())) {
      synchronized (state) {
        if (state.active() || state.operation() != null) {
          measurements.add(energyMeasurement(state));
        }
      }
    }
    return measurements;
  }

  void handleMessage(String topic, byte[] payload) {
    try {
      JsonNode envelope = parseEnvelope(payload);
      String value = envelope.path("value").asText("");
      Instant timestamp = parseTimestamp(envelope.path("timestamp").asText(""));
      TopicDevice topicDevice = topicDevice(topic);
      String key = stateKey(topicDevice.deviceClass(), topicDevice.deviceId());

      if (topic.endsWith("/events/received/command")) {
        handleCommand(key, topicDevice, value, timestamp);
      }
      else if (topic.endsWith("/events/emitted/command_feedback")) {
        handleCommandFeedback(key, topicDevice, value, timestamp);
      }
    }
    catch (Exception e) {
      System.err.println("Failed to process message on " + topic + ": " + e.getMessage());
    }
  }

  private void handleCommand(String key, TopicDevice topicDevice, String value, Instant timestamp) {
    Optional<CommandValue> parsed = parseCommandValue(value);
    if (parsed.isEmpty()) {
      return;
    }

    CommandValue command = parsed.get();
    Optional<EnergyOperation> operation = operationFromCommand(topicDevice, command);

    DeviceState state = states.computeIfAbsent(
        key,
        ignored -> new DeviceState(topicDevice.deviceClass(), topicDevice.deviceId(), command.station())
    );

    synchronized (state) {
      if (state.completedAtOrAfter(command.commandId(), timestamp)) {
        return;
      }

      state.station(command.station());
      state.commandId(command.commandId());
      state.action(command.action());
      state.touch();

      if (operation.isPresent()) {
        setOperation(state, operation.get());
        state.active(true);
        state.lastInfo("command received");
      }
    }
  }

  private void handleCommandFeedback(
      String key,
      TopicDevice topicDevice,
      String value,
      Instant timestamp
  ) throws JsonProcessingException {
    Optional<JsonNode> parsed = parseFeedbackValue(value);
    if (parsed.isEmpty()) {
      return;
    }

    JsonNode feedback = parsed.get();
    Integer commandId = feedback.hasNonNull("commandId") ? feedback.get("commandId").asInt() : null;
    String status = feedback.path("status").asText("");
    String info = feedback.path("info").asText("");

    DeviceState state = states.computeIfAbsent(
        key,
        ignored -> new DeviceState(topicDevice.deviceClass(), topicDevice.deviceId(), topicDevice.deviceClass().toUpperCase())
    );

    synchronized (state) {
      state.commandId(commandId);
      state.lastInfo(info);
      state.touch();

      Optional<EnergyOperation> operation = operationFromFeedback(topicDevice, info);

      if (operation.isPresent()) {
        setOperation(state, operation.get());
        state.active(true);
      }
      else if ("MUST_CONTINUE".equals(status) && state.operation() != null) {
        state.active(true);
      }
      else if ("DONE".equals(status)) {
        if (commandId != null) {
          state.completedCommand(commandId, timestamp);
        }
        clearOperation(state);
        state.active(false);
        state.action(null);
        state.lastInfo("done");
      }
    }
  }

  private Optional<EnergyOperation> operationFromCommand(TopicDevice topicDevice, CommandValue command) {
    if ("SortingLine".equals(topicDevice.deviceClass())
        && "SORTING".equals(command.station())
        && "EJECT".equals(command.action())) {
      return Optional.of(EnergyOperation.DRILLING);
    }

    if ("ConveyorBelt".equals(topicDevice.deviceClass())
        && "CONVEYOR".equals(command.station())
        && "MOVE_TO_SENSOR".equals(command.action())) {
      return Optional.of(EnergyOperation.MILLING);
    }

    return Optional.empty();
  }

  private Optional<EnergyOperation> operationFromFeedback(TopicDevice topicDevice, String info) {
    if (!"MultiProcessing".equals(topicDevice.deviceClass())) {
      return Optional.empty();
    }

    String normalized = info.toLowerCase();

    if (normalized.contains("4/16") && normalized.contains("heat payload")) {
      return Optional.of(EnergyOperation.HEATING);
    }

    if (normalized.contains("14/16") && normalized.contains("saw payload")) {
      return Optional.of(EnergyOperation.GRINDING);
    }

    if (normalized.contains("15/16") && normalized.contains("eject payload")) {
      return Optional.of(EnergyOperation.POLISHING);
    }

    return Optional.empty();
  }

  private EnergyMeasurement energyMeasurement(DeviceState state) {
    long now = System.currentTimeMillis();
    double dtSeconds = Math.max(0.0, now - state.lastPublishMillis()) / 1000.0;
    state.lastPublishMillis(now);

    double powerW = getPowerW(state);
    state.addEnergyWh(powerW * dtSeconds / 3600.0);
    state.lastPowerW(powerW);

    return new EnergyMeasurement(
        config.energyMeasurementTopic(state),
        round(powerW, 2),
        Instant.now(),
        state.operation(),
        state.deviceClass(),
        state.deviceId()
    );
  }

  private void setOperation(DeviceState state, EnergyOperation operation) {
    EnergyOperation previous = state.operation();
    if (previous != null && previous != operation) {
      pendingMeasurements.add(baselineMeasurement(state, previous));
    }
    state.operation(operation);
  }

  private void clearOperation(DeviceState state) {
    EnergyOperation previous = state.operation();
    if (previous != null) {
      pendingMeasurements.add(baselineMeasurement(state, previous));
    }
    state.operation(null);
  }

  private EnergyMeasurement baselineMeasurement(DeviceState state, EnergyOperation operation) {
    return new EnergyMeasurement(
        config.energyMeasurementTopic(operation),
        0.0,
        Instant.now(),
        operation,
        state.deviceClass(),
        state.deviceId()
    );
  }

  private double getPowerW(DeviceState state) {
    if (!state.active() || state.operation() == null) {
      return 0.0;
    }

    double power = POWER_MODEL_W.getOrDefault(state.operation(), 50.0);
    String info = state.lastInfo().toLowerCase();

    if (state.operation() == EnergyOperation.HEATING && info.contains("heat payload")) {
      power *= 1.15;
    }
    else if (state.operation() == EnergyOperation.GRINDING && info.contains("saw payload")) {
      power *= 1.10;
    }
    else if (state.operation() == EnergyOperation.POLISHING && info.contains("eject payload")) {
      power *= 1.05;
    }
    else if (info.contains("waiting for hold timer")) {
      power *= 0.75;
    }
    else if (info.contains("move") || info.contains("arm") || info.contains("rotating")) {
      power *= 1.05;
    }

    return round(Math.max(0.0, power), 2);
  }

  private JsonNode parseEnvelope(byte[] payload) throws JsonProcessingException {
    return objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
  }

  private Instant parseTimestamp(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return Instant.now();
    }

    String normalized = timestamp.replaceFirst("([+-]\\d{2}:\\d{2})Z$", "$1");
    try {
      return OffsetDateTime.parse(normalized).toInstant();
    }
    catch (DateTimeParseException ignored) {
      try {
        return Instant.parse(timestamp);
      }
      catch (DateTimeParseException alsoIgnored) {
        return Instant.now();
      }
    }
  }

  private Optional<CommandValue> parseCommandValue(String value) {
    String command = unquoteJsonString(value);
    Matcher matcher = COMMAND_PATTERN.matcher(command);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(new CommandValue(
        matcher.group(1),
        Integer.parseInt(matcher.group(2)),
        matcher.group(3),
        matcher.group(4)
    ));
  }

  private Optional<JsonNode> parseFeedbackValue(String value) throws JsonProcessingException {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readTree(value));
  }

  private String unquoteJsonString(String value) {
    if (value == null) {
      return "";
    }

    String trimmed = value.trim();
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      try {
        return objectMapper.readValue(trimmed, String.class);
      }
      catch (JsonProcessingException ignored) {
        return value;
      }
    }

    return value;
  }

  private TopicDevice topicDevice(String topic) {
    String[] parts = topic.split("/");
    if (parts.length < 4) {
      throw new IllegalArgumentException("Unexpected PLC topic: " + topic);
    }
    return new TopicDevice(parts[2], parts[3]);
  }

  private String stateKey(String deviceClass, String deviceId) {
    return deviceClass + "/" + deviceId;
  }

  private double round(double value, int digits) {
    double factor = Math.pow(10, digits);
    return Math.round(value * factor) / factor;
  }

  private record TopicDevice(String deviceClass, String deviceId) {
  }

  private record CommandValue(String station, int commandId, String action, String args) {
  }
}
