package com.example

/**
 * Marker trait for messages that may cross node boundaries (cluster, sharding, remote).
 * Every such message class must extend this trait so the Jackson-CBOR serializer
 * can handle it. See application.conf serialization-bindings.
 */
trait CborSerializable
