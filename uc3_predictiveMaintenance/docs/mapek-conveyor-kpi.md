# MAPE-K Conveyor KPI Service

Concrete class:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/service/mapek/ConveyorKpiMapekService.kt
```

Service ID:

```text
conveyor-kpi-mapek
```

Component ID:

```text
conveyor-kpi-mapek-service
```

## Purpose

The service tracks how long a payload needs to move from the first conveyor sensor to the second conveyor sensor.

The transport time and payload weight are used to calculate productivity. Productivity is then compared to a nominal baseline to estimate conveyor integrity.

## Inputs

Default data points:

```text
1-1-conveyor.phototransistor-feed-station
1-1-conveyor.phototransistor-swap-station
```

The service can also use simulator diagnostic information from:

```text
1-1-conveyor.sim.cycle-end
```

when the diagnostic JSON contains `actual_travel_ms`.

## Outputs

```text
conveyor.state
conveyor.transport-time-ms
conveyor.payload-weight-kg
conveyor.productivity-kg-per-second
conveyor.integrity-percent
```

These are written back through mappings to:

```text
SQLite data values
AAS state properties
Monitoring snapshot
```

## MAPE-K steps

### Monitor

- observe feed-station rising edge
- observe swap-station rising edge
- derive or read transport time
- read configured payload weight

### Analyze

```text
productivity = payloadWeightKg / transportTimeSeconds
baselineProductivity = baselinePayloadWeightKg / nominalTransportTimeSeconds
integrityPercent = productivity / baselineProductivity * 100
```

### Plan

```text
integrity < emergency threshold -> EMERGENCY
integrity < warning threshold   -> WARNING
otherwise                       -> NORMAL
```

Default thresholds:

```text
warning:   30 %
emergency: 10 %
```

### Execute

- emit service output event
- write state and KPIs back into the DT through mappings
- keep `sendBack(state)` as an extension point for later physical commands

## Event-driven behavior

The service manager now forwards relevant events to the MAPE-K service immediately.

This avoids losing short sensor pulses between periodic engine cycles.

```text
GATEWAY_DATA_RECEIVED
  -> DtServiceManager
  -> ConveyorKpiMapekService.handleEvent(...)
  -> immediate service tick
  -> SERVICE_OUTPUT event
  -> MappingSynchronizer
  -> SQLite + AAS
```

## Example outcome

With:

```text
nominalTransportTime = 5 s
payloadWeight = 1 kg
baselinePayloadWeight = 1 kg
```

The following transport times produce:

```text
5 s  -> 100 % integrity -> NORMAL
20 s -> 25 % integrity  -> WARNING
60 s -> 8.3 % integrity -> EMERGENCY
```
