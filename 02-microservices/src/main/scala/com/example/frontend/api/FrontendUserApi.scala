package com.example.frontend.api

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.spray._

/**
 * Public HTTP contract of the API gateway (frontend) for user-related calls.
 *
 * Deliberately decoupled from the backend contract
 * ([[com.example.backend.users.api.UserServiceApi]]): the backend can
 * rename/add fields without breaking public clients, and vice versa.
 * Translation is done in [[com.example.frontend.mapping.UserMapping]] via
 * Chimney.
 */
final case class RegisterUserRequest(name: String, email: String)

// Note: `id` / `displayName` differ from the backend's `userId` / `name` on purpose
// to force explicit mapping rules.
final case class UserResponse(id: String, displayName: String, email: String)

object FrontendUserApi {
  implicit val registerUserRequestFormat: RootJsonFormat[RegisterUserRequest] = jsonFormat2(RegisterUserRequest)
  implicit val userResponseFormat:        RootJsonFormat[UserResponse]        = jsonFormat3(UserResponse)

  val getUser: PublicEndpoint[String, StatusCode, UserResponse, Any] =
    endpoint.get
      .in("users" / path[String]("userId"))
      .out(jsonBody[UserResponse])
      .errorOut(statusCode)

  val registerUser: PublicEndpoint[(String, RegisterUserRequest), StatusCode, UserResponse, Any] =
    endpoint.post
      .in("users" / path[String]("userId"))
      .in(jsonBody[RegisterUserRequest])
      .out(statusCode(StatusCode.Created).and(jsonBody[UserResponse]))
      .errorOut(statusCode)
}
