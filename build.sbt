import uk.gov.hmrc.DefaultBuildSettings.{itSettings, targetJvm}

val appName = "voa-bar"

ThisBuild / scalaVersion := "3.8.1"
ThisBuild / majorVersion := 1
ThisBuild / scalafmtFailOnErrors := true
ThisBuild / semanticdbEnabled := true

val commonSettings = Seq(
  targetJvm := "jvm-21",
  scalacOptions += "-Wconf:msg=Flag .* set repeatedly:s"
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(commonSettings)
  .settings(
    maintainer := "voa.service.optimisation@digital.hmrc.gov.uk",
    PlayKeys.playDefaultPort := 8447,
    libraryDependencies ++= AppDependencies.appDependencies,
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Wconf:msg=Implicit parameters should be provided with a \\`using\\` clause&src=views/.*:s",
    javaOptions += "-XX:+EnableDynamicAgentLoading",
    Test / fork := true
  )

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice)
  .settings(commonSettings)
  .settings(itSettings())
  .settings(libraryDependencies ++= AppDependencies.itDependencies)

addCommandAlias("scalastyle", ";scalafmtAll;scalafmtSbt;it/test:scalafmt;scalafixAll")
