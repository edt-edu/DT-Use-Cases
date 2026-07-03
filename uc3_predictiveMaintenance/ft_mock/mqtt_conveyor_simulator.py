#!/usr/bin/env python3
"""
MQTT conveyor-belt simulator for the Kotlin DT engine.

This version is adjusted for the current MAPE-K implementation:
- The MAPE-K service derives transport time from the rising edge of
  /<machine-id>/phototransistor-feed-station to the rising edge of
  /<machine-id>/phototransistor-swap-station.
- Sensor TRUE dwell time is intentionally longer than the DT polling interval
  so the current cycle-based implementation does not miss short pulses.
- The default expected travel time is 5000 ms, matching the current default
  nominal-transport-time-seconds: 5.0 in the Spring configuration.

Install dependency:
  python -m pip install paho-mqtt

Example:
  python mqtt_conveyor_simulator_dt_compatible.py --cycles 0

Example with degradation pattern:
  python mqtt_conveyor_simulator_dt_compatible.py \
    --offset-pattern-ms 0,0,5000,12000,50000 --cycles 0
"""

from __future__ import annotations

import argparse
import json
import random
import signal
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Iterable

try:
    import paho.mqtt.client as mqtt
except ImportError as exc:
    raise SystemExit(
        "Missing dependency: paho-mqtt\n"
        "Install with: python -m pip install paho-mqtt"
    ) from exc


CONVEYOR_TOPICS = {
    "feed_sensor": "/{machine_id}/phototransistor-feed-station",
    "swap_sensor": "/{machine_id}/phototransistor-swap-station",
    "pulse_button": "/{machine_id}/pulse-button",
    # These are command topics from the machine table. Keep them optional.
    "motor_forward": "/{machine_id}/move-conveyor-forward",
    "motor_backward": "/{machine_id}/move-conveyor-backward",
}


@dataclass(frozen=True)
class SimulatorConfig:
    broker: str
    port: int
    machine_id: str
    qos: int
    retain: bool
    payload_format: str
    cycles: int
    expected_travel_ms: int
    offset_ms: int
    offset_pattern_ms: list[int]
    jitter_ms: int
    sensor_dwell_ms: int
    cycle_gap_ms: int
    pulse_ms: int
    initial_delay_ms: int
    publish_motor_state: bool
    publish_diagnostics: bool
    seed: int | None


_STOP = False


def request_stop(signum: int, frame: Any) -> None:  # noqa: ARG001
    global _STOP
    _STOP = True


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def topic(config: SimulatorConfig, name: str) -> str:
    return CONVEYOR_TOPICS[name].format(machine_id=config.machine_id)


def raw_payload(value: Any) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def build_payload(config: SimulatorConfig, topic_name: str, value: Any, cycle: int | None = None) -> str:
    if config.payload_format == "raw":
        return raw_payload(value)
    return json.dumps(
        {
            "machine_id": config.machine_id,
            "topic_name": topic_name,
            "value": value,
            "cycle": cycle,
            "ts": now_iso(),
        },
        separators=(",", ":"),
    )


def publish(
    client: mqtt.Client,
    config: SimulatorConfig,
    topic_name: str,
    value: Any,
    cycle: int | None = None,
) -> None:
    mqtt_topic = topic(config, topic_name)
    payload = build_payload(config, topic_name, value, cycle)
    result = client.publish(mqtt_topic, payload, qos=config.qos, retain=config.retain)
    result.wait_for_publish(timeout=2.0)
    print(f"{now_iso()}  PUB  {mqtt_topic:<55} {payload}", flush=True)


def publish_json_topic(
    client: mqtt.Client,
    config: SimulatorConfig,
    mqtt_topic: str,
    payload_obj: dict[str, Any],
) -> None:
    payload = json.dumps(payload_obj, separators=(",", ":"))
    result = client.publish(mqtt_topic, payload, qos=config.qos, retain=config.retain)
    result.wait_for_publish(timeout=2.0)
    print(f"{now_iso()}  PUB  {mqtt_topic:<55} {payload}", flush=True)


def sleep_ms(milliseconds: int) -> None:
    end_time = time.monotonic() + max(0, milliseconds) / 1000.0
    while not _STOP and time.monotonic() < end_time:
        time.sleep(min(0.05, end_time - time.monotonic()))


def sleep_until(start_monotonic: float, offset_ms: int) -> None:
    target = start_monotonic + max(0, offset_ms) / 1000.0
    while not _STOP:
        remaining = target - time.monotonic()
        if remaining <= 0:
            return
        time.sleep(min(0.05, remaining))


def parse_int_list(text: str) -> list[int]:
    if not text.strip():
        return []
    return [int(part.strip()) for part in text.split(",") if part.strip()]


def get_offset_for_cycle(config: SimulatorConfig, cycle: int) -> int:
    if config.offset_pattern_ms:
        return config.offset_pattern_ms[(cycle - 1) % len(config.offset_pattern_ms)]
    return config.offset_ms


def calculate_actual_travel_ms(config: SimulatorConfig, cycle: int) -> tuple[int, int, int]:
    configured_offset = get_offset_for_cycle(config, cycle)
    jitter = random.randint(-config.jitter_ms, config.jitter_ms) if config.jitter_ms > 0 else 0
    actual_travel = max(0, config.expected_travel_ms + configured_offset + jitter)
    actual_offset = actual_travel - config.expected_travel_ms
    return actual_travel, configured_offset, actual_offset


def publish_initial_state(client: mqtt.Client, config: SimulatorConfig) -> None:
    # This gives the DT a clean false state before the first rising edge.
    publish(client, config, "feed_sensor", False)
    publish(client, config, "swap_sensor", False)
    publish(client, config, "pulse_button", False)
    if config.publish_motor_state:
        publish(client, config, "motor_forward", False)
        publish(client, config, "motor_backward", False)


def run_cycle(client: mqtt.Client, config: SimulatorConfig, cycle: int) -> None:
    actual_travel_ms, configured_offset_ms, actual_offset_ms = calculate_actual_travel_ms(config, cycle)

    if config.publish_diagnostics:
        publish_json_topic(
            client,
            config,
            f"/{config.machine_id}/sim/cycle-start",
            {
                "machine_id": config.machine_id,
                "cycle": cycle,
                "ts": now_iso(),
                "expected_travel_ms": config.expected_travel_ms,
                "configured_offset_ms": configured_offset_ms,
                "actual_offset_ms": actual_offset_ms,
                "actual_travel_ms": actual_travel_ms,
                "sensor_dwell_ms": config.sensor_dwell_ms,
            },
        )

    if config.publish_motor_state:
        publish(client, config, "motor_backward", False, cycle)
        publish(client, config, "motor_forward", True, cycle)

    # Optional pulse before the workpiece reaches the feed sensor.
    publish(client, config, "pulse_button", True, cycle)
    sleep_ms(config.pulse_ms)
    publish(client, config, "pulse_button", False, cycle)

    # Important: the MAPE-K transport measurement starts here.
    feed_rising_wall_clock = now_iso()
    feed_rising_monotonic = time.monotonic()
    publish(client, config, "feed_sensor", True, cycle)

    # We schedule sensor falling/rising/falling by absolute offsets from feed rising.
    # This preserves the intended feed->swap travel time even if dwell overlaps.
    events: list[tuple[int, str, bool]] = [
        (config.sensor_dwell_ms, "feed_sensor", False),
        (actual_travel_ms, "swap_sensor", True),
        (actual_travel_ms + config.sensor_dwell_ms, "swap_sensor", False),
    ]
    for offset_ms, topic_name, value in sorted(events, key=lambda item: item[0]):
        if _STOP:
            break
        sleep_until(feed_rising_monotonic, offset_ms)
        publish(client, config, topic_name, value, cycle)

    if config.publish_motor_state:
        publish(client, config, "motor_forward", False, cycle)

    if config.publish_diagnostics:
        measured_cycle_until_end_ms = int((time.monotonic() - feed_rising_monotonic) * 1000)
        publish_json_topic(
            client,
            config,
            f"/{config.machine_id}/sim/cycle-end",
            {
                "machine_id": config.machine_id,
                "cycle": cycle,
                "ts": now_iso(),
                "feed_rising_ts": feed_rising_wall_clock,
                "expected_travel_ms": config.expected_travel_ms,
                "actual_travel_ms": actual_travel_ms,
                "actual_offset_ms": actual_offset_ms,
                "measured_cycle_until_end_ms": measured_cycle_until_end_ms,
            },
        )

    sleep_ms(config.cycle_gap_ms)


def create_mqtt_client(client_id: str) -> mqtt.Client:
    try:
        return mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)
    except AttributeError:
        return mqtt.Client(client_id=client_id)


def connect(config: SimulatorConfig) -> mqtt.Client:
    client_id = f"sim-{config.machine_id}-{random.randint(1000, 9999)}"
    client = create_mqtt_client(client_id)
    client.connect(config.broker, config.port, keepalive=30)
    client.loop_start()
    return client


def non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be >= 0")
    return parsed


def parse_args(argv: Iterable[str]) -> SimulatorConfig:
    parser = argparse.ArgumentParser(description="MQTT conveyor belt timing-offset simulator for the DT engine")
    parser.add_argument("--broker", default="localhost", help="MQTT broker host, default: localhost")
    parser.add_argument("--port", type=int, default=1883, help="MQTT broker port, default: 1883")
    parser.add_argument("--machine-id", default="1-1-conveyor", help="Machine id used in /<machine-id>/... topics")
    parser.add_argument("--qos", type=int, choices=[0, 1, 2], default=0, help="MQTT QoS, default: 0")
    parser.add_argument("--retain", action="store_true", help="Publish retained messages. Useful if the DT starts after the simulator.")
    parser.add_argument("--payload-format", choices=["raw", "json"], default="raw", help="Payload format, default: raw")
    parser.add_argument("--cycles", type=non_negative_int, default=0, help="Number of cycles; 0 means endless, default: 0")
    parser.add_argument("--expected-travel-ms", type=non_negative_int, default=5000, help="Expected feed-to-swap travel time, default: 5000")
    parser.add_argument("--offset-ms", type=int, default=0, help="Static offset added to expected travel time")
    parser.add_argument(
        "--offset-pattern-ms",
        type=parse_int_list,
        default=[],
        help="Comma-separated offsets per cycle, e.g. 0,0,5000,12000,50000. Overrides --offset-ms.",
    )
    parser.add_argument("--jitter-ms", type=non_negative_int, default=50, help="Random timing jitter +/-, default: 50")
    parser.add_argument(
        "--sensor-dwell-ms",
        type=non_negative_int,
        default=1500,
        help="How long sensors stay true. Default 1500 ms so the DT polling cycle can see it.",
    )
    parser.add_argument("--cycle-gap-ms", type=non_negative_int, default=1500, help="Delay between cycles")
    parser.add_argument("--pulse-ms", type=non_negative_int, default=200, help="Pulse button true duration")
    parser.add_argument("--initial-delay-ms", type=non_negative_int, default=1500, help="Delay after publishing initial false state")
    parser.add_argument("--publish-motor-state", action="store_true", help="Also publish simulated motor forward/backward topics")
    parser.add_argument("--publish-diagnostics", action="store_true", help="Publish /<machine-id>/sim/... JSON diagnostic topics")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for repeatable jitter")

    args = parser.parse_args(list(argv))
    return SimulatorConfig(
        broker=args.broker,
        port=args.port,
        machine_id=args.machine_id.strip("/"),
        qos=args.qos,
        retain=args.retain,
        payload_format=args.payload_format,
        cycles=args.cycles,
        expected_travel_ms=args.expected_travel_ms,
        offset_ms=args.offset_ms,
        offset_pattern_ms=args.offset_pattern_ms,
        jitter_ms=args.jitter_ms,
        sensor_dwell_ms=args.sensor_dwell_ms,
        cycle_gap_ms=args.cycle_gap_ms,
        pulse_ms=args.pulse_ms,
        initial_delay_ms=args.initial_delay_ms,
        publish_motor_state=args.publish_motor_state,
        publish_diagnostics=args.publish_diagnostics,
        seed=args.seed,
    )


def main(argv: Iterable[str] = sys.argv[1:]) -> int:
    global _STOP
    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)

    config = parse_args(argv)
    if config.seed is not None:
        random.seed(config.seed)

    print("Starting MQTT conveyor simulator for DT engine")
    print(f"Broker: {config.broker}:{config.port}")
    print(f"Machine: {config.machine_id}")
    print(f"Expected travel: {config.expected_travel_ms} ms")
    print(f"Offset pattern: {config.offset_pattern_ms or [config.offset_ms]} ms")
    print(f"Sensor dwell: {config.sensor_dwell_ms} ms")
    print(f"Jitter: +/-{config.jitter_ms} ms")
    print("Press Ctrl+C to stop.\n")

    client = connect(config)
    try:
        publish_initial_state(client, config)
        sleep_ms(config.initial_delay_ms)
        cycle = 1
        while not _STOP and (config.cycles == 0 or cycle <= config.cycles):
            run_cycle(client, config, cycle)
            cycle += 1
    finally:
        print("\nStopping simulator and resetting topics...")
        try:
            publish_initial_state(client, config)
        finally:
            client.loop_stop()
            client.disconnect()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
