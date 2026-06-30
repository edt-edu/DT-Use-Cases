# Smart Energy Grid Mock

Lightweight Spring Boot service that mocks the currently available energy from a smart grid for the monitoring digital twin.

The service exposes one REST endpoint:

```text
GET http://localhost:8090/available-energy
```

Example response:

```json
{
  "availableEnergy": 3706.34,
  "unit": "W",
  "simulatedTime": "2026-06-15T09:48:00",
  "baselineEnergy": 1200.0,
  "solarEnergy": 2320.23,
  "noise": 186.11,
  "speedMultiplier": 60.0
}
```

## Model

Available energy is calculated as:

```text
baseline + solar curve + noise
```

The solar curve is zero between 18:00 and 06:00 and follows a sine curve during the day, peaking at noon. Noise is deterministic for a given seed and simulated minute, so repeated calls within the same simulated minute are stable.

## Configuration

All settings can be provided as Spring Boot properties or environment variables:

| Property | Environment variable | Default | Description |
| --- | --- | --- | --- |
| `server.port` | `PORT` | `8090` | HTTP port |
| `smart-grid.baseline-watts` | `SMART_GRID_BASELINE_WATTS` | `1200` | Static available-energy baseline |
| `smart-grid.solar-peak-watts` | `SMART_GRID_SOLAR_PEAK_WATTS` | `4500` | Maximum additional solar energy at noon |
| `smart-grid.noise-watts` | `SMART_GRID_NOISE_WATTS` | `250` | Maximum absolute noise amplitude |
| `smart-grid.speed-multiplier` | `SMART_GRID_SPEED_MULTIPLIER` | `60` | Simulated seconds per real second |
| `smart-grid.start-time` | `SMART_GRID_START_TIME` | `2026-06-15T00:00:00` | Simulated timestamp at application startup |
| `smart-grid.seed` | `SMART_GRID_SEED` | `42` | Deterministic noise seed |

## Startup

From the repository root:

```bash
./gradlew :smart-energy-grid-mock:bootRun
```

With custom simulation speed:

```bash
SMART_GRID_SPEED_MULTIPLIER=300 ./gradlew :smart-energy-grid-mock:bootRun
```

Or with Spring Boot arguments:

```bash
./gradlew :smart-energy-grid-mock:bootRun --args='--smart-grid.speed-multiplier=300 --server.port=8095'
```
