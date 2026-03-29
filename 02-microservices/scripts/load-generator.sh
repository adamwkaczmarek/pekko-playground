#!/usr/bin/env bash
# =============================================================================
# load-generator.sh — generate realistic load to make sharding visible
#
# Usage:
#   FRONTEND_URL=http://$(minikube ip):30080 ./scripts/load-generator.sh
#
# What it does:
#   1. Registers 50 users (spread across shards)
#   2. Places 2-5 orders per user
#   3. Continuously polls random users and orders (keeps actors alive)
#
# Observe results in:
#   - Grafana / Loki: search for "Started shard" or "entityId" log lines
#   - Cluster shard state:
#       kubectl -n pekko-demo exec -it deploy/user-service -- \
#         curl -s localhost:8558/cluster/shards/User | python3 -m json.tool
# =============================================================================
set -euo pipefail

BASE_URL="${FRONTEND_URL:-http://localhost:8080}"
NUM_USERS="${NUM_USERS:-50}"
ORDERS_PER_USER_MIN=2
ORDERS_PER_USER_MAX=5

ITEMS=("laptop" "phone" "tablet" "headphones" "keyboard" "mouse" "monitor" "camera" "speaker" "watch")

info()  { echo "[$(date '+%H:%M:%S')] $*"; }
random_item() { echo "${ITEMS[$((RANDOM % ${#ITEMS[@]}))]}"; }
random_between() { echo $(( RANDOM % ($2 - $1 + 1) + $1 )); }

# ── Phase 1: Register users ───────────────────────────────────────────────────
info "Registering ${NUM_USERS} users..."
declare -a REGISTERED_USERS

for i in $(seq 1 "$NUM_USERS"); do
  USER_ID="user-$(printf '%04d' "$i")"
  HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
    -X POST "${BASE_URL}/users/${USER_ID}" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"User ${i}\", \"email\": \"user${i}@example.com\"}" \
    || echo "000")

  if [[ "$HTTP_STATUS" == "201" ]]; then
    REGISTERED_USERS+=("$USER_ID")
    [[ $((i % 10)) -eq 0 ]] && info "Registered ${i}/${NUM_USERS} users..."
  else
    info "WARN: user ${USER_ID} returned HTTP ${HTTP_STATUS}"
  fi
  # Small pause to avoid overwhelming the cluster while it's still forming
  sleep 0.05
done

info "Successfully registered ${#REGISTERED_USERS[@]} users."

# ── Phase 2: Place orders ─────────────────────────────────────────────────────
info "Placing orders (${ORDERS_PER_USER_MIN}-${ORDERS_PER_USER_MAX} per user)..."
declare -a ORDER_IDS
TOTAL_ORDERS=0

for USER_ID in "${REGISTERED_USERS[@]}"; do
  NUM_ORDERS=$(random_between $ORDERS_PER_USER_MIN $ORDERS_PER_USER_MAX)
  for _ in $(seq 1 "$NUM_ORDERS"); do
    ITEM1=$(random_item)
    ITEM2=$(random_item)
    RESPONSE=$(curl -sf \
      -X POST "${BASE_URL}/orders" \
      -H "Content-Type: application/json" \
      -d "{\"userId\": \"${USER_ID}\", \"items\": [\"${ITEM1}\", \"${ITEM2}\"]}" \
      || echo '{}')

    ORDER_ID=$(echo "$RESPONSE" | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4 || true)
    if [[ -n "$ORDER_ID" ]]; then
      ORDER_IDS+=("$ORDER_ID")
      TOTAL_ORDERS=$((TOTAL_ORDERS + 1))
    fi
    sleep 0.02
  done
done

info "Placed ${TOTAL_ORDERS} orders."

# ── Phase 3: Continuous read polling ─────────────────────────────────────────
info "Starting continuous polling (Ctrl+C to stop)..."
info "Watch shard distribution with:"
info "  kubectl -n pekko-demo exec -it deploy/user-service -- curl -s localhost:8558/cluster/shards/User"
echo ""

ROUND=0
while true; do
  ROUND=$((ROUND + 1))

  # Poll 5 random users
  for _ in $(seq 1 5); do
    IDX=$(random_between 0 $((${#REGISTERED_USERS[@]} - 1)))
    USER="${REGISTERED_USERS[$IDX]}"
    curl -sf "${BASE_URL}/users/${USER}" -o /dev/null || true
  done

  # Poll 3 random orders
  if [[ ${#ORDER_IDS[@]} -gt 0 ]]; then
    for _ in $(seq 1 3); do
      IDX=$(random_between 0 $((${#ORDER_IDS[@]} - 1)))
      ORDER="${ORDER_IDS[$IDX]}"
      curl -sf "${BASE_URL}/orders/${ORDER}" -o /dev/null || true
    done
  fi

  # Every 10 rounds, place a new order to create fresh entities
  if [[ $((ROUND % 10)) -eq 0 ]]; then
    IDX=$(random_between 0 $((${#REGISTERED_USERS[@]} - 1)))
    USER="${REGISTERED_USERS[$IDX]}"
    ITEM=$(random_item)
    curl -sf -X POST "${BASE_URL}/orders" \
      -H "Content-Type: application/json" \
      -d "{\"userId\": \"${USER}\", \"items\": [\"${ITEM}\"]}" \
      -o /dev/null || true
    info "Round ${ROUND} — new order placed for ${USER}"
  fi

  sleep 0.5
done
