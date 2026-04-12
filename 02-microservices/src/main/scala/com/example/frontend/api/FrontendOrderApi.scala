package com.example.frontend.api

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.spray._

/**
 * Public HTTP contract of the API gateway (frontend) for order-related calls.
 *
 * Deliberately decoupled from the backend contract
 * ([[com.example.backend.orders.api.OrderServiceApi]]). Translation is done
 * in [[com.example.frontend.mapping.OrderMapping]] via Chimney.
 */
// `customerId` / `products` differ from the backend's `userId` / `items` on purpose.
final case class PlaceOrderCommand(customerId: String, products: List[String])

final case class OrderResponse(orderId: String, customerId: String, products: List[String], state: String)

object FrontendOrderApi {
  implicit val placeOrderCommandFormat: RootJsonFormat[PlaceOrderCommand] = jsonFormat2(PlaceOrderCommand)
  implicit val orderResponseFormat:     RootJsonFormat[OrderResponse]     = jsonFormat4(OrderResponse)

  val placeOrder: PublicEndpoint[PlaceOrderCommand, StatusCode, OrderResponse, Any] =
    endpoint.post
      .in("orders")
      .in(jsonBody[PlaceOrderCommand])
      .out(statusCode(StatusCode.Created).and(jsonBody[OrderResponse]))
      .errorOut(statusCode)

  val getOrder: PublicEndpoint[String, StatusCode, OrderResponse, Any] =
    endpoint.get
      .in("orders" / path[String]("orderId"))
      .out(jsonBody[OrderResponse])
      .errorOut(statusCode)
}
