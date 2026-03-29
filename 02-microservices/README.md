# 02 — Microservices

Second submodule of the [pekko-playground](../README.md) project.

Demonstrates a microservice architecture split into a **frontend** (API Gateway)
and a **backend** (two independent Pekko clusters). The only externally accessible
entry point is FrontendService — the backend is internal.

---

## Architecture

```
                         [External Client]
                                │
                          port 8080
                                │
                    ┌───────────▼───────────┐
                    │    FrontendService    │  ← stateless, no cluster
                    │    (API Gateway)      │     can scale freely
                    └───────────┬───────────┘
                                │ HTTP (internal)
              ┌─────────────────┴──────────────────┐
              │                                    │
       port 8081                             port 8082
              │                                    │
┌─────────────▼────────────┐      ┌───────────────▼────────────┐
│   UserService Cluster    │◄─────│   OrderService Cluster     │
│   (backend)              │ HTTP │   (backend)                │
│                          │      │                            │
│  ShardRegion[UserEntity] │      │  ShardRegion[OrderEntity]  │
│  cluster: :25510         │      │  cluster: :25520           │
│  management: :8558       │      │  management: :8568         │
└──────────────────────────┘      └────────────────────────────┘
```

**Key observations:**
- Frontend does not belong to any Pekko cluster (`provider = local`)
- Backend services are not exposed externally — only accessible internally
- OrderService calls UserService directly (backend-to-backend) when verifying a user

---

## Services

### FrontendService (`com.example.frontend`)
Stateless API Gateway. Accepts all external requests and forwards them
to the appropriate backend service by rewriting the URI.

| Endpoint | Forwards to |
|---|---|
| `POST /users/{id}` | UserService |
| `GET  /users/{id}` | UserService |
| `POST /orders`     | OrderService |
| `GET  /orders/{id}`| OrderService |

### UserService (`com.example.backend.users`)
Manages user profiles. Internal Pekko cluster with Cluster Sharding.

| Endpoint | Description |
|---|---|
| `POST /users/{id}` | Registers or updates a user |
| `GET  /users/{id}` | Retrieves a user profile |

### OrderService (`com.example.backend.orders`)
Accepts orders. Before persisting an order it verifies the user exists
by calling UserService over HTTP (backend-to-backend).

| Endpoint | Description |
|---|---|
| `POST /orders`      | Creates an order (verifies user) |
| `GET  /orders/{id}` | Retrieves an order |

---

## Flow: placing an order

```
External Client
  │
  │ POST :8080/orders {"userId": "jan", "items": ["shoe"]}
  ▼
FrontendService                     ← rewrites URI and forwards
  │
  │ POST :8082/orders (internal)
  ▼
OrderService
  │
  │ GET :8081/users/jan             ← backend-to-backend verification
  ▼
UserService
  │ → 200 OK  (user exists)
  │ → 404     (user not found → OrderService returns 400)
  ▼
OrderService
  │
  │ ask → OrderEntity("a1b2c3d4")  ← state stored in sharded actor
  ▼
Client ← 201 Created {"orderId": "a1b2c3d4", "status": "placed", ...}
```

---

## Running Locally

Start services in order: backend first, frontend last.

### Terminal 1 — UserService (backend)

```bash
sbt "microservices/runMain com.example.backend.users.UserServiceApp"
```

### Terminal 2 — OrderService (backend)

```bash
sbt "microservices/runMain com.example.backend.orders.OrderServiceApp"
```

### Terminal 3 — FrontendService

```bash
sbt "microservices/runMain com.example.frontend.FrontendApp"
```

### Terminal 4 — testing (everything through port 8080)

```bash
# 1. Register a user
curl -s -X POST http://localhost:8080/users/jan \
  -H "Content-Type: application/json" \
  -d '{"name": "Jan Kowalski", "email": "jan@example.com"}' | jq .

# 2. Get profile
curl -s http://localhost:8080/users/jan | jq .

# 3. Place an order
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": "jan", "items": ["shoe-42", "hat-L"]}' | jq .

# 4. Get order (use orderId from previous response)
curl -s http://localhost:8080/orders/<orderId> | jq .

# 5. Try placing an order without registering first (→ 400)
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": "ghost", "items": ["x"]}' | jq .
```

---

## Cluster State

```bash
# UserService
curl http://localhost:8558/cluster/members | jq .

# OrderService
curl http://localhost:8568/cluster/members | jq .

# Frontend has no management HTTP — it is stateless
```

---

## Code Structure

```
02-microservices/
└── src/main/
    ├── scala/com/example/
    │   ├── CborSerializable.scala              # Marker trait for cluster messages
    │   ├── frontend/
    │   │   ├── FrontendRoutes.scala            # Proxy: rewrites URI and forwards request
    │   │   └── FrontendApp.scala               # Main: stateless, provider=local, port 8080
    │   └── backend/
    │       ├── users/
    │       │   ├── UserEntity.scala            # Sharded user entity
    │       │   ├── UserRoutes.scala            # HTTP routes (GET/POST /users/{id})
    │       │   └── UserServiceApp.scala        # Main: cluster + HTTP, port 8081
    │       └── orders/
    │           ├── OrderEntity.scala           # Sharded order entity
    │           ├── OrderRoutes.scala           # HTTP routes + UserService verification
    │           └── OrderServiceApp.scala       # Main: cluster + HTTP, port 8082
    └── resources/
        ├── frontend.conf                       # provider=local, port 8080
        ├── user-service.conf                   # cluster on ports 25510 / 8081
        ├── order-service.conf                  # cluster on ports 25520 / 8082
        └── logback.xml
```

---

## Why separate clusters instead of roles?

See the discussion in [01-cluster-basics](../01-cluster-basics/README.md).

In short: UserService and OrderService are independent business domains —
they deploy, scale and restart independently. FrontendService is
stateless and scales horizontally with no cluster coordination.

---

## Learning Checkpoints

- [ ] Start all 3 services and observe backend cluster formation
- [ ] Execute the full sequence via frontend: register → place order → get order
- [ ] Try placing an order for a non-existent user — observe 400
- [ ] Stop UserService — observe 503 when placing orders
- [ ] Restart UserService — orders work again
- [ ] Call the backend directly on :8081 and :8082 — compare with :8080
- [ ] Verify that frontend does not appear in `/cluster/members` of the backends

---

## Deploying to Minikube (with Grafana + logs)

### Requirements

```bash
minikube start --cpus 4 --memory 6g
# + kubectl, helm, docker in PATH
```

### One-shot deployment

```bash
# From pekko-playground/
cd 02-microservices
./scripts/deploy.sh
```

The script:
1. Builds the Docker image inside Minikube (no external registry needed)
2. Deploys namespace, RBAC, UserService (3 replicas), OrderService (3 replicas), Frontend
3. Installs `kube-prometheus-stack` (Grafana + Prometheus) via Helm
4. Installs Loki + Promtail (log aggregation)
5. Adds Loki datasource to Grafana

### After deployment

```
Frontend API:  http://<minikube-ip>:30080
Grafana:       http://<minikube-ip>:30300   (admin / admin)
```

Minikube address: `minikube ip`

### Generating traffic

```bash
FRONTEND_URL=http://$(minikube ip):30080 ./scripts/load-generator.sh
```

The script registers 50 users, places ~150 orders, then continuously
queries random entities — triggering shard creation and entity activation
across different nodes.

### Observing sharding

#### Shard distribution in the UserService cluster

```bash
kubectl -n pekko-demo exec -it deploy/user-service -- \
  curl -s localhost:8558/cluster/shards/User | python3 -m json.tool
```

The output shows which shards are on which nodes and how many entities each contains.

#### Cluster state

```bash
# UserService
kubectl -n pekko-demo exec -it deploy/user-service -- \
  curl -s localhost:8558/cluster/members | python3 -m json.tool

# OrderService
kubectl -n pekko-demo exec -it deploy/order-service -- \
  curl -s localhost:8568/cluster/members | python3 -m json.tool
```

#### Logs in Grafana / Loki

In Grafana (Explore → Loki):

| What to look for | LogQL query |
|---|---|
| Cluster formation | `{namespace="pekko-demo"} \|= "ClusterBootstrap"` |
| Shard creation | `{namespace="pekko-demo"} \|= "Starting shard"` |
| User entities | `{namespace="pekko-demo", app="user-service"} \|= "user-"` |
| Rebalancing | `{namespace="pekko-demo"} \|= "Rebalance"` |
| Orders | `{namespace="pekko-demo", app="order-service"}` |

#### Observing rebalancing

Change the replica count — Pekko automatically redistributes shards:

```bash
# Scale to 5 replicas — watch shard rebalancing
kubectl -n pekko-demo scale deploy/user-service --replicas=5

# Scale back to 2 — watch consolidation
kubectl -n pekko-demo scale deploy/user-service --replicas=2
```

After each change query `/cluster/shards/User` and watch the logs in Grafana.

### Docker architecture

One image (`pekko-microservices:latest`) runs all three services.
The `SERVICE_NAME` environment variable in the pod spec selects which service to start:

```
SERVICE_NAME=user-service  → UserServiceApp  + CONFIG_RESOURCE=kubernetes-user-service
SERVICE_NAME=order-service → OrderServiceApp + CONFIG_RESOURCE=kubernetes-order-service
SERVICE_NAME=frontend      → FrontendApp     + CONFIG_RESOURCE=kubernetes-frontend
```

Kubernetes configs (`kubernetes-*.conf`) extend the local configs via `include`
and override only what differs in K8s:
- `pekko.remote.artery.canonical.hostname = ${POD_IP}` — pod IP instead of 127.0.0.1
- `pekko.cluster.seed-nodes = []` — empty list activates Cluster Bootstrap
- `pekko.discovery.method = kubernetes-api` — discovery via K8s API

### K8s file structure

```
02-microservices/
├── Dockerfile                          # Multi-stage build (sbt builder + JRE runtime)
├── entrypoint.sh                       # SERVICE_NAME dispatcher
├── k8s/
│   ├── namespace.yaml                  # namespace: pekko-demo
│   ├── rbac.yaml                       # ServiceAccount + Role for listing pods
│   ├── user-service.yaml               # Service (8081) + Deployment (3 replicas)
│   ├── order-service.yaml              # Service (8082) + Deployment (3 replicas)
│   └── frontend.yaml                   # NodePort Service (30080) + Deployment
├── scripts/
│   ├── deploy.sh                       # One-shot Minikube deployment
│   └── load-generator.sh              # Traffic generator (50 users, ~150 orders)
└── src/main/resources/
    ├── kubernetes-user-service.conf    # K8s overrides: POD_IP, bootstrap, discovery
    ├── kubernetes-order-service.conf   # K8s overrides + user-service DNS name
    └── kubernetes-frontend.conf       # K8s overrides: backend DNS names
```
