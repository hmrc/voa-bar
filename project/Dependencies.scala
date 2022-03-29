import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object Dependencies {

  private val httpCachingClientVersion = "9.6.0-play-28"
  private val reactiveMongoVersion = "8.0.0-play-28"
  private val bootstrapVersion = "5.21.0"
  private val autobarsXsdVersion = "9.1.0"
  private val guiceUtilsVersion = "5.0.2"
  private val catsEffectVersion = "3.3.8"
  private val saxonHeVersion = "11.2"
  private val xercesVersion = "2.12.2"
  private val persistenceMoxyVersion = "2.6.9"
  private val inbotUtilsVersion = "1.28"

  // Test dependencies
  private val scalaTestPlusPlayVersion = "5.1.0"
  private val scalaTestVersion = "3.2.10"
  private val testPlusScalaCheckVersion = "3.2.10.0"
  private val mockitoScalatestVersion = "1.17.5"
  private val wiremockVersion = "2.27.2"
  private val xmlunitVersion = "2.9.0"
  private val flexmarkVersion = "0.62.2"

  lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    "uk.gov.hmrc" %% "simple-reactivemongo"         % reactiveMongoVersion,
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-28"    % bootstrapVersion,
    "uk.gov.hmrc" %% "autobars-xsd"                 % autobarsXsdVersion,
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
