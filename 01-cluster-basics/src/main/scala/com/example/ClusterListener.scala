package com.example

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.ClusterEvent._
import org.apache.pekko.cluster.typed.{Cluster, Subscribe}

/**
 * Subscribes to all cluster membership and reachability events and logs them.
 *
 * This is the simplest entry point for understanding how Pekko Cluster works:
 * run multiple nodes and watch this actor report the lifecycle of each member.
 *
 * Key lifecycle states a member goes through:
 *   Joining -> WeaklyUp -> Up -> Leaving -> Exiting -> Removed
 *
 * Reachability events fire when the failure detector marks a node as unreachable
 * (e.g. due to a network partition) and when it becomes reachable again.
 */
object ClusterListener {

  // Internal protocol — wraps the two distinct cluster event hierarchies
  sealed trait Event
  private final case class MemberChange(event: MemberEvent)             extends Event
  private final case class ReachabilityChange(event: ReachabilityEvent) extends Event

  def apply(): Behavior[Event] = Behaviors.setup { ctx =>
    val cluster = Cluster(ctx.system)

    // Adapters bridge the untyped cluster event bus to our typed protocol
    val memberAdapter: ActorRef[MemberEvent] =
      ctx.messageAdapter(MemberChange.apply)
    val reachabilityAdapter: ActorRef[ReachabilityEvent] =
      ctx.messageAdapter(ReachabilityChange.apply)

    cluster.subscriptions ! Subscribe(memberAdapter,      classOf[MemberEvent])
    cluster.subscriptions ! Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

    ctx.log.info("ClusterListener started — watching for cluster events")

    Behaviors.receiveMessage {

      // ---- Membership events -----------------------------------------------
      case MemberChange(MemberUp(member)) =>
        ctx.log.info("★  Member UP:          {} | roles: {}", member.address, member.roles)
        Behaviors.same

      case MemberChange(MemberWeaklyUp(member)) =>
        // WeaklyUp: node is reachable but the cluster is not yet fully convergent.
        // Useful for keeping a partially partitioned node in service under some
        // consistency models, while the rest of the cluster remains uncertain.
        ctx.log.info("◑  Member WEAKLY UP:   {} (cluster not yet convergent)", member.address)
        Behaviors.same

      case MemberChange(MemberLeft(member)) =>
        ctx.log.info("←  Member LEAVING:     {}", member.address)
        Behaviors.same

      case MemberChange(MemberExited(member)) =>
        ctx.log.info("↓  Member EXITED:      {}", member.address)
        Behaviors.same

      case MemberChange(MemberDowned(member)) =>
        // Downed by the Split Brain Resolver — this node will be removed next.
        ctx.log.warn("✗  Member DOWNED:      {} (will be removed)", member.address)
        Behaviors.same

      case MemberChange(MemberRemoved(member, previousStatus)) =>
        ctx.log.info("✗  Member REMOVED:     {} (was: {})", member.address, previousStatus)
        Behaviors.same

      case MemberChange(MemberPreparingForShutdown(member)) =>
        ctx.log.info("⏳  Member PREPARING SHUTDOWN: {}", member.address)
        Behaviors.same

      case MemberChange(MemberReadyForShutdown(member)) =>
        ctx.log.info("⏸  Member READY FOR SHUTDOWN: {}", member.address)
        Behaviors.same

      case MemberChange(_) =>
        Behaviors.same

      // ---- Reachability events ---------------------------------------------
      case ReachabilityChange(UnreachableMember(member)) =>
        // The failure detector has not heard from this node within the threshold.
        // The Split Brain Resolver will now decide whether to down it.
        ctx.log.warn("⚡  Member UNREACHABLE: {} — SBR will evaluate", member.address)
        Behaviors.same

      case ReachabilityChange(ReachableMember(member)) =>
        ctx.log.info("✔  Member REACHABLE again: {}", member.address)
        Behaviors.same
    }
  }
}
