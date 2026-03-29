# =============================================================================
# 02-microservices — multi-stage Docker build
#
# Build context: project root (pekko-playground/)
# Build:  docker build -f 02-microservices/Dockerfile -t pekko-microservices:latest .
#
# The same image runs all three services — SERVICE_NAME env var selects which:
#   user-service  →  com.example.backend.users.UserServiceApp
#   order-service →  com.example.backend.orders.OrderServiceApp
#   frontend      →  com.example.frontend.FrontendApp
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# Install sbt
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl gnupg && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" \
        > /etc/apt/sources.list.d/sbt.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" \
        | apt-key add - && \
    apt-get update && \
    apt-get install -y --no-install-recommends sbt && \
    rm -rf /var/lib/apt/lists/*

# Copy only build descriptor files first — Docker caches this layer
# so repeated builds skip the slow dependency-download step.
COPY project/ project/
COPY build.sbt .

# Pre-fetch all dependencies (this layer is cached unless build.sbt changes)
RUN sbt microservices/update

# Now copy source and compile
COPY 02-microservices/src/ 02-microservices/src/
RUN sbt "microservices/stage"

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy only the JAR library directory (no generated start scripts — we use entrypoint.sh)
COPY --from=builder /build/02-microservices/target/universal/stage/lib/ lib/
COPY 02-microservices/entrypoint.sh  entrypoint.sh
COPY 02-microservices/jmx-config.yaml jmx-config.yaml
RUN chmod +x entrypoint.sh

# JMX Prometheus exporter — exposes JVM metrics on port 9010
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    curl -fL "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar" \
        -o jmx-exporter.jar && \
    rm -rf /var/lib/apt/lists/*

# Java 17 module opens required by Pekko's reflection and serialization.
# JMX exporter agent is appended by entrypoint.sh at runtime.
ENV JAVA_OPTS="\
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"

# Documented ports (actual binding depends on SERVICE_NAME)
# UserService:   HTTP 8081 | Management 8558 | Artery 25510 | JMX 9010
# OrderService:  HTTP 8082 | Management 8568 | Artery 25520 | JMX 9010
# Frontend:      HTTP 8080                                  | JMX 9010
EXPOSE 8080 8081 8082 8558 8568 9010 25510 25520

ENTRYPOINT ["./entrypoint.sh"]
