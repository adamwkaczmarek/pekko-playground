package com.example.backend

import io.prometheus.client.{Counter, Gauge}

/**
 * Prometheus metrics for Pekko Cluster Sharding.
 * Registered into CollectorRegistry.defaultRegistry (same instance
 * that the /metrics endpoint exposes via pekko-http-metrics).
 */
object ShardingMetrics {

  val entityActivations: Counter = Counter.build()
    .name("pekko_entity_activations_total")
    .help("Entity actors started (sharding wake-ups / recoveries from passivation)")
    .labelNames("entity_type")
    .register()

  val clusterMembersUp: Gauge = Gauge.build()
    .name("pekko_cluster_members_up")
    .help("Number of cluster members currently in Up state")
    .register()

  val shardsPerNode: Gauge = Gauge.build()
    .name("pekko_sharding_shards_per_node")
    .help("Number of shards currently hosted on this node")
    .labelNames("entity_type")
    .register()

  val entitiesPerNode: Gauge = Gauge.build()
    .name("pekko_sharding_entities_per_node")
    .help("Number of live entity actors on this node")
    .labelNames("entity_type")
    .register()
}
