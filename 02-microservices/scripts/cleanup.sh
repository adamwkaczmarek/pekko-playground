#!/usr/bin/env bash
# =============================================================================
# cleanup.sh — remove everything deployed by deploy.sh
# =============================================================================
set -euo pipefail

info() { echo "[INFO]  $*"; }

info "Uninstalling Helm releases..."
helm uninstall promtail          -n monitoring 2>/dev/null || true
helm uninstall loki              -n monitoring 2>/dev/null || true
helm uninstall kube-prometheus-stack -n monitoring 2>/dev/null || true

info "Deleting namespaces (cascades to all resources inside)..."
kubectl delete namespace pekko-demo  --ignore-not-found
kubectl delete namespace monitoring  --ignore-not-found

info "Removing Docker image from Minikube..."
eval "$(minikube docker-env)"
docker rmi pekko-microservices:latest 2>/dev/null || true

info "Done."
