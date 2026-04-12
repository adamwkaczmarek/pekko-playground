package com.example.backend.users

import com.example.backend.users.api.{UserProfileDto, UserRegisterRequest, UserServiceApi}
import io.scalaland.chimney.dsl._
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object UserRoutes {

  // JSON formats come from the service-owned API package.
  import UserServiceApi.{userProfileDtoFormat, userRegisterRequestFormat}

  // ── Routes ───────────────────────────────────────────────────────────────

  def apply(sharding: ClusterSharding)(implicit system: ActorSystem[_]): Route = {
    implicit val timeout:   Timeout          = 3.seconds
    implicit val scheduler: Scheduler        = system.scheduler
    implicit val ec:        ExecutionContext  = system.executionContext

    pathPrefix("users" / Segment) { userId =>
      val userRef = sharding.entityRefFor(UserEntity.TypeKey, userId)

      concat(

        // GET /users/{userId}
        // StatusReply.error → 404, StatusReply.success → 200
        (get & pathEnd) {
          onComplete(userRef.askWithStatus(UserEntity.GetProfile(_))) {
            case Success(profile) => complete(profile.transformInto[UserProfileDto])
            case Failure(_)       => complete(StatusCodes.NotFound)
          }
        },

        // POST /users/{userId}
        // StatusReply.success → 201 with profile body
        (post & pathEnd & entity(as[UserRegisterRequest])) { req =>
          onSuccess(userRef.askWithStatus(UserEntity.Register(req.name, req.email, _))) { profile =>
            complete(StatusCodes.Created -> profile.transformInto[UserProfileDto])
          }
        }
      )
    }
  }
}
