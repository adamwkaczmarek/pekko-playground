package com.example

/**
 * Marker trait for messages that must cross the network between cluster nodes.
 *
 * All actor messages sent between nodes need a serializer.  We use Jackson CBOR
 * (configured in application.conf under pekko.actor.serialization-bindings).
 * Just extend this trait on every message that leaves the JVM boundary.
 */
trait CborSerializable
