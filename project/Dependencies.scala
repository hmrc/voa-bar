import sbt._
import play.core.PlayVersion

object Dependencies {

  private val bootstrapVersion = "7.12.0"
  private val hmrcMongoVersion = "0.74.0"
  private val autoBarsXsdVersion = "9.7.0"
  private val httpCachingClientVersion = "10.0.0-play-28"
  private val jacksonModuleScalaVersion = "2.14.1"
  private val guiceUtilsVersion = "5.1.0"
  private val catsEffectVersion = "3.4.1"
  private val saxonHeVersion = "11.4"
  private val xercesVersion = "2.12.2"
  private val inbotUtilsVersion = "1.28"

  // Test dependencies
  private val scalaTestPlusPlayVersion = "5.1.0"
  private val scalaTestVersion = "3.2.14"
  private val testPlusScalaCheckVersion = "3.2.14.0"
  private val mockitoScalatestVersion = "1.17.12"
  private val wiremockVersion = "2.35.0"
  private val xmlunitVersion = "2.9.0"
  private val flexMarkVersion = "0.64.0"

  lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  private val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-28"    % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"     % hmrcMongoVersion,
    "uk.gov.hmrc" %% "autobars-xsd"                 % autoBarsXsdVersion,
    "uk.gov.hmrc" %% "http-caching-client"          % httpCachingClientVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonModuleScalaVersion,
    "net.codingwell" %% "scala-guice"               % guiceUtilsVersion,
    "org.typelevel" %% "cats-effect"                % catsEffectVersion,
    "net.sf.saxon" % "Saxon-HE"                     % saxonHeVersion,
    "xerces" % "xercesImpl"                         % xercesVersion,
    "io.inbot" % "inbot-utils" % inbotUtilsVersion
  )

  private def test(scope: String = "test,it") = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusPlayVersion % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatest" %% "scalatest" % scalaTestVersion % scope,
    "org.scalatestplus" %% "scalacheck-1-17" % testPlusScalaCheckVersion % scope,
    "org.mockito" %% "mockito-scala-scalatest" % mockitoScalatestVersion % scope,
    "com.github.tomakehurst" % "wiremock-jre8" % wiremockVersion % scope,
    "org.xmlunit" % "xmlunit-core" % xmlunitVersion % scope,
    "com.vladsch.flexmark" % "flexmark-all" % flexMarkVersion % scope // for scalatest 3.2.x
  )

}
