# Monitoring REST API

The monitoring REST API exposes the current DT state and is used by the built-in monitoring UI.

Base URL:

```text
http://localhost:8080
```

## Main endpoints

```text
GET /api/monitoring/snapshot
GET /api/monitoring/data-points
GET /api/monitoring/data-points/{id}/history?limit=50
GET /api/monitoring/services
GET /api/monitoring/aas/properties
GET /api/monitoring/mappings
GET /api/monitoring/events?limit=100
GET /api/monitoring/health
```

## App lifecycle endpoints

```text
GET  /api/app/status
POST /api/app/start
POST /api/app/stop
POST /api/app/cycle
POST /api/app/demo/transport
POST /api/app/demo/value
```

## Snapshot structure

`GET /api/monitoring/snapshot` returns:

```text
timestamp
conveyor
kpis
machineData
services
aasProperties
mappings
recentEvents
messages
```

The `conveyor` object is derived from service outputs:

```text
conveyor.state
conveyor.integrity-percent
conveyor.transport-time-ms
conveyor.productivity-kg-per-second
conveyor.payload-weight-kg
```

If no completed MAPE-K measurement exists yet, the state is:

```text
UNKNOWN
```

## Demo transport request

In non-MQTT integrated mode:

```bash
curl -X POST http://localhost:8080/api/app/demo/transport \
  -H "Content-Type: application/json" \
  -d '{"durationSeconds":20.0,"payloadWeightKg":1.0,"runEngineCycle":true}'
```
