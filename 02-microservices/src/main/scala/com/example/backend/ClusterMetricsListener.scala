package com.example.backend

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.ClusterEvent._
import org.apache.pekko.cluster.typed.{Cluster, Subscribe}

/**
 * Subscribes to Pekko Cluster membership events and keeps
 * ShardingMetrics.clusterMembersUp in sync.
 *
 * Spawn once from each service's guardian:
 *   ctx.spawn(ClusterMetricsListener(), "cluster-metrics-listener")
 */
object ClusterMetricsListener {

  sealed trait Event
  private final case class MemberChange(event: MemberEvent) extends Event

  def apply(): Behavior[Event] = Behaviors.setup { ctx =>
    val cluster = Cluster(ctx.system)
    val adapter: ActorRef[MemberEvent] = ctx.messageAdapter(MemberChange.apply)
    cluster.subscriptions ! Subscribe(adapter, classOf[MemberEvent])

    Behaviors.receiveMessage {
      case MemberChange(_: MemberUp)      => ShardingMetrics.clusterMembersUp.inc(); Behaviors.same
      case MemberChange(_: MemberRemoved) => ShardingMetrics.clusterMembersUp.dec(); Behaviors.same
      case _                              => Behaviors.same
    }
  }
}
