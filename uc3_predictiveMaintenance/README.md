# DT Engine Kotlin Spring Boot

Kotlin/Spring Boot skeleton for a Digital Twin engine.

Current state:

- abstract DT engine lifecycle and cycle handling
- gateway abstraction
- model manager abstraction
- mapping and transformation abstraction
- synchronizer abstraction
- generic DT services
- MAPE-K service abstraction
- monitoring service abstraction
- event and payload model
- concrete MQTT gateway for the provided fischertechnik station topics

## MQTT gateway

The concrete gateway is in:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/gateway/mqtt/
```

Important classes:

- `MqttGateway`: concrete implementation of `AbstractGateway`
- `MqttTopicCatalog`: station-specific topic catalog
- `MqttTopicSpec`: one port/topic/data-point definition
- `MqttGatewayConfig`: runtime configuration
- `MqttPayloadCodec`: Boolean/Integer/String payload conversion
- `MqttGatewaySpringConfiguration`: optional Spring bean configuration

Direction convention:

- `OUT`: machine/CPS publishes the value, DT gateway subscribes and reads it
- `IN`: DT gateway publishes a command, machine/CPS consumes it

The gateway subscribes to all `OUT` topics of the configured machine type and converts incoming MQTT messages to `DtEvent(type = GATEWAY_DATA_RECEIVED)`. The latest value is cached under the data point id:

```text
<machine-id>.<topic-suffix>
```

Example:

```text
/1-1-conveyor/phototransistor-feed-station
-> dataPointId = 1-1-conveyor.phototransistor-feed-station
```

Commands are written by calling `MqttGateway.write(...)`. The `command` field can be either a topic suffix such as `move-conveyor-forward` or the full data point id. The command payload should contain `value`.

```kotlin
val result = gateway.write(
    CommandRequest(
        commandId = "cmd-1",
        target = ComponentId("mqtt-gateway"),
        command = "move-conveyor-forward",
        payload = mapOf("value" to true)
    )
)
```

## Spring configuration

The MQTT gateway is disabled by default. Enable it in `application.yml` or environment-specific config:

```yaml
dt:
  mqtt:
    enabled: true
    auto-start: true
    broker-uri: tcp://localhost:1883
    client-id: dt-engine-mqtt-gateway
    machine-id: 1-1-conveyor
    machine-type: CONVEYOR_BELT
    qos: 0
```

Supported `machine-type` values:

```text
VACUUM_GRIPPER
MULTIPROCESSING_STATION
THREE_D_GRIPPER
CONVEYOR_BELT
HIGH_BAY
PUNCHING_MACHINE
SORTING_LINE
INDEXED_LINE
```

The textual labels from the topic documentation, such as `Conveyor Belt` or `3D-Gripper`, are also accepted by `MachineType.parse(...)`.

## Run

```bash
gradle bootRun
```

or create a wrapper in the project folder:

```bash
gradle wrapper
./gradlew bootRun
```

## Tests

The current tests check the topic catalog and Spring context startup:

```bash
gradle test
```

## Notes

The MQTT topic catalog intentionally keeps the topic suffixes as provided. For example, the multiprocessing station command topic `move-converyor-forward` is kept with the original spelling because the real MQTT broker must match exactly.

## SQLite database layer

The database layer is in:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/database/
src/main/kotlin/de/unistuttgart/isw/dtengine/database/sqlite/
```

It adds the local SQLite backing store for the DT engine. The database is disabled by default so the abstract/MQTT-only project still starts without a DB.

Main abstractions:

- `DtDataStore`: data-point dictionary plus latest/history values
- `ServiceRequirementStore`: stores which data points a service needs before it may start
- `DtEventStore`: optional persistent event log
- `AasRepositoryClient`: AAS repository boundary that can now be backed by SQLite

Main SQLite implementations:

- `SqliteDtStore`: concrete data-point/value store and service requirement store
- `SqliteModelManager`: concrete `AbstractModelManager` backed by SQLite
- `SqliteMappingRegistry`: concrete `AbstractMappingRegistry` backed by SQLite
- `SqliteEventStore`: persistent event log
- `SqliteAasRepositoryClient`: persistent local AAS shell/submodel/property store
- `SqliteSchema`: schema bootstrap for all tables
- `SqliteSpringConfiguration`: optional Spring beans

The schema creates these tables:

```text
data_points            known data-point dictionary

data_values            time-series/value history
model_properties       DT model/data properties for the SQLite model manager
aas_shells             persisted AAS shell descriptors
aas_submodels          persisted AAS submodel descriptors
aas_shell_submodels    shell-to-submodel assignments
aas_properties         persisted AAS properties used by AasModelManager
service_requirements   required data points per service
service_registry       service id, type, required/produced data and metadata
service_model_requirements     model properties required by a service
service_function_requirements  required service functions / extension points
mappings               source-target mappings between gateway/model/db/services
dt_events              optional event log
```

Enable SQLite in `application.yml`:

```yaml
spring:
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

dt:
  sqlite:
    enabled: true
    auto-start: true
    jdbc-url: jdbc:sqlite:./data/dt-engine.sqlite
    component-id: sqlite-db
    model-manager-component-id: sqlite-model-manager
    auto-create-properties-from-gateway: true
    aas-repository-enabled: true
```

Example: store a gateway value in SQLite:

```kotlin
store.writeValue(
    DataValue(
        id = DataPointId("1-1-conveyor.phototransistor-feed-station"),
        value = true,
        source = ComponentId("mqtt-gateway"),
        metadata = mapOf(
            "machineId" to "1-1-conveyor",
            "topic" to "/1-1-conveyor/phototransistor-feed-station",
            "valueType" to "BOOLEAN"
        )
    )
)
```

Example: register service requirements and check if the service may start:

```kotlin
store.registerRequirements(
    serviceId = ServiceId("mapek-service"),
    dataPointIds = setOf(
        DataPointId("1-1-conveyor.phototransistor-feed-station"),
        DataPointId("1-1-conveyor.phototransistor-swap-station")
    )
)

val readiness = store.checkServiceReadiness(ServiceId("mapek-service"))
```

`readiness.ready` is only `true` if all required data points are known in `data_points`.

The SQLite model manager can consume a `GATEWAY_DATA_RECEIVED` event and create/update the corresponding model property. With `auto-create-properties-from-gateway: true`, the property id defaults to the data point id.

To pre-register all MQTT catalog topics as known data points, enable catalog seeding:

```yaml
dt:
  sqlite:
    enabled: true
    auto-start: true
    seed-mqtt-catalog: true
    seed-mqtt-writable-commands: true
  mqtt:
    machine-id: 1-1-conveyor
    machine-type: CONVEYOR_BELT
```

This writes the configured machine's MQTT catalog into `data_points`, so service readiness checks can succeed before the first MQTT value is received.

## Service manager and conveyor MAPE-K service

This version adds the first concrete DT service use case and a small service manager.

New service infrastructure:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/service/
├── DtServiceManager.kt
└── DtServiceManagerSpringConfiguration.kt

src/main/kotlin/de/unistuttgart/isw/dtengine/service/mapek/
├── ConveyorKpiMapekService.kt
└── ConveyorKpiMapekSpringConfiguration.kt
```

The database schema now also contains a service registry:

```text
service_registry                  service id, type, component id, produced/required data
service_model_requirements        model properties required by a service
service_function_requirements     required functions / extension points, e.g. sendBack(state)
```

The `DtServiceManager` registers services, writes their service descriptors and requirements to SQLite, checks readiness against known data points, starts ready services, forwards events, and calls service ticks during engine cycles.

### Conveyor MAPE-K use case

The first concrete service is `ConveyorKpiMapekService`. It tracks the process time between two sensor points, e.g. from the first gripper handover to the second gripper handover on a conveyor segment.

Default data-point inputs:

```text
1-1-conveyor.phototransistor-feed-station
1-1-conveyor.phototransistor-swap-station
```

These defaults can later be replaced by the actual first/second vacuum gripper data points or by mappings.

MAPE-K behavior:

```text
Monitor
  ingest start/end sensor events and optional position data points

Analyze
  transportTime = endTimestamp - startTimestamp
  productivity = payloadWeightKg / transportTimeSeconds
  baselineProductivity = baselinePayloadWeightKg / nominalTransportTimeSeconds
  integrityPercent = productivity / baselineProductivity * 100

Plan
  if integrity < 10%  -> EMERGENCY
  if integrity < 30%  -> WARNING
  otherwise           -> NORMAL

Execute
  write current conveyor state and KPI values back as DT data/model properties
  call open extension hook sendBack(state)
```

Produced data points:

```text
conveyor.state
conveyor.transport-time-ms
conveyor.payload-weight-kg
conveyor.productivity-kg-per-second
conveyor.integrity-percent
```

Enable it with SQLite and the service manager:

```yaml
dt:
  sqlite:
    enabled: true
    auto-start: true
    seed-mqtt-catalog: true
  services:
    manager:
      enabled: true
    conveyor-kpi-mapek:
      enabled: true
      start-signal-data-point-id: 1-1-conveyor.phototransistor-feed-station
      end-signal-data-point-id: 1-1-conveyor.phototransistor-swap-station
      payload-weight-kg: 1.0
      baseline-payload-weight-kg: 1.0
      nominal-transport-time-seconds: 5.0
      warning-integrity-threshold-percent: 30.0
      emergency-integrity-threshold-percent: 10.0
```

For later AAS/BaSyx or engine integration, override this hook in a subclass:

```kotlin
protected override suspend fun sendBack(
    state: ConveyorAlarmState,
    payload: Map<String, Any?>
) {
    // send to AAS, engine event bus, REST API, etc.
}
```

## AAS model manager

This version adds an AAS-oriented implementation of `AbstractModelManager`.

New files:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/model/aas/
├── AasModelManager.kt
├── AasModelManagerSpringConfiguration.kt
├── AasModels.kt
├── AasPathCodec.kt
├── AasRepositoryClient.kt
└── InMemoryAasRepositoryClient.kt
```

The `AasModelManager` maps DT model properties to AAS-style properties inside a configured shell and submodels. It keeps the same engine-facing interface as the SQLite model manager:

```text
validateSyntax(candidate)
validateConformance(candidate)
getProperty(propertyId)
createProperty(property)
updateProperty(propertyId, value)
deleteProperty(propertyId)
resolvePropertyId(dataPointOrExternalId)
handleGatewayEvent(event)
buildServiceAnswerEvent(requestEvent)
```

Default AAS structure:

```text
AAS shell
└── Data submodel
    └── gateway/runtime input properties
└── State submodel
    └── derived service/KPI/state properties
```

The default repository is `InMemoryAasRepositoryClient` when only AAS is enabled. If SQLite is also enabled and `dt.sqlite.aas-repository-enabled` is true, Spring provides `SqliteAasRepositoryClient` as the primary repository. This persists AAS shells, submodels, and properties in the same SQLite database that already stores data points, mappings, service registry entries, and event logs. The repository remains behind `AasRepositoryClient`, so a BaSyx adapter can later be plugged in without changing the DT engine, MAPE-K service, or synchronizer.

Enable the AAS model manager:

```yaml
dt:
  aas:
    enabled: true
    auto-start: true
    component-id: aas-model-manager
    shell-id: dt-engine-aas
    shell-id-short: DTEngineAas
    data-submodel-id: dt-engine-aas:data
    data-submodel-id-short: Data
    state-submodel-id: dt-engine-aas:state
    state-submodel-id-short: State
    auto-create-properties-from-gateway: true
  sqlite:
    enabled: true
    aas-repository-enabled: true
```

With this setup, `AasModelManager.createProperty(...)` and `AasModelManager.updateProperty(...)` write to `aas_properties`. The manager can still resolve by `modelPropertyId`, `dataPointId`, or `aasExternalId` because those identifiers are stored in the property metadata.

When both SQLite and AAS are enabled, the AAS model manager is marked as the primary `AbstractModelManager` Spring bean. The SQLite store remains the persistent data/value store and service registry, and the AAS model itself is persisted through `SqliteAasRepositoryClient`. Disable this with `dt.sqlite.aas-repository-enabled: false` if you want the AAS model manager to use the in-memory repository or a later BaSyx adapter.

Example: write the conveyor MAPE-K state into the AAS model:

```kotlin
modelManager.updateProperty(
    ModelPropertyId("conveyor.state"),
    DataValue(
        id = DataPointId("conveyor.state"),
        value = "WARNING",
        source = ComponentId("conveyor-kpi-mapek-service")
    )
)
```

The resulting AAS address is stored in the model property metadata:

```text
aasShellId
aasSubmodelId
aasIdShortPath
aasExternalId
```

For MQTT gateway events, `AasModelManager.handleGatewayEvent(...)` creates or updates the corresponding AAS property automatically when `auto-create-properties-from-gateway` is enabled.

## Monitoring REST service and frontend

This version adds a REST-connected monitoring service and a Vue/Vite frontend.

Backend additions:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/monitoring/
├── MonitoringApiModels.kt
├── MonitoringRestController.kt
├── MonitoringSpringConfiguration.kt
└── RestMonitoringService.kt
```

Frontend additions:

```text
frontend/monitoring-ui/
├── package.json
├── vite.config.ts
└── src/
    ├── App.vue
    ├── components/
    └── services/monitoringApi.ts
```

The REST API exposes the currently known machine data points, the conveyor state produced by the MAPE-K service, service registry entries, and AAS properties:

```text
GET /api/monitoring/snapshot
GET /api/monitoring/data-points
GET /api/monitoring/data-points/{id}/history?limit=50
GET /api/monitoring/services
GET /api/monitoring/aas/properties
GET /api/monitoring/health
```

The snapshot is the main endpoint for the UI. It contains:

```text
conveyor.state
conveyor.transport-time-ms
conveyor.payload-weight-kg
conveyor.productivity-kg-per-second
conveyor.integrity-percent
machineData[]
services[]
aasProperties[]
```

Enable the backend monitoring API:

```yaml
dt:
  monitoring:
    enabled: true
    auto-start: true
    register-with-service-manager: true
    service-id: monitoring-rest
    component-id: monitoring-rest-service
    max-recent-events: 100
    default-history-limit: 50
```

A useful local configuration for the full monitoring path is:

```yaml
dt:
  sqlite:
    enabled: true
    auto-start: true
    seed-mqtt-catalog: true
    aas-repository-enabled: true
  aas:
    enabled: true
    auto-start: true
  services:
    manager:
      enabled: true
    conveyor-kpi-mapek:
      enabled: true
  monitoring:
    enabled: true
    auto-start: true
```

Run the frontend:

```bash
cd frontend/monitoring-ui
npm install
npm run dev
```

The development server runs on port `5173` and proxies `/api` to the Spring Boot backend on `http://localhost:8080`.

## Mapping, synchronizer and event wiring

The final integration layer adds a central event bus, a concrete mapping synchronizer, automatic default mapping seeding and REST visibility for mappings/events.

### Runtime event flow

```text
MQTT gateway event
  -> DtEventBus
  -> MappingSynchronizer
       -> SQLite runtime value history
       -> AAS model property
  -> DtServiceManager
       -> ConveyorKpiMapekService / RestMonitoringService
  -> dt_events table and monitoring UI
```

### Default mappings

On startup, `DefaultMappingSeeder` can seed these mappings:

```text
GatewayEndpoint(mqtt-gateway, <machine data point>)
  -> DatabaseEndpoint(sqlite-db, data_values.value, <machine data point>)

GatewayEndpoint(mqtt-gateway, <machine data point>)
  -> ModelEndpoint(aas-model-manager, <machine data point>)

ServiceEndpoint(conveyor-kpi-mapek-service, conveyor.state / KPI outputs)
  -> DatabaseEndpoint(sqlite-db, data_values.value, <KPI data point>)

ServiceEndpoint(conveyor-kpi-mapek-service, conveyor.state / KPI outputs)
  -> ModelEndpoint(aas-model-manager, <KPI property>)
```

The MQTT mappings are generated from the MQTT station catalog for the configured `dt.mqtt.machine-id` and `dt.mqtt.machine-type`.

### New backend components

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/event/
├── InMemoryDtEventBus.kt
└── EventSpringConfiguration.kt

src/main/kotlin/de/unistuttgart/isw/dtengine/mapping/
├── DefaultMappingSeeder.kt
├── DefaultValueTransformer.kt
├── InMemoryMappingRegistry.kt
└── MappingProperties.kt

src/main/kotlin/de/unistuttgart/isw/dtengine/synchronization/
├── MappingSynchronizer.kt
└── SynchronizationSpringConfiguration.kt

src/main/kotlin/de/unistuttgart/isw/dtengine/engine/
├── DefaultDtEngine.kt
└── DefaultDtEngineSpringConfiguration.kt
```

### New monitoring endpoints

```text
GET /api/monitoring/mappings
GET /api/monitoring/events?limit=100
```

The Vue monitoring dashboard also shows a compact mapping list and recent engine events in the side column.

### Useful local configuration

```yaml
dt:
  sqlite:
    enabled: true
    auto-start: true
    seed-mqtt-catalog: true
    aas-repository-enabled: true
  aas:
    enabled: true
    auto-start: true
  mqtt:
    enabled: true
    auto-start: true
    machine-id: 1-1-conveyor
    machine-type: CONVEYOR_BELT
  services:
    manager:
      enabled: true
    conveyor-kpi-mapek:
      enabled: true
  events:
    enabled: true
    persist: true
  mappings:
    enabled: true
    auto-seed: true
  synchronization:
    enabled: true
    auto-start: true
    subscribe-to-events: true
    bridge-gateways-to-event-bus: true
    seed-mappings-on-startup: true
  monitoring:
    enabled: true
    auto-start: true
```

## Java / Kotlin version note

This project targets Java 21 bytecode via the Gradle Java/Kotlin toolchain. If your machine runs Java 25 as the default JVM, older Kotlin Gradle plugins may fail with:

```text
java.lang.IllegalArgumentException: 25.0.3
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(...)
```

The build script has therefore been updated to Kotlin `2.3.21`, which is Java-25-aware. You can still run the project on Java 21 by setting `JAVA_HOME` to a JDK 21 installation before executing Gradle.

Recommended quick start on Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
gradle clean bootRun
```

If you use Java 25 as the Gradle runtime, make sure your Gradle version also supports Java 25.


## Integrated runnable app

This version adds an application-level startup profile and REST control API for running the whole prototype as one Spring Boot app.

New backend package:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/app/
├── AppApiModels.kt
├── DtAppLifecycleService.kt
├── DtAppRestController.kt
└── DtAppSpringConfiguration.kt
```

New Spring profiles:

```text
src/main/resources/application-integrated.yml   full local app without a live MQTT broker
src/main/resources/application-mqtt.yml         MQTT overlay for a real broker
```

The integrated profile starts the backend path in a deterministic order:

```text
SQLite data store
  -> AAS model manager backed by SQLite
  -> mapping seeding
  -> event-bus subscriptions and gateway bridge
  -> mapping synchronizer
  -> service manager and services
  -> optional gateways
  -> DT engine
```

### Run local integrated app without MQTT

This is the easiest way to test the complete DT, DB, AAS, MAPE-K, synchronizer, event and monitoring path locally:

```bash
gradle bootRun --args='--spring.profiles.active=integrated'
```

Open:

```text
http://localhost:8080
```

The static page at `/` uses the same monitoring REST API as the Vue dashboard. It also includes a small demo control for injecting conveyor transports without a physical MQTT broker.

Useful endpoints:

```text
GET  /api/app/status
POST /api/app/start
POST /api/app/stop
POST /api/app/cycle
POST /api/app/demo/transport
POST /api/app/demo/value
```

Example demo transport:

```bash
curl -X POST http://localhost:8080/api/app/demo/transport \
  -H "Content-Type: application/json" \
  -d '{"durationSeconds": 5.0, "payloadWeightKg": 1.0, "runEngineCycle": true}'
```

Longer durations reduce the calculated productivity and integrity. With the default nominal transport time of 5 seconds, a transport duration above roughly 16.7 seconds leads to `WARNING`, and above roughly 50 seconds leads to `EMERGENCY`.

### Run integrated app with MQTT

Start a local Mosquitto broker if needed:

```bash
docker compose -f docker-compose.mqtt.yml up
```

Then start the app with the MQTT overlay profile:

```bash
gradle bootRun --args='--spring.profiles.active=integrated,mqtt'
```

On Windows PowerShell, convenience scripts are available:

```powershell
.\scripts\run-integrated.ps1
.\scripts\run-integrated-mqtt.ps1
```

On macOS/Linux:

```bash
./scripts/run-integrated.sh
./scripts/run-integrated-mqtt.sh
```

## MQTT-driven integrated runtime, no manual POST required

The REST `POST /api/app/demo/*` endpoints are only for local testing without a broker. In the real MQTT profile, the data path is automatic:

```text
MQTT broker publishes machine value
  -> MqttGateway receives it via subscription
  -> event bus receives GATEWAY_DATA_RECEIVED
  -> MappingSynchronizer writes SQLite + AAS
  -> ServiceManager ingests service inputs
  -> periodic app cycle ticks MAPE-K and writes KPI/state outputs
  -> monitoring UI polls REST snapshot
```

Run it with:

```bash
gradle bootRun --args='--spring.profiles.active=integrated,mqtt'
```

The `application-mqtt.yml` profile now enables the gateway and the app polling loop:

```yaml
dt:
  app:
    start-gateways: true
    polling:
      enabled: true
      interval-millis: 1000
  mqtt:
    enabled: true
    broker-uri: tcp://localhost:1883
    machine-id: 1-1-conveyor
    machine-type: CONVEYOR_BELT
    discovery-enabled: true
    discovery-topic-filters:
      - /1-1-conveyor/#
    register-unknown-topics: true
```

The gateway does not use REST POST to ingest machine values. It subscribes to the configured MQTT topics. The UI then polls the Spring Boot monitoring REST endpoints to read the current DT snapshot.

### About “pulling” from MQTT

MQTT is a publish/subscribe protocol. A normal MQTT broker does not provide a standard API to list all topics or query “the latest value of every topic”. The gateway can receive the latest value immediately after subscribing only when the producer publishes retained messages. Without retained messages, the gateway receives values when the machine publishes them.

For that reason, this implementation combines:

- catalog subscriptions for known fischertechnik topics,
- wildcard discovery subscription for machine-scoped topics, e.g. `/1-1-conveyor/#`,
- automatic registration/mapping of discovered topics,
- periodic DT engine cycles to process the most recent cached values and tick services.

