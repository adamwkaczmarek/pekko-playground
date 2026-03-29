# Pekko Playground

A collection of examples for learning [Apache Pekko](https://pekko.apache.org/) Cluster.
Each example is a separate SBT submodule with its own code, configuration, and run instructions.

---

## Submodules

| Submodule | Description |
|---|---|
| [01-cluster-basics](01-cluster-basics/README.md) | Cluster formation, Cluster Sharding, Split Brain Resolver, Management HTTP |
| [02-microservices](02-microservices/README.md)   | Two independent clusters (UserService + OrderService) communicating over HTTP |

---

## Requirements

- JDK 17+
- sbt 1.9+
- Docker (optional, for Minikube deployment)

---

## Running

```bash
# Compile all submodules
sbt compile

# Run a specific submodule
sbt "01-cluster-basics/run"
```

Detailed run instructions are in each submodule's README.
