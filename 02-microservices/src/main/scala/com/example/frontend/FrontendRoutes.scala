package com.example.frontend

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, Uri}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.{Materializer, SystemMaterializer}

import scala.concurrent.ExecutionContext

/**
 * HTTP routes for the FrontendService (API Gateway).
 *
 * The frontend is the only entry point exposed to the outside world.
 * It proxies all requests to the appropriate backend service without
 * holding any state itself.
 *
 * Routing:
 *   /users/...   ->  UserService (internal)
 *   /orders/...  ->  OrderService (internal)
 *
 * Because this is a pure proxy, we use extractRequest + singleRequest
 * to forward the original request with a rewritten URI.
 */
object FrontendRoutes {

  def apply()(implicit system: ActorSystem[_]): Route = {
    implicit val ec:  ExecutionContext = system.executionContext
    implicit val mat: Materializer     = SystemMaterializer(system).materializer

    val userServiceUrl  = system.settings.config.getString("user-service.base-url")
    val orderServiceUrl = system.settings.config.getString("order-service.base-url")

    extractRequest { originalRequest =>
      concat(

        // /users/** → UserService
        pathPrefix("users") {
          val backendUri = Uri(userServiceUrl).withPath(originalRequest.uri.path)
          val backendReq = originalRequest.withUri(backendUri).removeHeader("Timeout-Access")
          complete(Http().singleRequest(backendReq))
        },

        // /orders/** → OrderService
        pathPrefix("orders") {
          val backendUri = Uri(orderServiceUrl).withPath(originalRequest.uri.path)
          val backendReq = originalRequest.withUri(backendUri).removeHeader("Timeout-Access")
          complete(Http().singleRequest(backendReq))
        }
      )
    }
  }
}
