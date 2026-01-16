import uk.gov.hmrc.DefaultBuildSettings.itSettings

val appName = "voa-bar"

ThisBuild / scalaVersion := "3.7.4"
ThisBuild / majorVersion := 1
ThisBuild / scalafmtFailOnErrors := true
ThisBuild / semanticdbEnabled := true

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    maintainer := "voa.service.optimisation@digital.hmrc.gov.uk",
    PlayKeys.playDefaultPort := 8447,
    libraryDependencies ++= AppDependencies.appDependencies,
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Wconf:msg=Flag .* set repeatedly:s",
    scalacOptions += "-Wconf:msg=Implicit parameters should be provided with a \\`using\\` clause&src=views/.*:s",
    javaOptions += "-XX:+EnableDynamicAgentLoading",
    Test / fork := true
  )

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice)
  .settings(itSettings())
  .settings(libraryDependencies ++= AppDependencies.itDependencies)

addCommandAlias("scalastyle", ";scalafmtAll;scalafmtSbt;it/test:scalafmt;scalafixAll")
