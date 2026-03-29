package com.example.backend

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.{ClusterSharding => ClassicSharding}
import org.apache.pekko.cluster.sharding.ShardRegion
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout

import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * Polls the local ShardRegion every 15 seconds and updates
 * ShardingMetrics.shardsPerNode / entitiesPerNode gauges.
 *
 * Spawned once per entity type from each service's guardian.
 * Polling is deferred 15 s so the cluster has time to form before
 * the first query — and the try/catch silently skips any poll that
 * fires before sharding is fully initialised.
 */
object ShardStatsPoller {

  def apply(entityTypeName: String): Behavior[Nothing] =
    Behaviors.setup[Nothing] { ctx =>
      val ec        = ctx.system.executionContext
      val scheduler = ctx.system.classicSystem.scheduler

      scheduler.scheduleAtFixedRate(15.seconds, 15.seconds)(
        () => poll(ctx.system.classicSystem, entityTypeName)
      )(ec)

      Behaviors.empty[Nothing]
    }

  private def poll(
      classicSystem: org.apache.pekko.actor.ActorSystem,
      entityTypeName: String
  ): Unit = {
    implicit val ec: scala.concurrent.ExecutionContext = classicSystem.dispatcher
    implicit val timeout: Timeout = 3.seconds
    try {
      val region = ClassicSharding(classicSystem).shardRegion(entityTypeName)
      (region ? ShardRegion.GetShardRegionStats)
        .mapTo[ShardRegion.ShardRegionStats]
        .foreach { stats =>
          ShardingMetrics.shardsPerNode.labels(entityTypeName).set(stats.stats.size.toDouble)
          ShardingMetrics.entitiesPerNode.labels(entityTypeName).set(stats.stats.values.sum.toDouble)
        }
    } catch {
      case NonFatal(_) => // sharding not yet initialised — will retry next interval
    }
  }
}
