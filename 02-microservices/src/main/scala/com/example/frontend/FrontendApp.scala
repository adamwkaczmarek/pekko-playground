package com.example.frontend

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.BootstrapSetup
import org.apache.pekko.actor.setup.ActorSystemSetup
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import fr.davit.pekko.http.metrics.core.HttpMetrics._
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives._
import fr.davit.pekko.http.metrics.prometheus.{PrometheusRegistry, PrometheusSettings}
import fr.davit.pekko.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.{concat, get, path}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Entry point for the FrontendService (API Gateway).
 *
 * This is a STATELESS service — no Pekko Cluster, no Sharding.
 * It only proxies HTTP requests to the appropriate backend service.
 * Multiple instances can run behind a load balancer without coordination.
 *
 * Start:
 *   sbt "microservices/runMain com.example.frontend.FrontendApp"
 *
 * External API (proxied to backends):
 *   POST /users/{id}   → UserService
 *   GET  /users/{id}   → UserService
 *   POST /orders       → OrderService
 *   GET  /orders/{id}  → OrderService
 */
object FrontendApp extends App {

  val configName = sys.env.getOrElse("CONFIG_RESOURCE", "frontend")
  val config = ConfigFactory.load(configName)

  // No cluster — provider is "local".
  // Frontend can be scaled horizontally by Kubernetes without any cluster coordination.
  implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](
    Behaviors.empty[Nothing],
    "FrontendSystem",
    ActorSystemSetup.create(BootstrapSetup().withConfig(config))
  )

  val metricsRegistry = PrometheusRegistry(settings = PrometheusSettings.default
    .withIncludeMethodDimension(true)
    .withIncludeStatusDimension(true)
  )

  val port = config.getInt("frontend.http.port")
  val routes = concat(
    path("metrics") {
      get {
        metrics(metricsRegistry)
      }
    },
    FrontendRoutes()
  )
  Http().newMeteredServerAt("0.0.0.0", port, metricsRegistry).bind(routes)
  system.log.info("Frontend listening on port {} (external, /metrics available)", port)

  sys.addShutdownHook {
    system.log.info("Frontend shutting down...")
    system.terminate()
  }

  Await.ready(system.whenTerminated, Duration.Inf)
}
