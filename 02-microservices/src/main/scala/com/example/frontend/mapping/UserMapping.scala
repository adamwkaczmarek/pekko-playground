package com.example.frontend.mapping

import com.example.backend.users.api.{UserProfileDto, UserRegisterRequest}
import com.example.frontend.api.{RegisterUserRequest, UserResponse}
import io.scalaland.chimney.dsl._

/**
 * Boundary translation between the frontend public contract
 * ([[com.example.frontend.api]]) and the backend user-service contract
 * ([[com.example.backend.users.api]]).
 *
 * All rules are declared here so field additions/renames on either side
 * surface as compile errors rather than silent wire mismatches.
 */
object UserMapping {

  /** Frontend request → backend request. Fields line up 1:1, fully derived. */
  def toBackend(req: RegisterUserRequest): UserRegisterRequest =
    req.transformInto[UserRegisterRequest]

  /** Backend response → frontend response. Fields intentionally renamed. */
  def toFrontend(dto: UserProfileDto): UserResponse =
    dto.into[UserResponse]
      .withFieldRenamed(_.userId, _.id)
      .withFieldRenamed(_.name,   _.displayName)
      .transform
}
