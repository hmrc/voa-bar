import uk.gov.hmrc.DefaultBuildSettings.itSettings

val appName = "voa-bar"

ThisBuild / scalaVersion := "3.6.3"
ThisBuild / majorVersion := 1
ThisBuild / scalafmtFailOnErrors := true
ThisBuild / semanticdbEnabled := true

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    PlayKeys.playDefaultPort := 8447,
    libraryDependencies ++= AppDependencies.appDependencies,
    scalacOptions += "-Wconf:src=routes/.*:s",
    Test / fork := true
  )

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice)
  .settings(itSettings())
  .settings(libraryDependencies ++= AppDependencies.itDependencies)

addCommandAlias("scalastyle", ";scalafmtAll;scalafmtSbt;it/test:scalafmt;scalafixAll")
