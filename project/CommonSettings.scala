import sbt.*
import sbt.Keys.*

object Versions {
  val pekko            = "1.1.2"
  val pekkoManagement  = "1.1.0"
  val pekkoHttp        = "1.1.0"
  val pekkoHttpMetrics = "1.0.1"
  val tapir            = "1.11.10"
  val sttp             = "3.10.1"
  val catsEffect       = "3.5.4"
  val chimney          = "1.5.0"
  val logback          = "1.5.6"
  val scalatest        = "3.2.18"
  val scala            = "2.13.15"
}

object Dependencies {
  import Versions.*

  val clusterDeps: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor-typed"            % pekko,
    "org.apache.pekko" %% "pekko-cluster-typed"          % pekko,
    "org.apache.pekko" %% "pekko-cluster-sharding-typed" % pekko,
    "org.apache.pekko" %% "pekko-serialization-jackson"  % pekko,
    "org.apache.pekko" %% "pekko-slf4j"                  % pekko,
    "ch.qos.logback"    % "logback-classic"              % logback,
  )

  val managementDeps: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-management"                   % pekkoManagement,
    "org.apache.pekko" %% "pekko-management-cluster-http"      % pekkoManagement,
    "org.apache.pekko" %% "pekko-management-cluster-bootstrap" % pekkoManagement,
    "org.apache.pekko" %% "pekko-discovery-kubernetes-api"     % pekkoManagement,
  )

  val httpDeps: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-http"            % pekkoHttp,
    "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttp,
  )

  val metricsDeps: Seq[ModuleID] = Seq(
    "fr.davit" %% "pekko-http-metrics-prometheus" % Versions.pekkoHttpMetrics,
  )

  // Tapir + Netty (cats-effect) — used by the frontend (API gateway).
  // Backends still run on Pekko HTTP.
  val tapirDeps: Seq[ModuleID] = Seq(
    "com.softwaremill.sttp.tapir"   %% "tapir-core"               % tapir,
    "com.softwaremill.sttp.tapir"   %% "tapir-netty-server-cats"  % tapir,
    "com.softwaremill.sttp.tapir"   %% "tapir-json-spray"         % tapir,
    "com.softwaremill.sttp.tapir"   %% "tapir-prometheus-metrics" % tapir,
    "com.softwaremill.sttp.client3" %% "core"                     % sttp,
    "com.softwaremill.sttp.client3" %% "cats"                     % sttp,
    "com.softwaremill.sttp.client3" %% "spray-json"               % sttp,
    "org.typelevel"                 %% "cats-effect"              % catsEffect,
    "io.scalaland"                  %% "chimney"                  % chimney,
  )

  val testDeps: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekko      % Test,
    "org.scalatest"    %% "scalatest"                 % scalatest  % Test,
  )
}

object CommonSettings {
  val settings: Def.SettingsDefinition = Seq(
    scalaVersion    := Versions.scala,
    organization    := "com.example",
    version         := "0.1.0",
    Test / fork     := true,
    libraryDependencySchemes +=
      "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always,
  )
}
