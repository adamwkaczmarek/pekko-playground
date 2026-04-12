import Dependencies._
import CommonSettings._

lazy val root = (project in file("."))
  .aggregate(clusterBasics, microservices)
  .settings(
    name           := "pekko-playground",
    publish / skip := true,
  )

lazy val microservices = (project in file("02-microservices"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name                   := "02-microservices",
    settings,
    Compile / mainClass    := Some("com.example.frontend.FrontendApp"),
    libraryDependencies   ++= clusterDeps ++ managementDeps ++ httpDeps ++ metricsDeps ++ tapirDeps ++ testDeps,
    dockerBaseImage        := "eclipse-temurin:17-jre-jammy",
  )

lazy val clusterBasics = (project in file("01-cluster-basics"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "01-cluster-basics",
    settings,
    libraryDependencies ++= clusterDeps ++ managementDeps ++ testDeps,
  )
