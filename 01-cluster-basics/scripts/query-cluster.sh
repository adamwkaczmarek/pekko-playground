#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Query the cluster state via Pekko Management HTTP API.
# Defaults to node 1 (port 8558). Pass a port as the first argument to query
# a different node.
#
# Usage:
#   ./scripts/query-cluster.sh             # query node 1
#   ./scripts/query-cluster.sh 8559        # query node 2
#   ./scripts/query-cluster.sh 8558 health # readiness probe
# ---------------------------------------------------------------------------
PORT="${1:-8558}"
CMD="${2:-members}"

BASE="http://localhost:$PORT"

case "$CMD" in
  members)
    echo "=== Cluster members (port $PORT) ==="
    curl -s "$BASE/cluster/members" | python3 -m json.tool 2>/dev/null || \
    curl -s "$BASE/cluster/members"
    ;;
  health)
    echo "=== Health check (port $PORT) ==="
    echo -n "ready:  "; curl -so /dev/null -w "%{http_code}\n" "$BASE/health/ready"
    echo -n "alive:  "; curl -so /dev/null -w "%{http_code}\n" "$BASE/health/alive"
    ;;
  *)
    echo "Unknown command: $CMD (members|health)"
    exit 1
    ;;
esac
