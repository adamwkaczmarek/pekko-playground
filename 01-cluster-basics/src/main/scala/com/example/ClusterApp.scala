package com.example

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.management.scaladsl.PekkoManagement
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Entry point for the Pekko Cluster demo.
 *
 * Start three instances on different ports to form a local cluster:
 *
 *   # Terminal 1 (first seed node)
 *   sbt -Dpekko.remote.artery.canonical.port=25520 run
 *
 *   # Terminal 2
 *   sbt -Dpekko.remote.artery.canonical.port=25521 \
 *       -Dpekko.management.http.port=8559 run
 *
 *   # Terminal 3
 *   sbt -Dpekko.remote.artery.canonical.port=25522 \
 *       -Dpekko.management.http.port=8560 run
 *
 * Watch the logs — ClusterListener prints a line every time a member joins,
 * leaves, becomes unreachable, or is removed.
 *
 * Query cluster state via the management HTTP API:
 *   curl http://localhost:8558/cluster/members | jq .
 */
object ClusterApp extends App {

  val system = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { ctx =>

      // 1. Subscribe to cluster events — this is the learning surface.
      ctx.spawn(ClusterListener(), "cluster-listener")

      // 2. Register the Counter entity type with Cluster Sharding.
      //    From this point any node can send a message to counter-N and Pekko
      //    will route it to whichever node currently owns that shard.
      val sharding = ClusterSharding(ctx.system)
      sharding.init(Entity(Counter.TypeKey)(entityCtx => Counter(entityCtx.entityId)))

      // 3. Spawn a driver that periodically increments a handful of counters.
      //    Because the entities are distributed, requests will cross the network
      //    to whichever node owns each counter — visible in the logs.
      ctx.spawn(CounterDriver(sharding), "counter-driver")

      Behaviors.empty
    },
    "ClusterSystem"
  )

  // ---- Pekko Management HTTP -----------------------------------------------
  // Provides:
  //   GET  /cluster/members          — list all members and their status
  //   GET  /cluster/members/{addr}   — single member detail
  //   PUT  /cluster/members/{addr}   — trigger graceful leave
  //   DELETE /cluster/members/{addr} — force down a member
  //   GET  /health/ready             — readiness probe (for k8s)
  //   GET  /health/alive             — liveness probe  (for k8s)
  val management = PekkoManagement(system)
  management.start()

  // ---- Cluster Bootstrap (Kubernetes only) ---------------------------------
  // When seed-nodes is empty the app assumes it is running in Kubernetes and
  // uses the Kubernetes API to discover other pods and form the cluster.
  val seedNodes = system.settings.config.getList("pekko.cluster.seed-nodes")
  if (seedNodes.isEmpty) {
    system.log.info("No static seed-nodes configured — starting Cluster Bootstrap (Kubernetes mode)")
    ClusterBootstrap(system).start()
  }

  sys.addShutdownHook {
    system.log.info("Shutting down...")
    system.terminate()
  }

  // Block the main thread until the ActorSystem terminates.
  // Without this, sbt batch mode exits after main() returns — killing the JVM
  // and with it the entire Pekko cluster node.
  Await.ready(system.whenTerminated, Duration.Inf)
}

// ---------------------------------------------------------------------------
// Driver: sends periodic Increment messages to 5 sharded Counter entities.
// ---------------------------------------------------------------------------
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{Behaviors => B}
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding

object CounterDriver {

  sealed trait Msg
  case object Tick extends Msg

  def apply(sharding: ClusterSharding): Behavior[Msg] =
    B.withTimers { timers =>
      // Fire the first tick immediately, then every 8 seconds.
      timers.startTimerWithFixedDelay(Tick, 0.seconds, 8.seconds)

      B.receive { (ctx, _) =>
        // Cycle through 5 logical counters.  Each has a stable entity ID so
        // Pekko always routes "counter-0" to the same shard (unless it
        // rebalances after a topology change).
        val id  = s"counter-${(System.currentTimeMillis() / 8000) % 5}"
        val ref = sharding.entityRefFor(Counter.TypeKey, id)
        ref ! Counter.Increment(1)
        ctx.log.info("Sent Increment(1) to {}", id)
        B.same
      }
    }
}
