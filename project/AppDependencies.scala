import sbt.*
import play.core.PlayVersion

object AppDependencies {

  private val bootstrapVersion          = "9.11.0"
  private val hmrcMongoVersion          = "2.6.0"
  private val autoBarsXsdVersion        = "9.16.0"
  private val jacksonModuleScalaVersion = "2.19.0"
  private val guiceUtilsVersion         = "6.0.0" // Use 6.0.0 because 7.0.0 is not compatible with play-guice:3.0.7
  private val catsEffectVersion         = "3.6.1"
  private val jerichoHtmlVersion        = "3.4"
  private val httpComponentsVersion     = "4.5.14"
  private val xercesVersion             = "2.12.2"
  private val apachePOIVersion          = "5.4.1"

  // Test dependencies
  private val scalaTestPlusPlayVersion    = "7.0.1"
  private val scalaTestVersion            = "3.2.19"
  private val testPlusScalaCheckVersion   = "3.2.19.0"
  private val scalaTestPlusMockitoVersion = "3.2.19.0"
  private val wiremockVersion             = "3.13.0"
  private val xmlunitVersion              = "2.10.0"
  private val flexMarkVersion             = "0.64.8"

  private val compile = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "uk.gov.hmrc"                  %% "autobars-xsd"              % autoBarsXsdVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % jacksonModuleScalaVersion,
    "net.codingwell"               %% "scala-guice"               % guiceUtilsVersion,
    "org.typelevel"                %% "cats-effect"               % catsEffectVersion,
    "net.htmlparser.jericho"        % "jericho-html"              % jerichoHtmlVersion,
    "org.apache.httpcomponents"     % "httpmime"                  % httpComponentsVersion,
    "xerces"                        % "xercesImpl"                % xercesVersion,
    "org.apache.poi"                % "poi"                       % apachePOIVersion
  )

  private val commonTests = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusPlayVersion  % Test,
    "org.playframework"      %% "play-test"          % PlayVersion.current       % Test,
    "org.scalatest"          %% "scalatest"          % scalaTestVersion          % Test,
    "org.scalatestplus"      %% "scalacheck-1-18"    % testPlusScalaCheckVersion % Test,
    "com.vladsch.flexmark"    % "flexmark-all"       % flexMarkVersion           % Test // for scalatest 3.2.x
  )

  private val testOnly = Seq(
    "org.scalatestplus" %% "mockito-5-12" % scalaTestPlusMockitoVersion % Test,
    "org.xmlunit"        % "xmlunit-core" % xmlunitVersion              % Test
  )

  private val integrationTestOnly = Seq(
    "org.wiremock" % "wiremock" % wiremockVersion % Test
  )

  val appDependencies: Seq[ModuleID] = compile ++ commonTests ++ testOnly

  val itDependencies: Seq[ModuleID] = commonTests ++ integrationTestOnly

}
