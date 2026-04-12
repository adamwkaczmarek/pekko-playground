package com.example.backend.orders

import com.example.backend.orders.api.{OrderDetailsDto, OrderPlaceRequest, OrderServiceApi}
import io.scalaland.chimney.dsl._
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.http.scaladsl.model.{HttpRequest, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.apache.pekko.util.Timeout

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object OrderRoutes {

  // JSON formats come from the service-owned API package.
  import OrderServiceApi.{orderDetailsDtoFormat, orderPlaceRequestFormat}

  // ── Routes ───────────────────────────────────────────────────────────────

  def apply(sharding: ClusterSharding)(implicit system: ActorSystem[_]): Route = {
    implicit val timeout:   Timeout         = 5.seconds
    implicit val scheduler: Scheduler       = system.scheduler
    implicit val ec:        ExecutionContext = system.executionContext
    implicit val mat:       Materializer     = SystemMaterializer(system).materializer

    // UserService base URL — backend-to-backend call, bypasses the frontend
    val userServiceUrl = system.settings.config.getString("user-service.base-url")

    concat(

      // POST /orders — place a new order
      // Validates that the user exists by calling UserService internally,
      // then persists the order in the sharded OrderEntity.
      (post & path("orders") & entity(as[OrderPlaceRequest])) { req =>
        val orderId  = UUID.randomUUID().toString.take(8)
        val orderRef = sharding.entityRefFor(OrderEntity.TypeKey, orderId)

        val result: Future[Route] =
          Http().singleRequest(HttpRequest(uri = s"$userServiceUrl/users/${req.userId}"))
            .flatMap { resp =>
              resp.discardEntityBytes()
              resp.status match {
                case StatusCodes.OK =>
                  orderRef.ask(OrderEntity.PlaceOrder(req.userId, req.items, _)).map {
                    case details: OrderEntity.OrderDetails =>
                      complete(StatusCodes.Created -> details.transformInto[OrderDetailsDto])
                    case _ =>
                      complete(StatusCodes.InternalServerError)
                  }
                case StatusCodes.NotFound =>
                  Future.successful(
                    complete(StatusCodes.BadRequest -> s"User '${req.userId}' not found")
                  )
                case other =>
                  system.log.warn("UserService returned unexpected status: {}", other)
                  Future.successful(
                    complete(StatusCodes.ServiceUnavailable -> "UserService unavailable")
                  )
              }
            }

        onSuccess(result)(identity)
      },

      // GET /orders/{orderId}
      (get & path("orders" / Segment)) { orderId =>
        val orderRef = sharding.entityRefFor(OrderEntity.TypeKey, orderId)
        onSuccess(orderRef.ask(OrderEntity.GetOrder(_))) {
          case details: OrderEntity.OrderDetails => complete(details.transformInto[OrderDetailsDto])
          case OrderEntity.OrderNotFound         => complete(StatusCodes.NotFound)
        }
      }
    )
  }
}
