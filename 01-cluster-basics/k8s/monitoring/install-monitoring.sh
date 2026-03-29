#!/usr/bin/env bash
# =============================================================================
# Install kube-prometheus-stack (Prometheus + Grafana + Alertmanager) into
# minikube and import the Pekko Cluster dashboard.
#
# Prerequisites:
#   minikube start
#   helm (https://helm.sh/docs/intro/install/)
#   kubectl
#
# Usage:
#   ./k8s/monitoring/install-monitoring.sh
# =============================================================================
set -euo pipefail

NAMESPACE="monitoring"
RELEASE="kube-prometheus-stack"
GRAFANA_PORT=3000
DASHBOARD_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/grafana-dashboard.json"

echo "=== Adding prometheus-community Helm repo ==="
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

echo ""
echo "=== Installing $RELEASE in namespace $NAMESPACE ==="
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install "$RELEASE" prometheus-community/kube-prometheus-stack \
  --namespace "$NAMESPACE" \
  --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword=admin \
  --wait --timeout 5m

echo ""
echo "=== Applying ServiceMonitor for Pekko ==="
kubectl apply -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../"  # apply whole k8s/ dir if not done

kubectl apply -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/servicemonitor.yaml"

echo ""
echo "=== Importing Grafana dashboard ==="
# Get Grafana pod name
GRAFANA_POD=$(kubectl -n "$NAMESPACE" get pod \
  -l "app.kubernetes.io/name=grafana" -o jsonpath='{.items[0].metadata.name}')

# Wait for Grafana to be ready
kubectl -n "$NAMESPACE" wait --for=condition=ready pod "$GRAFANA_POD" --timeout=120s

# Import dashboard via Grafana HTTP API
kubectl -n "$NAMESPACE" port-forward "$GRAFANA_POD" "$GRAFANA_PORT":3000 &
PF_PID=$!
sleep 3

curl -s -X POST \
  -H "Content-Type: application/json" \
  -u admin:admin \
  "http://localhost:$GRAFANA_PORT/api/dashboards/import" \
  -d "{\"dashboard\": $(cat "$DASHBOARD_FILE"), \"overwrite\": true, \"folderId\": 0}" \
  | python3 -m json.tool || true

kill $PF_PID 2>/dev/null || true

echo ""
echo "=== Done! ==="
echo ""
echo "Access Grafana:"
echo "  kubectl -n $NAMESPACE port-forward svc/${RELEASE}-grafana $GRAFANA_PORT:80"
echo "  open http://localhost:$GRAFANA_PORT  (admin / admin)"
echo ""
echo "Access Prometheus:"
echo "  kubectl -n $NAMESPACE port-forward svc/${RELEASE}-prometheus $((GRAFANA_PORT+1)):9090"
echo "  open http://localhost:$((GRAFANA_PORT+1))"
echo ""
echo "Access Pekko Management:"
echo "  kubectl -n pekko-demo port-forward svc/pekko-cluster 8558:8558"
echo "  curl http://localhost:8558/cluster/members | jq ."
