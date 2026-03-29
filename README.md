# Pekko Playground

Repozytorium z przykładami uczącymi [Apache Pekko](https://pekko.apache.org/) Cluster.
Każdy przykład to osobny podmoduł SBT z własnym kodem, konfiguracją i instrukcją uruchomienia.

---

## Podmoduły

| Podmoduł | Opis |
|---|---|
| [01-cluster-basics](01-cluster-basics/README.md) | Formowanie klastra, Cluster Sharding, Split Brain Resolver, Management HTTP |
| [02-microservices](02-microservices/README.md)   | Dwa niezależne klastry (UserService + OrderService) komunikujące się przez HTTP |

---

## Wymagania

- JDK 17+
- sbt 1.9+
- Docker (opcjonalnie, do deploymentu na Minikube)

---

## Uruchomienie

```bash
# Kompilacja wszystkich podmodułów
sbt compile

# Uruchomienie konkretnego podmodułu
sbt "01-cluster-basics/run"
```

Szczegółowe instrukcje uruchomienia znajdziesz w README każdego podmodułu.
