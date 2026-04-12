package com.example.frontend.mapping

import com.example.backend.orders.api.{OrderDetailsDto, OrderPlaceRequest}
import com.example.frontend.api.{OrderResponse, PlaceOrderCommand}
import io.scalaland.chimney.dsl._

/**
 * Boundary translation between the frontend public contract
 * ([[com.example.frontend.api]]) and the backend order-service contract
 * ([[com.example.backend.orders.api]]).
 */
object OrderMapping {

  /** Frontend command → backend request. Fields intentionally renamed. */
  def toBackend(cmd: PlaceOrderCommand): OrderPlaceRequest =
    cmd.into[OrderPlaceRequest]
      .withFieldRenamed(_.customerId, _.userId)
      .withFieldRenamed(_.products,   _.items)
      .transform

  /** Backend response → frontend response. */
  def toFrontend(dto: OrderDetailsDto): OrderResponse =
    dto.into[OrderResponse]
      .withFieldRenamed(_.userId, _.customerId)
      .withFieldRenamed(_.items,  _.products)
      .withFieldRenamed(_.status, _.state)
      .transform
}
