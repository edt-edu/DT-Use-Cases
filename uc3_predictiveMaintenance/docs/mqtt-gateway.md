# MQTT Gateway

The MQTT gateway is the concrete implementation of the abstract gateway layer.

Main package:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/gateway/mqtt/
```

Important classes:

```text
MqttGateway
MqttGatewayConfig
MqttGatewaySpringConfiguration
MqttTopicCatalog
MqttTopicModel
MqttPayloadCodec
MqttClientFactory
```

## Topic model

A machine topic is represented as a `MqttTopicSpec` with:

```text
machine type
port description
port number
direction
data type
topic suffix
```

Data-point IDs are built as:

```text
<machine-id>.<topic-suffix>
```

Example:

```text
Topic:       /1-1-conveyor/phototransistor-feed-station
DataPointId: 1-1-conveyor.phototransistor-feed-station
```

## Direction convention

```text
OUT = machine publishes, DT subscribes
IN  = DT publishes command, machine consumes
```

## Discovery mode

MQTT does not provide a standard broker API to list all topics. The gateway can therefore subscribe to wildcard filters and learn topics when messages arrive.

Example config:

```yaml
dt:
  mqtt:
    enabled: true
    auto-start: true
    broker-uri: tcp://localhost:1883
    machine-id: 1-1-conveyor
    machine-type: CONVEYOR_BELT
    discovery-enabled: true
    discovery-topic-filters:
      - /1-1-conveyor/#
    register-unknown-topics: true
```

When an unknown topic is observed, the synchronizer can auto-create default mappings:

```text
gateway topic -> SQLite latest/history value
gateway topic -> AAS data property
```

## Retained MQTT values

The gateway receives the latest value on startup only if the publisher uses retained MQTT messages.

For testing:

```bash
mosquitto_pub -h localhost -r -t /1-1-conveyor/phototransistor-feed-station -m false
mosquitto_pub -h localhost -r -t /1-1-conveyor/phototransistor-swap-station -m false
```

## Manual test sequence

```bash
mosquitto_pub -h localhost -t /1-1-conveyor/phototransistor-feed-station -m true
mosquitto_pub -h localhost -t /1-1-conveyor/phototransistor-feed-station -m false

# wait a few seconds

mosquitto_pub -h localhost -t /1-1-conveyor/phototransistor-swap-station -m true
mosquitto_pub -h localhost -t /1-1-conveyor/phototransistor-swap-station -m false
```
