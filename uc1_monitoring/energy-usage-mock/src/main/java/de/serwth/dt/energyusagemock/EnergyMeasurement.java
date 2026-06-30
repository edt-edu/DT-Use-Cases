package de.serwth.dt.energyusagemock;

import java.time.Instant;

record EnergyMeasurement(
    String topic,
    double powerW,
    Instant timestamp,
    EnergyOperation operation,
    String sourceDeviceClass,
    String sourceDeviceId
) {
}
