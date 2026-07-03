# Troubleshooting

## `conveyor.state` stays `UNKNOWN`

This means no completed MAPE-K measurement has been written yet.

Check:

```text
GET /api/app/status
GET /api/monitoring/snapshot
```

Expected services:

```text
sqlite-db                  RUNNING
aas-model-manager          RUNNING
dt-service-manager         RUNNING
conveyor-kpi-mapek-service RUNNING
dt-engine                  RUNNING
```

Also check the service registry section in the snapshot:

```text
serviceId: conveyor-kpi-mapek
ready: true
missingDataPoints: []
```

Then verify that one complete sensor sequence arrived:

```text
feed-station true
feed-station false
swap-station true
swap-station false
```

The event-driven MAPE-K patch should process short pulses immediately. If the state is still unknown, inspect `recentEvents` for:

```text
GATEWAY_DATA_RECEIVED
SERVICE_OUTPUT
MAPPING_APPLIED for conveyor.state
```

## MQTT values arrive but MAPE-K does not update

Check the exact data-point IDs. Default MAPE-K inputs are:

```text
1-1-conveyor.phototransistor-feed-station
1-1-conveyor.phototransistor-swap-station
```

If your simulator publishes different topics, either change the simulator or configure the service input IDs.

## Gateway receives only `false`

Short sensor pulses may be missed if the service only reads latest DB values. The current project includes the event-driven service manager patch to avoid this.

For the Python simulator, keep sensor dwell long enough for human debugging:

```bash
python mqtt_conveyor_simulator_dt_compatible.py --sensor-dwell-ms 1500 --cycles 0
```

## MQTT broker does not provide latest values after app restart

MQTT only sends the current value on subscription if the publisher used retained messages.

Publish retained initial states:

```bash
mosquitto_pub -h localhost -r -t /1-1-conveyor/phototransistor-feed-station -m false
mosquitto_pub -h localhost -r -t /1-1-conveyor/phototransistor-swap-station -m false
```

## Spring bean missing for app lifecycle

Start with the correct profile:

```bash
gradle bootRun --args='--spring.profiles.active=integrated,mqtt'
```

## JUnit Platform launcher error

Make sure `build.gradle.kts` contains:

```kotlin
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

## Java 25 / Kotlin compiler error

Use Java 21 for running Gradle, or keep Kotlin updated to a version that supports your Java version.

Recommended for this project:

```text
Java 21
```
