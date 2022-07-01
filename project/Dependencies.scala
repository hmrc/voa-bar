import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object Dependencies {

  private val bootstrapVersion = "5.25.0"
  private val hmrcMongoVersion = "0.66.0"
  private val autoBarsXsdVersion = "9.5.0"
  private val httpCachingClientVersion = "9.6.0-play-28"
  private val guiceUtilsVersion = "5.1.0"
  private val catsEffectVersion = "3.3.12"
  private val saxonHeVersion = "11.3"
  private val xercesVersion = "2.12.2"
  private val persistenceMoxyVersion = "2.6.9"
  private val inbotUtilsVersion = "1.28"

  // Test dependencies
  private val scalaTestPlusPlayVersion = "5.1.0"
  private val scalaTestVersion = "3.2.10" // v3.2.10 provides scala-xml_2.13:1.3.0
  private val testPlusScalaCheckVersion = "3.2.10.0"
  private val mockitoScalatestVersion = "1.17.7"
  private val wiremockVersion = "2.27.2"
  private val xmlunitVersion = "2.9.0"
  private val flexmarkVersion = "0.64.0"

  lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-28"    % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"     % hmrcMongoVersion,
    "uk.gov.hmrc" %% "autobars-xsd"                 % autoBarsXsdVersion,
    "uk.gov.hmrc" %% "http-caching-client"          % httpCachingClientVersion,
    "net.codingwell" %% "scala-guice"               % guiceUtilsVersion,
    "org.typelevel" %% "cats-effect"                % catsEffectVersion,
    "net.sf.saxon" % "Saxon-HE"                     % saxonHeVersion,
    "xerces" % "xercesImpl"                         % xercesVersion,
    "org.eclipse.persistence" % "org.eclipse.persistence.moxy" % persistenceMoxyVersion,
    "io.inbot" % "inbot-utils" % inbotUtilsVersion
  )

  def test(scope: String = "test,it") = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusPlayVersion % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatest" %% "scalatest" % scalaTestVersion % scope,
    "org.scalatestplus" %% "scalacheck-1-15" % testPlusScalaCheckVersion % scope,
    "org.mockito" %% "mockito-scala-scalatest" % mockitoScalatestVersion % scope,
    "com.github.tomakehurst" % "wiremock-jre8" % wiremockVersion % scope,
    "org.xmlunit" % "xmlunit-core" % xmlunitVersion % scope,
    "com.vladsch.flexmark" % "flexmark-all" % flexmarkVersion % scope // for scalatest 3.1+
  )

}
