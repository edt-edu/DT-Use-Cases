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

### Layout

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

Only one SCADA may drive the PLCs, so the server's SCADA must be stopped while yours runs (step 1).

### Requirements

- Your machine on the platform network: the `*.local` names are resolved by mDNS. Ansible's
  `REN_shared/ansible/inventories/proxyjump-hosts.yml` inventory works from outside, for the deployment only.
- SSH access to `mbdo-admin@mbdo-server.local` and to the three RevPis as `pi`
- Docker with `docker compose`
- JDK 21 (Lombok in the SCADA backend breaks on newer JDKs)
- Node/npm
- Ansible, for the deployment only

Each block below starts from these variables; adapt `WS` to the folder holding the four clones. Steps
3–7 each keep running, so open one terminal per step and run them in this order.

```bash
export WS=~/edt-edu   # folder containing uc2-flexible-replanning, dt-setups, dt-platform, cps-fischertechnik
export UC2="$WS/uc2-flexible-replanning"
export SETUP="$WS/dt-setups/REN_Setups/REN_1S1V_1C1V_1H1M_02"
```

One-time setup, the frontend dependencies:

```bash
cd "$UC2/frontend" && npm install
cd "$WS/cps-fischertechnik/factoryscada/frontend" && npm install
```

### 0. Deploy (only after a configuration change)

Push the controllers and `rppmcontroller` to the RevPis (add `-l RevPi01` for a single one), and the server
configuration (gateway, SCADA) to `mbdo-server`:

```bash
cd "$SETUP/ansible"
ansible-playbook playbooks/push_plcs_config.yml
ansible-playbook playbooks/push_server_config.yml
```

Then recreate the server's gateway, so it reloads its configuration:

```bash
ssh -t mbdo-admin@mbdo-server.local 'cd ~/mbdo/gateway && sudo docker compose up -d --force-recreate dt-support-gateway'
```

### 1. Server: broker and gateway up, SCADA down

`up -d` leaves running containers alone, so this is safe if they are already up:

```bash
ssh -t mbdo-admin@mbdo-server.local 'cd ~/mbdo/mosquitto && sudo docker compose up -d && cd ~/mbdo/gateway && sudo docker compose up -d && cd ~/mbdo/factoryscada && sudo docker compose stop'
```

### 2. InfluxDB (:8086)

The twin stores its measurements and incidents in a local InfluxDB, with the setup's credentials (org `mbdo`, admin
token). Create its bucket once (fails harmlessly if it exists):

```bash
cd "$SETUP/config/server/gateway"
docker compose up -d --wait influxdb2
docker exec influxdb2 influx bucket create -o mbdo -t "$(cat services/influxdb/config/.env.influxdb2-admin-token)" -n digital_twin
```

If `up` fails with `bind source path does not exist`, an old `influxdb2` container from another checkout is in the
way: add `--force-recreate` (the data volume is kept). Web UI: <http://localhost:8086> (user `admin`, password in
`services/influxdb/config/.env.influxdb2-admin-password`).

### 3. RevPi controllers

One terminal each:

```bash
ssh -t pi@RevPi103156.local 'cd ~/rppmcontroller_current_setup && python3 RevPi_Controller.py'
```

```bash
ssh -t pi@RevPi103259.local 'cd ~/rppmcontroller_current_setup && python3 RevPi_Controller.py'
```

```bash
ssh -t pi@RevPi103228.local 'cd ~/rppmcontroller_current_setup && python3 RevPi_Controller.py'
```

They publish the machine state on `PLC/RevPi0x/...` to `mbdo-server.local:1883` (their `config.yml`), and the server's
gateway republishes it on `gateway/REN_1S1V_1C1V_1H1M_02/...`, the only topics the twin consumes.

### 4. SCADA backend (:9000)

Start it after the controllers: it connects to the RevPis only at startup. `configuration.yml` points it at the three
RevPis, and the `prod` profile (`application-prod.yaml`) at the server's broker.

```bash
cd "$WS/cps-fischertechnik/factoryscada/backend"
SCADA_CONF="$SETUP/config/server/factoryscada/services/scada-backend/config"
./gradlew bootRun --console=plain --args="--configuration.path=$SCADA_CONF/configuration.yml --missions.parallelized.configuration.path=$SCADA_CONF/missions-parallelized-configuration.yml --spring.profiles.active=prod --spring.config.additional-location=file:$SCADA_CONF/ --logging.file.name=build/scada-backend.log"
```

`--logging.file.name` overrides the default `/app/config/scada-backend.log`, which only exists in Docker.

### 5. SCADA frontend (:4201)

```bash
cd "$WS/cps-fischertechnik/factoryscada/frontend"
npm start -- --port 4201
```

Open <http://localhost:4201> to send commands and missions to the machines. It runs on 4201 because the twin's
frontend uses 4200.

### 6. DT backend (:8080)

The broker comes from `src/main/resources/configuration.yml` (`localhost:1883`): point it at the server's with
`MQTT_SOURCE_BROKER_URL` / `MQTT_TARGET_BROKER_URL`. The replanning calls the SCADA on `localhost:9000` by default
(`SCADA_BASE_URL`).

```bash
cd "$UC2"
export MQTT_SOURCE_BROKER_URL=tcp://mbdo-server.local:1883
export MQTT_TARGET_BROKER_URL=tcp://mbdo-server.local:1883
export INFLUX_ORG=mbdo
export INFLUX_BUCKET=digital_twin
export INFLUX_TOKEN="$(cat "$SETUP/config/server/gateway/services/influxdb/config/.env.influxdb2-admin-token")"
sh ./mvnw spring-boot:run
```

`mvnw` is not marked executable in the repository, hence `sh ./mvnw`.

### 7. DT frontend (:4200)

```bash
cd "$UC2/frontend"
npm start
```

Open the twin at <http://localhost:4200>; `/api` is proxied to the backend on :8080.

### Checking it works

Run each check in order. If one fails, the problem is at that link of the chain.

1. The PLCs publish to the broker: `PLC/RevPi01/...`, `PLC/RevPi02/...` and `PLC/RevPi03/...` topics show up.

   ```bash
   ssh -t mbdo-admin@mbdo-server.local "sudo docker exec mqtt-broker mosquitto_sub -t 'PLC/#' -v -W 10" | cut -c1-120 | sort -u -t' ' -k1,1 | head -30
   ```

2. The gateway republishes for the twin: the `VGRSystemModel_01`, `VGRSystemModel_02`, `SLSystemModel`,
   `CBSystemModel`, `MPSystemModel` and `HBSystemModel` topics show up.

   ```bash
   ssh -t mbdo-admin@mbdo-server.local "sudo docker exec mqtt-broker mosquitto_sub -t 'gateway/REN_1S1V_1C1V_1H1M_02/#' -v -W 10" | cut -d/ -f3 | sort | uniq -c
   ```

   If nothing shows up, read the gateway logs:

   ```bash
   ssh -t mbdo-admin@mbdo-server.local 'cd ~/mbdo/gateway && sudo docker compose logs --tail 50 dt-support-gateway'
   ```

3. The twin is running and tracking the token:

   ```bash
   curl -s localhost:8080/dt/status; echo
   curl -s localhost:8080/api/machines/token; echo
   ```

4. End to end: send a command to a machine from the SCADA (<http://localhost:4201>). Its values change on the twin's
   shopfloor view (<http://localhost:4200>). The raw telemetry the view receives:

   ```bash
   curl -sN localhost:8080/api/machines/stream | head -20
   ```

### Stopping

- DT, SCADA and RevPi controllers: `Ctrl-C` in each terminal.
- The local InfluxDB:

  ```bash
  (cd "$SETUP/config/server/gateway" && docker compose stop influxdb2)
  ```

- Give the SCADA back to the server, so the platform runs as usual for the other users. Leave the broker and the
  gateway running: they are shared.

  ```bash
  ssh -t mbdo-admin@mbdo-server.local 'cd ~/mbdo/factoryscada && sudo docker compose up -d'
  ```

### Variant: gateway on your machine

To run a modified gateway, stop the server's one (only one gateway may publish on the shared broker, or every
message is doubled) and start it locally with `configuration.local.yml`, which reads from `mbdo-server.local:1883` and
writes to the local InfluxDB:

```bash
ssh -t mbdo-admin@mbdo-server.local 'cd ~/mbdo/gateway && sudo docker compose stop dt-support-gateway'
cd "$WS/dt-platform/components/gateways/data-gateway-service"
./gradlew bootRun --console=plain --args="--configuration.path=$SETUP/config/server/gateway/services/gateway/config/configuration.local.yml"
```

The path must be absolute, because Gradle runs the app from the gateway directory. Restart the server's gateway
afterwards (`sudo docker compose up -d dt-support-gateway` in `~/mbdo/gateway`).

### Without the platform

The publishers in [`tools/mqtt-publishers`](https://github.com/edt-edu/uc2-flexible-replanning/tree/mbdo-dt-uc2/tools/mqtt-publishers) replay a mission on a broker
directly (`pip install -r requirements.txt` first): `nominal_mission_publisher.py` runs it normally, and
`faulty_conveyor_belt_publisher.py` stalls the token at the conveyor-belt feed, which triggers the conveyor-belt
failure replanning. `--broker` / `--port` select the broker. Don't run them on the platform broker while it is in use:
they inject synthetic sensor values.

## Configuration

### DT backend

Set in [`application.properties`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/resources/application.properties); the ones with
an environment variable can be overridden without editing it.

| Environment variable | Default | Description |
| --- | --- | --- |
| `INFLUX_URL` | `http://localhost:8086` | InfluxDB URL |
| `INFLUX_TOKEN` | `my-super-token` | InfluxDB token |
| `INFLUX_ORG` | `uc2-org` | InfluxDB organisation (`mbdo` for the setup's InfluxDB) |
| `INFLUX_BUCKET` | `digital_twin` | Bucket of the measurements and incidents |
| `MQTT_SOURCE_BROKER_URL` | `tcp://localhost:1883` (from `configuration.yml`) | Broker the gateway topics are read from (`tcp://mbdo-server.local:1883` on the platform) |
| `MQTT_TARGET_BROKER_URL` | `tcp://localhost:1883` (from `configuration.yml`) | Broker the twin publishes on |
| `MQTT_TOPICS` | `gateway/#` | Topics subscribed to |
| `TELEMETRY_GATEWAY_TOPIC_FILTER` | `gateway/#` | Topics forwarded to the shopfloor view |
| `SCADA_BASE_URL` | `http://localhost:9000` | FactorySCADA REST API used by the replanning |

Other properties (pass them as `-Dspring-boot.run.arguments=--<property>=<value>` or edit the file):

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port |
| `sysml.models.path` | `classpath*:models/**/*.sysml` | SysML models loaded at startup |
| `anomaly-detection.primary-state-machine` | `TokenLocation` | State machine run per token |
| `anomaly-detection.timeout-poll-interval-ms` | `2000` | How often the time constraints are re-checked |
| `replanning.retry-delay-ms` / `replanning.retry-max-ms` | `1000` / `60000` | A replanning that can't run yet is retried every delay, for at most max |
| `replanning.command-timeout-ms` | `60000` | Time allowed for the stop command of a failed machine |

`MQTT_BROKER_URL` is not used by the gateway mapping: set `MQTT_SOURCE_BROKER_URL` / `MQTT_TARGET_BROKER_URL` to
change the broker.

### Mapping the gateway topics onto the models

[`src/main/resources/configuration.yml`](https://github.com/edt-edu/uc2-flexible-replanning/blob/mbdo-dt-uc2/src/main/resources/configuration.yml) maps each
gateway measurement (`gateway/+/<MachineModel>/<attribute>`) onto an attribute of a SysML model
(`<model>/<attribute path>`), and lists the topics recorded in InfluxDB. The `<setup>` segment is a wildcard, so the
same file works for every setup. To use another file, start the backend with
`--configuration.path=/absolute/path/configuration.yml`.

### Platform configuration

The setup's own configuration is in `dt-setups/REN_Setups/REN_1S1V_1C1V_1H1M_02/config`, deployed with the Ansible
playbooks of step 0:
- `RevPi0x/python/`: the controller of each RevPi (`RevPi_Controller.py`, with the VGR named positions) and its
  `config.yml` (broker address);
- `server/gateway/services/gateway/config/`: the gateway mapping of the `PLC/...` topics onto `gateway/...`
  (`configuration.yml` for the server, `configuration.local.yml` for your machine);
- `server/factoryscada/services/scada-backend/config/`: the SCADA's machines, missions and broker.

## Ports

| Port | Service | Host |
| --- | --- | --- |
| 1883 | MQTT broker | `mbdo-server.local` |
| 6001 / 6011 | PLC (commands / notifications) | `RevPi103156.local`, `RevPi103259.local`, `RevPi103228.local` |
| 8086 | InfluxDB | `localhost` |
| 9000 | SCADA backend | `localhost` |
| 4201 | SCADA frontend | `localhost` |
| 8080 | DT backend | `localhost` |
| 4200 | DT frontend | `localhost` |
