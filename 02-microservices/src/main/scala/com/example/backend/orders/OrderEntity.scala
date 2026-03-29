package com.example.backend.orders

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import com.example.CborSerializable
import com.example.backend.ShardingMetrics

/**
 * Sharded entity representing a single order.
 *
 * Lives inside the OrderService cluster. The orderId is the shard key —
 * all messages for the same order land on the same node.
 *
 * State machine:
 *   empty ──PlaceOrder──► placed  (idempotent: repeated PlaceOrder returns same result)
 */
object OrderEntity {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Order")

  // ── Protocol ─────────────────────────────────────────────────────────────

  sealed trait Command extends CborSerializable

  final case class PlaceOrder(
    userId:  String,
    items:   List[String],
    replyTo: ActorRef[Response]
  ) extends Command

  final case class GetOrder(replyTo: ActorRef[Response]) extends Command

  sealed trait Response extends CborSerializable

  final case class OrderDetails(
    orderId: String,
    userId:  String,
    items:   List[String],
    status:  String
  ) extends Response

  case object OrderNotFound extends Response

  // ── Behaviour ────────────────────────────────────────────────────────────

  def apply(orderId: String): Behavior[Command] = Behaviors.setup { _ =>
    ShardingMetrics.entityActivations.labels("Order").inc()
    empty(orderId)
  }

  private def empty(orderId: String): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case PlaceOrder(userId, items, replyTo) =>
          ctx.log.info("Order {} placed for user {}: {}", orderId, userId, items.mkString(", "))
          replyTo ! OrderDetails(orderId, userId, items, "placed")
          placed(orderId, userId, items)

        case GetOrder(replyTo) =>
          replyTo ! OrderNotFound
          Behaviors.same
      }
    }

  private def placed(orderId: String, userId: String, items: List[String]): Behavior[Command] =
    Behaviors.receiveMessage {
      case PlaceOrder(_, _, replyTo) =>
        replyTo ! OrderDetails(orderId, userId, items, "placed")
        Behaviors.same

      case GetOrder(replyTo) =>
        replyTo ! OrderDetails(orderId, userId, items, "placed")
        Behaviors.same
    }
}
