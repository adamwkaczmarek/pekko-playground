# 01 — Cluster Basics

First submodule of the [pekko-playground](../README.md) project.
Demonstrates the fundamentals of Pekko cluster formation, Cluster Sharding, Split Brain Resolver,
and exposing cluster state via Management HTTP.

---

## Contents

| File | Description |
|---|---|
| `src/.../ClusterListener.scala` | Subscribes to cluster events and logs every membership change |
| `src/.../Counter.scala` | Sharded counter entity — demonstrates work distribution across nodes |
| `src/.../ClusterApp.scala` | Entry point: starts ActorSystem, sharding, Management HTTP |
| `src/.../CborSerializable.scala` | Marker trait for messages that cross node boundaries |
| `application.conf` | Local configuration (static seed nodes) |
| `kubernetes.conf` | Kubernetes overlay (Cluster Bootstrap, POD_IP) |
| `k8s/` | Namespace, RBAC, Deployment, Services |
| `k8s/monitoring/` | ServiceMonitor, Grafana dashboard, install script |

---

## Key Concepts

### How a cluster forms

```
Node A starts → waits for seed nodes
Node B starts → contacts seed A → joins
Node C starts → contacts seed A → joins
                        │
              Cluster UP: {A, B, C}
```

Each node starts an ActorSystem named `ClusterSystem`.
Nodes communicate via **Artery TCP** (port 25520).
`ClusterListener` subscribes to the membership lifecycle:

```
Joining → WeaklyUp → Up → Leaving → Exiting → Removed
```

### Cluster Sharding

`Counter` is a **sharded entity**. There could potentially be millions of counters
(`counter-0`, `counter-1`, … `counter-N`), but only a handful of nodes.
Pekko assigns each entity to a **shard**, and each shard to a node:

```
counter-0  → shard-12 → Node B
counter-1  → shard-07 → Node A
counter-2  → shard-12 → Node B   (same shard as counter-0)
```

Any node can send `Counter.Increment(1)` to `counter-0` —
sharding routes the message transparently.
When Node B leaves the cluster, its shards rebalance to the remaining nodes.

### Split Brain Resolver (SBR)

A network partition can split the cluster:

```
Side A: {Node1, Node2}  ←— partition —→  Side B: {Node3}
```

Both sides think the other is dead. Without SBR both keep running — **split brain**.
We use `keep-majority`: side A (2 nodes) survives, side B (1 node) shuts down.

### Pekko Management HTTP

Each node exposes a REST API on port 8558:

```bash
# List all members and their status
curl http://localhost:8558/cluster/members | jq .

# Graceful leave for a node
curl -X PUT http://localhost:8558/cluster/members/pekko%3A%2F%2FClusterSystem%40127.0.0.1%3A25521

# Readiness / liveness (used by Kubernetes probes)
curl http://localhost:8558/health/ready
curl http://localhost:8558/health/alive
```

---

## Running Locally (3 nodes)

**Requirements:** JDK 17+, sbt 1.9+

### Option A — automated script

```bash
chmod +x scripts/*.sh
./scripts/start-local-cluster.sh
```

Logs from all three nodes are tailed live.
Stop with: `./scripts/start-local-cluster.sh stop`.

### Option B — three terminals

```bash
# Terminal 1 — first seed node (port 25520, management 8558)
sbt "01-cluster-basics/run" -Dpekko.remote.artery.canonical.port=25520

# Terminal 2 (wait ~10s for node 1)
sbt "01-cluster-basics/run" \
    -Dpekko.remote.artery.canonical.port=25521 \
    -Dpekko.management.http.port=8559

# Terminal 3
sbt "01-cluster-basics/run" \
    -Dpekko.remote.artery.canonical.port=25522 \
    -Dpekko.management.http.port=8560
```

### What to observe

1. **Node joining** — look for `★ Member UP` lines in `ClusterListener` logs on all nodes.
2. **Shard assignment** — the shard coordinator log shows which node is responsible for which shard.
3. **Counter distribution** — `CounterDriver` increments counters; log lines appear on different nodes depending on shard assignment.
4. **Kill a node** — `Ctrl+C` in one terminal. The remaining nodes log `⚡ UNREACHABLE`, then after the SBR window `✗ DOWNED` and `✗ REMOVED`. Shards rebalance automatically.

```bash
# Query cluster state from node 1
./scripts/query-cluster.sh
# or
curl http://localhost:8558/cluster/members | jq .
```

---

## Deploy to Minikube

### 1. Start Minikube

```bash
minikube start --cpus=4 --memory=4g
```

### 2. Build Docker image inside Minikube

```bash
eval $(minikube docker-env)
docker build -t pekko-cluster-basics:latest .
```

### 3. Apply Kubernetes manifests

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/rbac.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/deployment.yaml

# Watch pods starting up
kubectl -n pekko-demo get pods -w
```

### 4. Verify the cluster has formed

```bash
kubectl -n pekko-demo port-forward svc/pekko-cluster 8558:8558 &
curl http://localhost:8558/cluster/members | jq .
# Should show 3 nodes with status "Up"
```

### 5. Scaling

```bash
# Add two nodes — watch them join via ClusterListener logs
kubectl -n pekko-demo scale deployment pekko-cluster --replicas=5
kubectl -n pekko-demo logs -l app=pekko-cluster --follow
```

---

## Monitoring (Prometheus + Grafana)

```bash
chmod +x k8s/monitoring/install-monitoring.sh
./k8s/monitoring/install-monitoring.sh

# Access Grafana
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80
# http://localhost:3000  (admin / admin)
```

Import `k8s/monitoring/grafana-dashboard.json` (Dashboards → Import → Upload JSON).

---

## Module Structure

```
01-cluster-basics/
├── src/main/
│   ├── scala/com/example/
│   │   ├── CborSerializable.scala     # Marker trait for cluster messages
│   │   ├── ClusterListener.scala      # Cluster event subscriber (start here)
│   │   ├── Counter.scala              # Sharded counter entity
│   │   └── ClusterApp.scala           # Main + CounterDriver
│   └── resources/
│       ├── application.conf           # Local config (static seed nodes)
│       ├── kubernetes.conf            # K8s overlay (bootstrap discovery)
│       └── logback.xml
├── scripts/
│   ├── start-local-cluster.sh
│   └── query-cluster.sh
├── docker/
│   └── jmx-config.yaml               # JMX Prometheus Exporter rules
├── Dockerfile
└── k8s/
    ├── namespace.yaml
    ├── rbac.yaml                      # Permissions to list pods (bootstrap)
    ├── service.yaml                   # Headless + ClusterIP services
    ├── deployment.yaml                # Deployment with 3 replicas
    └── monitoring/
        ├── servicemonitor.yaml        # Prometheus scrape config
        ├── grafana-dashboard.json     # Ready-to-use Grafana dashboard
        └── install-monitoring.sh
```

---

## Learning Checkpoints

- [ ] Start 3 local nodes and observe cluster formation (`★ Member UP` in logs)
- [ ] Kill one node, observe the sequence `⚡ UNREACHABLE → ✗ DOWNED → ✗ REMOVED`
- [ ] Restart the killed node and observe it rejoining
- [ ] Query `GET /cluster/members` and understand the JSON structure
- [ ] Add `roles = ["special"]` to one node's config, observe it in the events
- [ ] Change `replicas: 5` in `deployment.yaml`, apply and observe cluster expansion
