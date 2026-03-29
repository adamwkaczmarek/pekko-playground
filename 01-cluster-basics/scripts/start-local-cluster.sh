#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Starts a 3-node Pekko cluster on localhost.
#
# Each node runs in its own background process and writes logs to
#   logs/node-{1,2,3}.log
#
# Usage:
#   ./scripts/start-local-cluster.sh        # start the cluster
#   ./scripts/start-local-cluster.sh stop   # kill all nodes
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_FILE="$ROOT_DIR/.cluster-pids"

stop_cluster() {
  if [[ -f "$PID_FILE" ]]; then
    echo "Stopping cluster nodes..."
    while IFS= read -r pid; do
      kill "$pid" 2>/dev/null && echo "  killed PID $pid" || true
    done < "$PID_FILE"
    rm -f "$PID_FILE"
  else
    echo "No running cluster found (no .cluster-pids file)."
  fi
  exit 0
}

[[ "${1:-}" == "stop" ]] && stop_cluster

mkdir -p "$LOG_DIR"
cd "$ROOT_DIR"

echo "=== Building project... ==="
sbt compile

start_node() {
  local n=$1
  local artery_port=$((25519 + n))
  local mgmt_port=$((8557 + n))
  local log="$LOG_DIR/node-$n.log"

  echo "Starting node $n  artery=:$artery_port  management=http://localhost:$mgmt_port"

  # build.sbt sets javaOptions + run/fork=true so sbt passes --add-opens to the
  # forked JVM automatically.  No need to repeat them here.
  sbt \
    -Dpekko.remote.artery.canonical.port="$artery_port" \
    -Dpekko.management.http.port="$mgmt_port" \
    run \
    > "$log" 2>&1 &

  echo $! >> "$PID_FILE"
  echo "  PID=$!  log=$log"
}

rm -f "$PID_FILE"

# Node 1 is the first seed — start it and wait a moment before the others
# so there is a stable seed to join.
start_node 1
echo "Waiting 8s for seed node to start..."
sleep 8

start_node 2
sleep 3

start_node 3

echo ""
echo "=== Cluster started ==="
echo "  Logs:  tail -f $LOG_DIR/node-*.log"
echo "  State: curl http://localhost:8558/cluster/members | jq ."
echo "  Stop:  $0 stop"
echo ""

# Tail all three log files so you see interleaved output
tail -f "$LOG_DIR"/node-*.log
