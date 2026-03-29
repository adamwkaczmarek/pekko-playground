package com.example

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey

/**
 * A simple sharded counter — the canonical Cluster Sharding example.
 *
 * How sharding works in Pekko:
 *   1. You define an EntityTypeKey that names a "shard region" (Counter here).
 *   2. ClusterSharding distributes entity actors across the cluster nodes.
 *   3. Any node can send a message to any entity by ID; sharding routes it
 *      transparently to whichever node currently hosts that entity.
 *   4. Entities are automatically rebalanced when nodes join or leave.
 *
 * Run 3 nodes and watch the logs — you will see "Counter[counter-2] lives on
 * node pekko://ClusterSystem@127.0.0.1:25521" style messages as shards are
 * assigned and reassigned.
 */
object Counter {

  // The TypeKey is the cluster-wide identity of this entity type.
  // It maps to a ShardRegion on every node.
  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Counter")

  // CborSerializable is required because messages may travel over the network
  // when the sending node does not host the target entity.
  sealed trait Command extends CborSerializable

  final case class Increment(by: Int)               extends Command
  final case class GetValue(replyTo: ActorRef[Int]) extends Command
  case object Reset                                  extends Command

  def apply(entityId: String): Behavior[Command] = counter(entityId, 0)

  private def counter(entityId: String, value: Int): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Increment(by) =>
          val next = value + by
          ctx.log.info("[{}] {} + {} = {}", entityId, value, by, next)
          counter(entityId, next)

        case GetValue(replyTo) =>
          replyTo ! value
          Behaviors.same

        case Reset =>
          ctx.log.info("[{}] reset to 0", entityId)
          counter(entityId, 0)
      }
    }
}
