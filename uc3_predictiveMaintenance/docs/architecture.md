# Architecture Overview

The DT engine is structured around a small set of replaceable components:

```text
MQTT Broker / Machine
        |
        v
MqttGateway
        |
        v
DtEventBus
        |
        +--> MappingSynchronizer --> SQLite data store
        |                         --> AAS model manager
        |
        +--> DtServiceManager ----> MAPE-K services
        |                         --> Monitoring services
        |
        v
Monitoring REST API + UI
```

## Main components

### Gateway

The gateway connects external machine communication to the DT event model. The current concrete implementation is `MqttGateway`.

Responsibilities:

- subscribe to configured MQTT topics
- optionally discover wildcard MQTT topics
- decode raw MQTT payloads
- emit `GATEWAY_DATA_RECEIVED` events
- publish command values to writable MQTT topics

### Database

The SQLite layer stores runtime and configuration data.

Responsibilities:

- data-point dictionary
- latest values and history
- service registry
- service requirements
- mappings
- DT events
- local AAS persistence

### AAS model manager

The AAS model manager mirrors gateway and service values into an AAS-like model structure.

Logical structure:

```text
AAS Shell: dt-engine-aas
├── Data submodel
│   └── machine and gateway values
└── State submodel
    └── derived service states and KPIs
```

### Service manager

The service manager owns DT services and checks whether they are ready based on the service registry.

Responsibilities:

- start and stop services
- register service metadata
- forward events to services
- trigger service ticks
- publish service output events

### MAPE-K service

The current concrete MAPE-K service is `ConveyorKpiMapekService`.

It observes conveyor transport time, calculates productivity and integrity, and writes a conveyor state back to the DT.

### Mapping synchronizer

The synchronizer applies source-to-target mappings, for example:

```text
MQTT gateway value -> SQLite data value
MQTT gateway value -> AAS data property
Service output     -> SQLite KPI value
Service output     -> AAS state property
```

### Monitoring

The monitoring layer exposes REST endpoints and a small UI under `http://localhost:8080`.

The UI reads a monitoring snapshot from the backend rather than talking directly to MQTT or SQLite.
