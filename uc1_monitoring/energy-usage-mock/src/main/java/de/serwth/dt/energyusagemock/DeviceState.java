package de.serwth.dt.energyusagemock;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DeviceState {
  private final String deviceClass;
  private final String deviceId;
  private final Map<Integer, Instant> completedCommands = new ConcurrentHashMap<>();

  private String station;
  private Integer commandId;
  private String action;
  private EnergyOperation operation;
  private boolean active;
  private long lastSeenMillis;
  private long lastPublishMillis;
  private double energyWh;
  private double lastPowerW;
  private String lastInfo;

  public DeviceState(String deviceClass, String deviceId, String station) {
    this.deviceClass = deviceClass;
    this.deviceId = deviceId;
    this.station = station;
    this.active = false;
    this.lastSeenMillis = System.currentTimeMillis();
    this.lastPublishMillis = System.currentTimeMillis();
    this.energyWh = 0.0;
    this.lastPowerW = 0.0;
    this.lastInfo = "";
  }

  String deviceClass() {
    return deviceClass;
  }

  String deviceId() {
    return deviceId;
  }

  String station() {
    return station;
  }

  void station(String station) {
    this.station = station;
  }

  Integer commandId() {
    return commandId;
  }

  void commandId(Integer commandId) {
    this.commandId = commandId;
  }

  boolean completedAtOrAfter(int commandId, Instant timestamp) {
    Instant completedAt = completedCommands.get(commandId);
    return completedAt != null && !completedAt.isBefore(timestamp);
  }

  void completedCommand(int commandId, Instant timestamp) {
    completedCommands.merge(commandId, timestamp, (current, replacement) ->
        current.isAfter(replacement) ? current : replacement
    );
  }

  String action() {
    return action;
  }

  void action(String action) {
    this.action = action;
  }

  EnergyOperation operation() {
    return operation;
  }

  void operation(EnergyOperation operation) {
    if (this.operation != operation) {
      this.energyWh = 0.0;
      this.lastPublishMillis = System.currentTimeMillis();
    }
    this.operation = operation;
  }

  boolean active() {
    return active;
  }

  void active(boolean active) {
    this.active = active;
  }

  long lastPublishMillis() {
    return lastPublishMillis;
  }

  void lastPublishMillis(long lastPublishMillis) {
    this.lastPublishMillis = lastPublishMillis;
  }

  double energyWh() {
    return energyWh;
  }

  void addEnergyWh(double delta) {
    this.energyWh += delta;
  }

  void lastPowerW(double lastPowerW) {
    this.lastPowerW = lastPowerW;
  }

  String lastInfo() {
    return lastInfo == null ? "" : lastInfo;
  }

  void lastInfo(String lastInfo) {
    this.lastInfo = lastInfo == null ? "" : lastInfo;
  }

  void touch() {
    this.lastSeenMillis = System.currentTimeMillis();
  }
}
