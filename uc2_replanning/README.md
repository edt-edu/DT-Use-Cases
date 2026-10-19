# Flexible Replanning DT

The implementation lives in its own repository, <https://github.com/edt-edu/uc2-flexible-replanning>. It runs together with three other
[edt-edu](https://github.com/edt-edu) repositories (setup configurations, gateway, SCADA). All four are tagged `mbdo-dt-uc2`: checking out
that tag in each of them gives the versions this use case was run with, so it can be reproduced.

| Repository | URL | Used for |
| --- | --- | --- |
| `uc2-flexible-replanning` | <https://github.com/edt-edu/uc2-flexible-replanning> | the digital twin: backend + frontend |
| `dt-setups` | <https://github.com/edt-edu/dt-setups> | configuration and deployment (Ansible) of the setup: RevPi controllers, gateway, InfluxDB, SCADA |
| `dt-platform` | <https://github.com/edt-edu/dt-platform> | `components/gateways/data-gateway-service`, only to run the gateway on your machine |
| `cps-fischertechnik` | <https://github.com/edt-edu/cps-fischertechnik> | `factoryscada` (SCADA backend + frontend), with the replanning API the twin calls |

Clone them side by side in one folder, at the tag:

```bash
mkdir -p ~/edt-edu && cd ~/edt-edu
for repo in uc2-flexible-replanning dt-setups dt-platform cps-fischertechnik; do
  git clone --branch mbdo-dt-uc2 https://github.com/edt-edu/$repo.git
done
```

`--branch` with a tag leaves each clone on a detached `HEAD` at the tagged commit. In clones you already have:
`git fetch --tags && git checkout mbdo-dt-uc2`.

## Architecture

The digital twin follows the execution of the production line from the gateway measurements, detects when a token
does not progress as its SysML mission expects (e.g. the conveyor belt fails and the token stays at its feed), records
the incident, and asks the FactorySCADA to move the machines to alternative missions.

Paths are relative to `src/main/java/mbdo/dt/` of that repository.

- **DT Engine**: [`orchestrator/DtEngine`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/java/mbdo/dt/orchestrator/DtEngine.java)
  loads the models, starts the runtime services and dispatches every observation to them.
  [`AnomalyTimeoutScheduler`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/java/mbdo/dt/orchestrator/AnomalyTimeoutScheduler.java)
  re-checks the time constraints periodically, so a stalled state is noticed even when no message arrives.
- **Models**: the SysML v2 models under
  [`src/main/resources/models`](https://github.com/edt-edu/uc2-flexible-replanning/tree/mbdo-dt-uc2/src/main/resources/models) describe the machines (`CB`, `SL`,
  `MPS`, `VGR`), the token-location mission (`Token-Location-Mission`) and the replanning strategies (`Replanning`).
  They are parsed by [`models/SysMLV2Parser`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/java/mbdo/dt/models/SysMLV2Parser.java)
  into a structural and a behavioral model.
- **Anomaly detection**:
  [`services/impl/AnomalyDetectionImpl`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/java/mbdo/dt/services/impl/AnomalyDetectionImpl.java)
  runs the primary state machine (`TokenLocation`) once per token and raises an anomaly when a guard or a
  `StateDurationLimit` constraint is violated.
- **Replanning**:
  [`services/impl/ReplanningServiceImpl`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/java/mbdo/dt/services/impl/ReplanningServiceImpl.java)
  picks the strategy triggered by the anomaly, plans the mission migration of each machine from its current SCADA
  state, and executes it through the SCADA replanning REST API
  ([`replanning/scada/RestScadaClient`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/java/mbdo/dt/replanning/scada/RestScadaClient.java)):
  stop a machine, swap its mission, send a command.
- **Notification / incidents**: [`incident/`](https://github.com/edt-edu/uc2-flexible-replanning/tree/mbdo-dt-uc2/src/main/java/mbdo/dt/incident) keeps the
  incident registry and history and streams them to the GUI (`/api/replanning-incidents`).
- **Digital shadow**: [`data/DigitalShadow`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/java/mbdo/dt/data/DigitalShadow.java)
  stores the measurements and incidents in InfluxDB (bucket `digital_twin`).
- **Gateway interface**:
  [`connectivity/MqttMessageHandler`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/java/mbdo/dt/connectivity/MqttMessageHandler.java)
  subscribes to the topics `gateway/<setup>/<MachineModel>/<attribute>` republished by the platform
  [data-gateway-service](https://github.com/edt-edu/dt-platform/tree/mbdo-dt-uc2/components/gateways/data-gateway-service)
  and maps them onto the model attributes, following
  [`src/main/resources/configuration.yml`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/resources/configuration.yml). The twin
  never talks to the PLCs directly.
- **Dashboard**: the Angular frontend in [`frontend`](https://github.com/edt-edu/uc2-flexible-replanning/tree/mbdo-dt-uc2/frontend) shows the 2D shopfloor map
  (machine state and token location, from `/api/machines/stream`) and the replanning incidents.

## Starting the DT

The twin runs on the physical Fischertechnik platform of Rennes, setup
[`REN_1S1V_1C1V_1H1M_02`](https://github.com/edt-edu/dt-setups/tree/mbdo-dt-uc2/REN_Setups/REN_1S1V_1C1V_1H1M_02):
three RevPi PLCs, a shared MQTT broker and a gateway on `mbdo-server`. It needs the four repositories listed above, cloned side by side at tag `mbdo-dt-uc2`.

### Where things run

The SCADA running in Docker on `mbdo-server` is built from the registry image, which doesn't have the replanning API.
So the SCADA runs on your machine, from the `cps-fischertechnik` clone, next to the twin:

| Where | What | Address |
| --- | --- | --- |
| `mbdo-server` | MQTT broker (`~/mbdo/mosquitto`) | `mbdo-server.local:1883` |
| `mbdo-server` | gateway + InfluxDB `factory` (`~/mbdo/gateway`) | – |
| `RevPi01` (`RevPi103156.local`) | VGR1 + sorting line | 6001 / 6011 |
| `RevPi02` (`RevPi103259.local`) | VGR2 + conveyor belt | 6001 / 6011 |
| `RevPi03` (`RevPi103228.local`) | multiprocessing + high bay | 6001 / 6011 |
| your machine | InfluxDB `digital_twin`, SCADA backend + frontend, DT backend + frontend | `:8086`, `:9000` + <http://localhost:4201>, `:8080` + <http://localhost:4200> |

Only one SCADA may drive the PLCs, so the server's SCADA must be stopped while yours runs.

The launch steps (deployment, server, InfluxDB, RevPi controllers, SCADA, twin), the checks, the configuration of the
twin and the ports are in the [README of `uc2-flexible-replanning`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/README.md#starting-the-dt), at the same tag.
