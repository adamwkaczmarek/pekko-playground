#!/usr/bin/env bash
# =============================================================================
# deploy.sh — one-shot Minikube deployment for 02-microservices
#
# Prerequisites:
#   minikube start --cpus 4 --memory 6g
#   kubectl, helm, docker must be in PATH
#
# What it does:
#   1. Builds the Docker image inside Minikube's daemon (no registry needed)
#   2. Deploys namespace, RBAC, and all three services
#   3. Installs kube-prometheus-stack (Grafana + Prometheus)
#   4. Installs Loki + Promtail (log aggregation)
#   5. Patches Grafana with a Loki datasource
#   6. Prints access URLs
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
K8S_DIR="${SCRIPT_DIR}/../k8s"
NAMESPACE="pekko-demo"
IMAGE="pekko-microservices:latest"

# ── helpers ───────────────────────────────────────────────────────────────────
info()  { echo "[INFO]  $*"; }
error() { echo "[ERROR] $*" >&2; exit 1; }

require_cmd() {
  command -v "$1" &>/dev/null || error "'$1' not found — please install it first."
}

# ── preflight ─────────────────────────────────────────────────────────────────
require_cmd minikube
require_cmd kubectl
require_cmd helm
require_cmd docker

info "Checking Minikube is running..."
minikube status | grep -q "Running" || error "Minikube is not running. Start it with: minikube start --cpus 4 --memory 6g"

# ── 1. Build Docker image inside Minikube ─────────────────────────────────────
info "Pointing Docker daemon to Minikube..."
eval "$(minikube docker-env)"

info "Building ${IMAGE} (context: ${ROOT_DIR})..."
docker build \
  -f "${ROOT_DIR}/02-microservices/Dockerfile" \
  -t "${IMAGE}" \
  "${ROOT_DIR}"

# ── 2. Apply Kubernetes manifests ─────────────────────────────────────────────
info "Applying namespace and RBAC..."
kubectl apply -f "${K8S_DIR}/namespace.yaml"
kubectl apply -f "${K8S_DIR}/rbac.yaml"

info "Deploying UserService..."
kubectl apply -f "${K8S_DIR}/user-service.yaml"

info "Deploying OrderService..."
kubectl apply -f "${K8S_DIR}/order-service.yaml"

info "Deploying Frontend..."
kubectl apply -f "${K8S_DIR}/frontend.yaml"

# PodMonitors tell the Prometheus Operator which pods/ports to scrape.
# Must be applied AFTER kube-prometheus-stack installs the CRD, so we apply
# them again after the Helm install below — this line is just a no-op on first run.
kubectl apply -f "${K8S_DIR}/podmonitors.yaml" 2>/dev/null || true

# ── 3. Install kube-prometheus-stack (Grafana + Prometheus) ───────────────────
info "Adding Helm repos..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana              https://grafana.github.io/helm-charts
helm repo update

info "Installing kube-prometheus-stack..."
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=admin \
  --set grafana.service.type=NodePort \
  --set grafana.service.nodePort=30300 \
  --set prometheus.service.type=NodePort \
  --set prometheus.service.nodePort=30090 \
  --set prometheus.prometheusSpec.enableRemoteWriteReceiver=true \
  --wait --timeout 5m

# ── 4. Install Loki + Promtail (loki-stack — dev-friendly single-binary) ──────
# The standalone grafana/loki chart (v3+) requires an object storage backend
# (S3/GCS/etc.) even in SingleBinary mode unless a full schema config is provided.
# loki-stack bundles Loki 2.x + Promtail and is designed for local/dev use.
info "Installing loki-stack (Loki + Promtail)..."
helm upgrade --install loki grafana/loki-stack \
  --namespace monitoring \
  --set loki.enabled=true \
  --set grafana.enabled=false \
  --set prometheus.enabled=false \
  --set promtail.enabled=true \
  --wait --timeout 5m

# ── 5. Add Loki datasource to Grafana ─────────────────────────────────────────
info "Waiting for Grafana to be ready..."
kubectl -n monitoring rollout status deploy/kube-prometheus-stack-grafana --timeout=120s

GRAFANA_URL="http://$(minikube ip):30300"

info "Adding Loki datasource to Grafana..."
curl -sf -X POST "${GRAFANA_URL}/api/datasources" \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "name":      "Loki",
    "type":      "loki",
    "url":       "http://loki.monitoring.svc.cluster.local:3100",
    "access":    "proxy",
    "isDefault": false
  }' | grep -q '"id"' && info "Loki datasource added." \
  || info "Loki datasource may already exist — skipping."

# ── 6. Apply PodMonitors + Grafana dashboards ────────────────────────────────
# PodMonitors CRD is now guaranteed to exist (installed by kube-prometheus-stack).
info "Applying PodMonitors (Prometheus Operator scrape config)..."
kubectl apply -f "${K8S_DIR}/podmonitors.yaml"

# ConfigMaps labelled grafana_dashboard=1 are picked up automatically by the
# kube-prometheus-stack sidecar and imported into Grafana.
info "Applying Grafana dashboards..."
kubectl apply -f "${K8S_DIR}/grafana-dashboards.yaml"

# ── 7. Wait for app pods ──────────────────────────────────────────────────────
info "Waiting for UserService pods..."
kubectl -n "${NAMESPACE}" rollout status deploy/user-service  --timeout=3m
info "Waiting for OrderService pods..."
kubectl -n "${NAMESPACE}" rollout status deploy/order-service --timeout=3m
info "Waiting for Frontend pod..."
kubectl -n "${NAMESPACE}" rollout status deploy/frontend      --timeout=2m

# ── 8. Print access info ──────────────────────────────────────────────────────
MINIKUBE_IP="$(minikube ip)"
FRONTEND_URL="http://${MINIKUBE_IP}:30080"
GRAFANA_FULL="http://${MINIKUBE_IP}:30300"

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Deployment complete!                                        ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Frontend (API):  ${FRONTEND_URL}                           "
echo "║  Grafana:         ${GRAFANA_FULL}  (admin / admin)          "
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Cluster state:                                              ║"
echo "║    kubectl -n ${NAMESPACE} exec -it deploy/user-service \\   "
echo "║      -- curl -s localhost:8558/cluster/members | python3 -m json.tool"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Shard allocation:                                           ║"
echo "║    kubectl -n ${NAMESPACE} exec -it deploy/user-service \\   "
echo "║      -- curl -s localhost:8558/cluster/shards/User          "
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  k6 load test (install: brew install k6 / apt install k6):    ║"
echo "║    K6_PROMETHEUS_RW_SERVER_URL=http://${MINIKUBE_IP}:30090/api/v1/write \\"
echo "║    K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=false \\       "
echo "║    K6_PROMETHEUS_RW_TREND_STATS=p(50),p(90),p(95),p(99),min,max,avg \\"
echo "║      k6 run -o experimental-prometheus-rw \\                 "
echo "║      -e FRONTEND_URL=${FRONTEND_URL} k6/load-test.js        "
echo "║  Prometheus:     http://${MINIKUBE_IP}:30090                 "
echo "╚══════════════════════════════════════════════════════════════╝"
