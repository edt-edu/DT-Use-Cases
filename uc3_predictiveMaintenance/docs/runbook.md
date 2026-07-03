# Runbook

## Requirements

- Java 21 recommended
- Gradle 9.x or compatible local Gradle installation
- Docker, only if you want to start a local MQTT broker with the included compose file
- Python and `paho-mqtt`, only if you use the Python conveyor simulator

## Build and test

```bash
gradle clean test
```

## Run integrated mode without MQTT

This starts the integrated DT stack and allows demo values through REST.

```bash
gradle bootRun --args='--spring.profiles.active=integrated'
```

Open:

```text
http://localhost:8080
```

Useful endpoints:

```text
GET  /api/app/status
GET  /api/monitoring/snapshot
POST /api/app/demo/transport
POST /api/app/demo/value
```

## Run integrated mode with MQTT

Start a broker:

```bash
docker compose -f docker-compose.mqtt.yml up
```

Start the DT app:

```bash
gradle bootRun --args='--spring.profiles.active=integrated,mqtt'
```

Open:

```text
http://localhost:8080
```

## Start the conveyor simulator

From the folder where the simulator script is located:

```bash
python mqtt_conveyor_simulator_dt_compatible.py --cycles 0
```

For changing states:

```bash
python mqtt_conveyor_simulator_dt_compatible.py --offset-pattern-ms 0,0,15000,55000 --cycles 0
```

Expected approximate outcomes with the default MAPE-K config:

```text
5 s travel   -> NORMAL
20 s travel  -> WARNING
60 s travel  -> EMERGENCY
```

## PowerShell helpers

```powershell
scripts/run-integrated.ps1
scripts/run-integrated-mqtt.ps1
```

## Shell helpers

```bash
./scripts/run-integrated.sh
./scripts/run-integrated-mqtt.sh
```
