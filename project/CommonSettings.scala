import sbt._
import sbt.Keys._

object Versions {
  val pekko           = "1.1.2"
  val pekkoManagement = "1.1.0"
  val pekkoHttp       = "1.1.0"
  val pekkoHttpMetrics = "1.0.1"
  val logback         = "1.5.6"
  val scalatest       = "3.2.18"
  val scala           = "2.13.14"
}

object Dependencies {
  import Versions._

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
