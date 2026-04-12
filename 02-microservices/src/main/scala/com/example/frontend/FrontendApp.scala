package com.example.frontend

import cats.effect.std.Dispatcher
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.netty.cats.{NettyCatsServer, NettyCatsServerOptions}

/**
 * Entry point for the FrontendService (API Gateway).
 *
 * Stateless API gateway built on Tapir + Netty (cats-effect).
 * No Pekko ActorSystem, no Pekko HTTP — the frontend can be scaled horizontally
 * by Kubernetes without any cluster coordination.
 *
 * Start:
 *   sbt "microservices/runMain com.example.frontend.FrontendApp"
 *
 * External API (proxied to backends):
 *   POST /users/{id}   → UserService
 *   GET  /users/{id}   → UserService
 *   POST /orders       → OrderService
 *   GET  /orders/{id}  → OrderService
 *   GET  /metrics      → Prometheus scrape endpoint
 */
object FrontendApp extends IOApp {

  private val log = LoggerFactory.getLogger(getClass)

  override def run(args: List[String]): IO[ExitCode] = {
    val configName      = sys.env.getOrElse("CONFIG_RESOURCE", "frontend")
    val config          = ConfigFactory.load(configName)
    val port            = config.getInt("frontend.http.port")
    val userServiceUrl  = config.getString("user-service.base-url")
    val orderServiceUrl = config.getString("order-service.base-url")

    val prometheusMetrics = PrometheusMetrics.default[IO]()

    val resources: Resource[IO, (sttp.client3.SttpBackend[IO, Any], NettyCatsServer[IO])] =
      for {
        sttpBackend <- HttpClientCatsBackend.resource[IO]()
        dispatcher  <- Dispatcher.parallel[IO]
      } yield {
        val opts: NettyCatsServerOptions[IO] =
          NettyCatsServerOptions
            .customiseInterceptors[IO](dispatcher)
            .metricsInterceptor(prometheusMetrics.metricsInterceptor())
            .options
        (sttpBackend, NettyCatsServer[IO](opts))
      }

    resources.use { case (sttpBackend, server) =>
      val routes       = FrontendRoutes(sttpBackend, userServiceUrl, orderServiceUrl)
      val allEndpoints = routes :+ prometheusMetrics.metricsEndpoint

      server
        .host("0.0.0.0")
        .port(port)
        .addEndpoints(allEndpoints)
        .start()
        .flatMap { binding =>
          IO(log.info("Frontend listening on port {} (Tapir/Netty, /metrics available)", binding.port)) >>
            IO.never.as(ExitCode.Success)
        }
    }
  }
}
