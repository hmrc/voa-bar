import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.itSettings

val appName = "voa-bar"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always // Resolves versions conflict

ThisBuild / scalaVersion := "3.3.3"
ThisBuild / majorVersion := 1

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*filters.*;.*models.*;.*handlers.*;.*components.*;.*repositories.*;.*RepoTestController.*;" +
      ".*BuildInfo.*;.*javascript.*;.*Routes.*;.*GuiceInjector;.*WebBarsService;.*BillingAuthorities;",
    ScoverageKeys.coverageMinimumStmtTotal := 71.5,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
  .settings(
    PlayKeys.playDefaultPort := 8447,
    libraryDependencies ++= AppDependencies.appDependencies,
    scalacOptions += "-Wconf:src=routes/.*:s",
    retrieveManaged := true,
    Test / fork := true
  )

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice)
  .settings(itSettings())
  .settings(libraryDependencies ++= AppDependencies.itDependencies)
  .settings(
    scalafmtFailOnErrors := true,
    semanticdbEnabled := true,
    wartremoverExcluded ++= (Compile / routes).value,
    wartremoverWarnings ++= Warts.allBut(Wart.Equals),
    wartremoverErrors ++= Warts.allBut(Wart.Equals)
  )

addCommandAlias("scalastyle", ";scalafmtAll;scalafmtSbt;it/test:scalafmt;scalafixAll")
