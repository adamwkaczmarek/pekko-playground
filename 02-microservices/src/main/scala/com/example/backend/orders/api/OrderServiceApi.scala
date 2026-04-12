package com.example.backend.orders.api

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

/**
 * Public wire contract of the OrderService.
 *
 * Owned by the order-service team. Serialised by the Pekko HTTP routes on the
 * server side, and consumed as-is by any client. The entity-internal type
 * [[com.example.backend.orders.OrderEntity.OrderDetails]] is NOT exposed
 * directly — the routes convert it to [[OrderDetailsDto]].
 */
final case class OrderPlaceRequest(userId: String, items: List[String])

final case class OrderDetailsDto(orderId: String, userId: String, items: List[String], status: String)

object OrderServiceApi {
  implicit val orderPlaceRequestFormat: RootJsonFormat[OrderPlaceRequest] = jsonFormat2(OrderPlaceRequest)
  implicit val orderDetailsDtoFormat:   RootJsonFormat[OrderDetailsDto]   = jsonFormat4(OrderDetailsDto)
}
