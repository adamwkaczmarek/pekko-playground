package com.example.backend.users.api

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

/**
 * Public wire contract of the UserService.
 *
 * These DTOs are owned by the user-service team. They are serialised by the
 * Pekko HTTP routes on the server side, and consumed as-is by any client
 * (including the frontend API gateway via an sttp call).
 *
 * The entity-internal type [[com.example.backend.users.UserEntity.UserProfile]]
 * is NOT exposed directly — the routes convert it to [[UserProfileDto]] so the
 * actor protocol can evolve independently from the HTTP contract.
 */
final case class UserRegisterRequest(name: String, email: String)

final case class UserProfileDto(userId: String, name: String, email: String)

object UserServiceApi {
  implicit val userRegisterRequestFormat: RootJsonFormat[UserRegisterRequest] = jsonFormat2(UserRegisterRequest)
  implicit val userProfileDtoFormat:      RootJsonFormat[UserProfileDto]      = jsonFormat3(UserProfileDto)
}
