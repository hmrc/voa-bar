import sbt.*
import play.core.PlayVersion

object Dependencies {

  private val bootstrapVersion = "8.1.0"
  private val hmrcMongoVersion = "1.6.0"
  private val autoBarsXsdVersion = "9.10.0"
  private val jacksonModuleScalaVersion = "2.16.0"
  private val guiceUtilsVersion = "6.0.0"
  private val catsEffectVersion = "3.5.2"
  private val xercesVersion = "2.12.2"
  private val apachePOIVersion = "5.2.5"

  // Test dependencies
  private val scalaTestPlusPlayVersion = "7.0.0"
  private val scalaTestVersion = "3.2.17"
  private val testPlusScalaCheckVersion = "3.2.17.0"
  private val mockitoScalatestVersion = "1.17.30"
  private val wiremockVersion = "2.35.0"
  private val xmlunitVersion = "2.9.1"
  private val flexMarkVersion = "0.64.8"

  lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  private val compile = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "uk.gov.hmrc"                  %% "autobars-xsd"              % autoBarsXsdVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % jacksonModuleScalaVersion,
    "net.codingwell"               %% "scala-guice"               % guiceUtilsVersion,
    "org.typelevel"                %% "cats-effect"               % catsEffectVersion,
    "xerces"                       % "xercesImpl"                 % xercesVersion,
    "org.apache.poi"               % "poi"                        % apachePOIVersion
  )

  private def test(scope: String = "test,it") = Seq(
    "org.scalatestplus.play"       %% "scalatestplus-play"        % scalaTestPlusPlayVersion % scope,
    "org.playframework"            %% "play-test"                 % PlayVersion.current % scope,
    "org.scalatest"                %% "scalatest"                 % scalaTestVersion % scope,
    "org.scalatestplus"            %% "scalacheck-1-17"           % testPlusScalaCheckVersion % scope,
    "org.mockito"                  %% "mockito-scala-scalatest"   % mockitoScalatestVersion % scope,
    "com.github.tomakehurst"       % "wiremock-jre8"              % wiremockVersion % scope,
    "org.xmlunit"                  % "xmlunit-core"               % xmlunitVersion % scope,
    "com.vladsch.flexmark"         % "flexmark-all"               % flexMarkVersion % scope // for scalatest 3.2.x
  )

}
