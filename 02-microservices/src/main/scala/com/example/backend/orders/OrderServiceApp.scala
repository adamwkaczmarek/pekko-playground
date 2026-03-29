package com.example.backend.orders

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.BootstrapSetup
import org.apache.pekko.actor.setup.ActorSystemSetup
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import com.example.backend.{ClusterMetricsListener, ShardStatsPoller}
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import fr.davit.pekko.http.metrics.core.HttpMetrics._
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives._
import fr.davit.pekko.http.metrics.prometheus.{PrometheusRegistry, PrometheusSettings}
import fr.davit.pekko.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.{concat, get, path}
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap
import org.apache.pekko.management.scaladsl.PekkoManagement

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Entry point for the OrderService cluster (backend).
 *
 * Internal service — not exposed directly to the outside world.
 * The FrontendService proxies external traffic to this service.
 * Calls UserService internally to verify users before placing orders.
 *
 * Start:
 *   sbt "microservices/runMain com.example.backend.orders.OrderServiceApp"
 */
object OrderServiceApp extends App {

  val configName = sys.env.getOrElse("CONFIG_RESOURCE", "order-service")
  val config = ConfigFactory.load(configName)

  implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { ctx =>
      ctx.spawn(ClusterMetricsListener(), "cluster-metrics-listener")
      ctx.spawn[Nothing](ShardStatsPoller("Order"), "shard-stats-poller")
      Behaviors.empty[Nothing]
    },
    "OrderService",
    ActorSystemSetup.create(BootstrapSetup().withConfig(config))
  )

  val sharding = ClusterSharding(system)
  sharding.init(Entity(OrderEntity.TypeKey)(ctx => OrderEntity(ctx.entityId)))

  PekkoManagement(system).start()

  val seedNodes = system.settings.config.getList("pekko.cluster.seed-nodes")
  if (seedNodes.isEmpty) {
    system.log.info("No static seed-nodes — starting Cluster Bootstrap (Kubernetes mode)")
    ClusterBootstrap(system).start()
  }

  val metricsRegistry = PrometheusRegistry(settings = PrometheusSettings.default
    .withIncludeMethodDimension(true)
    .withIncludeStatusDimension(true)
  )

  val port = config.getInt("order-service.http.port")
  val routes = concat(
    path("metrics") {
      get {
        metrics(metricsRegistry)
      }
    },
    OrderRoutes(sharding)
  )
  Http().newMeteredServerAt("0.0.0.0", port, metricsRegistry).bind(routes)
  system.log.info("OrderService listening on port {} (internal, /metrics available)", port)

  sys.addShutdownHook {
    system.log.info("OrderService shutting down...")
    system.terminate()
  }

  Await.ready(system.whenTerminated, Duration.Inf)
}
