import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object Dependencies {

  private val mockitoAllVersion = "1.10.19"
  private val scalaTestPlusPlayVersion = "3.1.3"
  private val httpCachingClientVersion = "9.5.0-play-26"
  private val reactiveMongoVersion = "8.0.0-play-26"
  private val bootstrapVersion = "4.0.0"
  private val autobarsXsdVersion = "9.1.0"
  private val guiceUtilsVersion = "4.2.9"
  private val saxonHeVersion = "9.9.1-7"
  private val xercesVersion = "2.12.0"

  lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    "uk.gov.hmrc" %% "simple-reactivemongo"           % reactiveMongoVersion,
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26"            % bootstrapVersion,
    "uk.gov.hmrc" %% "autobars-xsd"                 % autobarsXsdVersion,
    "uk.gov.hmrc" %% "http-caching-client"          % httpCachingClientVersion,
    "net.codingwell" %% "scala-guice"               % guiceUtilsVersion,
    "org.typelevel" %% "cats-effect"                % "2.1.4",
    "net.sf.saxon" % "Saxon-HE"                     % saxonHeVersion,
    "xerces" % "xercesImpl"                         % xercesVersion,
    "org.eclipse.persistence" % "org.eclipse.persistence.moxy" % "2.6.9",
    "io.inbot" % "inbot-utils" % "1.28"
  )

  def test(scope: String = "test,it") = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusPlayVersion % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.mockito" % "mockito-all" % mockitoAllVersion % scope,
    "org.scalacheck" %% "scalacheck" % "1.14.3" % scope,
    "com.github.tomakehurst" % "wiremock-jre8" % "2.26.3" % scope,
    "org.xmlunit" % "xmlunit-core" % "2.8.2" % scope
    //"commons-io" % "commons-io" % "2.8.0" % scope
  )

}
