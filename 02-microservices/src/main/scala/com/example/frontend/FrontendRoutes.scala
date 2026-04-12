package com.example.frontend

import cats.effect.IO
import com.example.backend.orders.api.{OrderDetailsDto, OrderServiceApi}
import com.example.backend.users.api.{UserProfileDto, UserServiceApi}
import com.example.frontend.api.{FrontendOrderApi, FrontendUserApi}
import com.example.frontend.mapping.{OrderMapping, UserMapping}
import sttp.client3._
import sttp.client3.sprayJson._
import sttp.model.{StatusCode, Uri}
import sttp.tapir.server.ServerEndpoint

/**
 * Tapir server endpoints for the FrontendService.
 *
 * The frontend owns its own public contract (see [[com.example.frontend.api]]).
 * Each handler:
 *   1. accepts a frontend DTO from the caller,
 *   2. translates it into the matching backend-service DTO via Chimney
 *      ([[com.example.frontend.mapping]]),
 *   3. calls the backend directly with sttp,
 *   4. translates the backend response back into the frontend DTO.
 *
 * Backends are consumed as a plain HTTP service — we import their API package
 * only for DTO definitions + JSON formats, never their routing technology.
 */
object FrontendRoutes {

  // Bring the spray-json formats of both contracts into implicit scope so that
  // `asJson[T]` (sttp) and the Tapir endpoints can marshal/unmarshal bodies.
  import FrontendUserApi.{registerUserRequestFormat, userResponseFormat}
  import FrontendOrderApi.{orderResponseFormat, placeOrderCommandFormat}
  import UserServiceApi.{userProfileDtoFormat, userRegisterRequestFormat}
  import OrderServiceApi.{orderDetailsDtoFormat, orderPlaceRequestFormat}

  def apply(
    sttpBackend:     SttpBackend[IO, Any],
    userServiceUrl:  String,
    orderServiceUrl: String,
  ): List[ServerEndpoint[Any, IO]] = {

    val userBaseUri:  Uri = parseUri(userServiceUrl,  "user-service")
    val orderBaseUri: Uri = parseUri(orderServiceUrl, "order-service")

    // ── users ────────────────────────────────────────────────────────────

    val getUser: ServerEndpoint[Any, IO] =
      FrontendUserApi.getUser.serverLogic[IO] { userId =>
        basicRequest
          .get(uri"$userBaseUri/users/$userId")
          .response(asJson[UserProfileDto])
          .send(sttpBackend)
          .map(projectBody(_)(UserMapping.toFrontend))
      }

    val registerUser: ServerEndpoint[Any, IO] =
      FrontendUserApi.registerUser.serverLogic[IO] { case (userId, req) =>
        val backendReq = UserMapping.toBackend(req)
        basicRequest
          .post(uri"$userBaseUri/users/$userId")
          .body(backendReq)
          .response(asJson[UserProfileDto])
          .send(sttpBackend)
          .map(projectBody(_)(UserMapping.toFrontend))
      }

    // ── orders ───────────────────────────────────────────────────────────

    val placeOrder: ServerEndpoint[Any, IO] =
      FrontendOrderApi.placeOrder.serverLogic[IO] { cmd =>
        val backendReq = OrderMapping.toBackend(cmd)
        basicRequest
          .post(uri"$orderBaseUri/orders")
          .body(backendReq)
          .response(asJson[OrderDetailsDto])
          .send(sttpBackend)
          .map(projectBody(_)(OrderMapping.toFrontend))
      }

    val getOrder: ServerEndpoint[Any, IO] =
      FrontendOrderApi.getOrder.serverLogic[IO] { orderId =>
        basicRequest
          .get(uri"$orderBaseUri/orders/$orderId")
          .response(asJson[OrderDetailsDto])
          .send(sttpBackend)
          .map(projectBody(_)(OrderMapping.toFrontend))
      }

    List(getUser, registerUser, placeOrder, getOrder)
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private def parseUri(raw: String, label: String): Uri =
    Uri.parse(raw).fold(err => sys.error(s"Invalid $label URL '$raw': $err"), identity)

  /**
   * Turn an sttp response whose body has already been parsed as `Either[_, B]`
   * into the `Either[StatusCode, F]` shape expected by Tapir `serverLogic`.
   * Backend non-2xx responses propagate as the same HTTP status code.
   */
  private def projectBody[E, B, F](
    response: Response[Either[E, B]],
  )(mapOk: B => F): Either[StatusCode, F] =
    response.body match {
      case Right(dto) => Right(mapOk(dto))
      case Left(_)    => Left(StatusCode(response.code.code))
    }
}
