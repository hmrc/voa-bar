import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object Dependencies {

  private val httpCachingClientVersion = "9.5.0-play-28"
  private val reactiveMongoVersion = "8.0.0-play-28"
  private val bootstrapVersion = "5.16.0"
  private val autobarsXsdVersion = "9.1.0"
  private val guiceUtilsVersion = "5.0.2"
  private val catsEffectVersion = "3.2.9"
  private val saxonHeVersion = "9.9.1-7"
  private val xercesVersion = "2.12.0"
  private val persistenceMoxyVersion = "2.6.9"
  private val inbotUtilsVersion = "1.28"

  // Test dependencies
  private val scalaTestPlusPlayVersion = "5.0.0"
  private val pegdownVersion = "1.6.0"
  private val mockitoAllVersion = "1.10.19"
  private val scalacheckVersion = "1.14.3"
  private val wiremockVersion = "2.26.3"
  private val xmlunitVersion = "2.8.2"

  lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    "uk.gov.hmrc" %% "simple-reactivemongo"           % reactiveMongoVersion,
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
    "org.pegdown" % "pegdown" % pegdownVersion % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.mockito" % "mockito-all" % mockitoAllVersion % scope,
    "org.scalacheck" %% "scalacheck" % scalacheckVersion % scope,
    "com.github.tomakehurst" % "wiremock-jre8" % wiremockVersion % scope,
    "org.xmlunit" % "xmlunit-core" % xmlunitVersion % scope
  )

}
