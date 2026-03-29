package com.example.backend.users

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.pekko.pattern.StatusReply
import com.example.CborSerializable
import com.example.backend.ShardingMetrics

/**
 * Sharded entity representing a single user.
 *
 * Commands reply via StatusReply[UserProfile]:
 *   StatusReply.success(profile) — operation succeeded
 *   StatusReply.error("msg")     — operation failed (e.g. user not found)
 *
 * State machine:
 *   unregistered ──Register──► registered
 *                               │
 *                               └──Register──► registered  (update allowed)
 */
object UserEntity {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("User")

  // ── Protocol ─────────────────────────────────────────────────────────────

  sealed trait Command extends CborSerializable

  final case class Register(
    name:    String,
    email:   String,
    replyTo: ActorRef[StatusReply[UserProfile]]
  ) extends Command

  final case class GetProfile(
    replyTo: ActorRef[StatusReply[UserProfile]]
  ) extends Command

  // No more Response sealed trait — StatusReply replaces it
  final case class UserProfile(
    userId: String,
    name:   String,
    email:  String
  ) extends CborSerializable

  // ── Behaviour ────────────────────────────────────────────────────────────

  def apply(userId: String): Behavior[Command] = Behaviors.setup { _ =>
    ShardingMetrics.entityActivations.labels("User").inc()
    unregistered(userId)
  }

  private def unregistered(userId: String): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Register(name, email, replyTo) =>
          ctx.log.info("Registered user {}", userId)
          replyTo ! StatusReply.success(UserProfile(userId, name, email))
          registered(userId, name, email)

        case GetProfile(replyTo) =>
          replyTo ! StatusReply.error(s"User '$userId' not found")
          Behaviors.same
      }
    }

  private def registered(userId: String, name: String, email: String): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Register(newName, newEmail, replyTo) =>
          ctx.log.info("Updated profile for user {}", userId)
          replyTo ! StatusReply.success(UserProfile(userId, newName, newEmail))
          registered(userId, newName, newEmail)

        case GetProfile(replyTo) =>
          replyTo ! StatusReply.success(UserProfile(userId, name, email))
          Behaviors.same
      }
    }
}
