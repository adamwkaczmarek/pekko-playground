package com.example.backend.users

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.Timeout
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object UserRoutes {

  // ── JSON formats ─────────────────────────────────────────────────────────

  final case class RegisterRequest(name: String, email: String)

  implicit val registerRequestFmt: RootJsonFormat[RegisterRequest]        = jsonFormat2(RegisterRequest)
  implicit val userProfileFmt:     RootJsonFormat[UserEntity.UserProfile] = jsonFormat3(UserEntity.UserProfile)

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
            case Success(profile) => complete(profile)
            case Failure(_)       => complete(StatusCodes.NotFound)
          }
        },

        // POST /users/{userId}
        // StatusReply.success → 201 with profile body
        (post & pathEnd & entity(as[RegisterRequest])) { req =>
          onSuccess(userRef.askWithStatus(UserEntity.Register(req.name, req.email, _))) { profile =>
            complete(StatusCodes.Created -> profile)
          }
        }
      )
    }
  }
}
