# Energy Usage Mock

MQTT-based Java mock for device energy measurements. It listens to PLC command and command feedback topics, tracks per-device activity, and publishes mocked power/energy measurements.

The default power model uses small industrial loads between 750 W and 2242.5 W depending on the active operation. In combination with the smart grid mock defaults, this creates example periods where current usage can exceed available energy, especially during low-solar periods or when multiple operations are active.

## Topics

Subscribed topics:

```text
PLC/Island 1/+/+/events/received/command
PLC/Island 1/+/+/events/emitted/command_feedback
```

Published topic per device:

```text
PLC/Island 1/{deviceClass}/{deviceId}/events/emitted/energy_measurement
```

## Configuration

Configuration can be supplied as command-line arguments, system properties, or environment variables.

| Argument | System property | Environment variable | Default |
| --- | --- | --- | --- |
| `--broker-host` | `energyUsageMock.brokerHost` | `ENERGY_USAGE_MOCK_BROKER_HOST` | `localhost` |
| `--broker-port` | `energyUsageMock.brokerPort` | `ENERGY_USAGE_MOCK_BROKER_PORT` | `1883` |
| `--client-id` | `energyUsageMock.clientId` | `ENERGY_USAGE_MOCK_CLIENT_ID` | generated |
| `--publish-interval-ms` | `energyUsageMock.publishIntervalMs` | `ENERGY_USAGE_MOCK_PUBLISH_INTERVAL_MS` | `1000` |
| `--publish-timeout-ms` | `energyUsageMock.publishTimeoutMs` | `ENERGY_USAGE_MOCK_PUBLISH_TIMEOUT_MS` | `2000` |
| `--island` | `energyUsageMock.island` | `ENERGY_USAGE_MOCK_ISLAND` | `Island 1` |

## Startup

From the repository root:

```bash
./gradlew :energy-usage-mock:run
```

Example with a remote broker and faster publishing:

```bash
./gradlew :energy-usage-mock:run --args='--broker-host=192.168.0.10 --publish-interval-ms=500'
```
