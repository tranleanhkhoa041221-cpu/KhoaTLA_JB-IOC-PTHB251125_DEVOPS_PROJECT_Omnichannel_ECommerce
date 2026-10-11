#!/usr/bin/env bash
# Tai OpenTelemetry Java agent ve /opt/quickbite/otel (chay tren VPS, can Internet).
set -euo pipefail
mkdir -p /opt/quickbite/otel
curl -fsSL -o /opt/quickbite/otel/opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
ls -lh /opt/quickbite/otel/opentelemetry-javaagent.jar
