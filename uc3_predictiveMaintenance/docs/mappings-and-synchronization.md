# Mappings and Synchronization

The DT engine uses explicit mappings between gateway values, database values, AAS properties, and services.

Main package:

```text
src/main/kotlin/de/unistuttgart/isw/dtengine/mapping/
src/main/kotlin/de/unistuttgart/isw/dtengine/synchronization/
```

## Main classes

```text
DefaultMappingSeeder
DefaultValueTransformer
InMemoryMappingRegistry
MappingProperties
MappingSynchronizer
SynchronizationSpringConfiguration
```

## Mapping directions

Typical mapping directions are:

```text
GATEWAY_TO_DATABASE
GATEWAY_TO_MODEL
DATABASE_TO_SERVICE
MODEL_TO_SERVICE
SERVICE_TO_DATABASE
SERVICE_TO_MODEL
```

## Default mappings

At startup, the default mapping seeder creates mappings for known MQTT topics:

```text
mqtt:<dataPointId>:to-db
mqtt:<dataPointId>:to-aas
```

For the conveyor MAPE-K service it also creates service-output mappings:

```text
service:conveyor-kpi-mapek:conveyor.state:to-db
service:conveyor-kpi-mapek:conveyor.state:to-aas
service:conveyor-kpi-mapek:conveyor.integrity-percent:to-db
service:conveyor-kpi-mapek:conveyor.integrity-percent:to-aas
...
```

## Auto mappings for discovered MQTT topics

If enabled, unknown MQTT topics are mapped automatically when first observed:

```text
auto:mqtt:<dataPointId>:to-db
auto:mqtt:<dataPointId>:to-aas
```

This is useful for discovery topics such as:

```text
/1-1-conveyor/sim/cycle-start
/1-1-conveyor/sim/cycle-end
```

## Synchronization flow

```text
DtEvent
  -> MappingSynchronizer.synchronize(...)
  -> select matching mappings
  -> transform value
  -> apply target write
  -> emit MAPPING_APPLIED / SYNCHRONIZATION_COMPLETED events
```

## Where values go

Gateway value:

```text
GATEWAY_DATA_RECEIVED
  -> data_values
  -> aas_properties in Data submodel
```

Service output:

```text
SERVICE_OUTPUT
  -> data_values
  -> aas_properties in State submodel
```
