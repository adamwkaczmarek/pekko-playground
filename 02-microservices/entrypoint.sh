#!/bin/bash
# =============================================================================
# Container entry point — selects main class based on SERVICE_NAME env var.
#
# Required env vars:
#   SERVICE_NAME    — one of: user-service, order-service, frontend
#
# Optional env vars (passed through to the JVM/config):
#   CONFIG_RESOURCE — config file name without .conf suffix
#                     defaults to <service-name without hyphen mapping> if unset
#   POD_IP          — set automatically by Kubernetes downwardAPI
#   JAVA_OPTS       — JVM flags (base flags already set in Dockerfile ENV)
# =============================================================================
set -eu

case "${SERVICE_NAME:?SERVICE_NAME env var is required}" in
  user-service)
    MAIN="com.example.backend.users.UserServiceApp"
    export CONFIG_RESOURCE="${CONFIG_RESOURCE:-user-service}"
    ;;
  order-service)
    MAIN="com.example.backend.orders.OrderServiceApp"
    export CONFIG_RESOURCE="${CONFIG_RESOURCE:-order-service}"
    ;;
  frontend)
    MAIN="com.example.frontend.FrontendApp"
    export CONFIG_RESOURCE="${CONFIG_RESOURCE:-frontend}"
    ;;
  *)
    echo "ERROR: Unknown SERVICE_NAME='${SERVICE_NAME}'. Valid values: user-service, order-service, frontend"
    exit 1
    ;;
esac

echo "Starting ${SERVICE_NAME} (main: ${MAIN}, config: ${CONFIG_RESOURCE})"

# Append JMX exporter agent — exposes JVM metrics on port 9010 for Prometheus
JVM_AGENT="-javaagent:/app/jmx-exporter.jar=9010:/app/jmx-config.yaml"

# shellcheck disable=SC2086
exec java $JAVA_OPTS $JVM_AGENT -cp "/app/lib/*" "$MAIN"
