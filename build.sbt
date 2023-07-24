import sbt.Keys.*
import sbt.*
import scoverage.ScoverageKeys
import uk.gov.hmrc.*
import DefaultBuildSettings.{defaultSettings, integrationTestSettings, scalaSettings}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val appName = "voa-bar"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always // Resolves versions conflict

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*filters.*;.*models.*;.*handlers.*;.*components.*;.*repositories.*;.*RepoTestController.*;" +
      ".*BuildInfo.*;.*javascript.*;.*Routes.*;.*GuiceInjector;.*WebBarsService;.*BillingAuthorities;",
    ScoverageKeys.coverageMinimumStmtTotal := 87,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
  )
  .settings(majorVersion := 1 )
  .settings(scalaSettings *)
  .settings(defaultSettings() *)
  .settings(
    libraryDependencies ++= Dependencies.appDependencies,
    retrieveManaged := true,
    PlayKeys.playDefaultPort := 8447,
    scalaVersion := "2.13.11",
    DefaultBuildSettings.targetJvm := "jvm-11",
    scalacOptions += "-Wconf:src=routes/.*:s",
    Test / fork := true
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings) *)
  .settings(integrationTestSettings() *)
  .settings(
    IntegrationTest / fork := true
  )
