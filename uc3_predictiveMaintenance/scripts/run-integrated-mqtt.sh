#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Starting integrated DT app with MQTT on http://localhost:8080"
gradle bootRun --args='--spring.profiles.active=integrated,mqtt'
