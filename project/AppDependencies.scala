import sbt.*
import play.core.PlayVersion

object AppDependencies {

  private val bootstrapVersion = "8.2.0"
  private val hmrcMongoVersion = "1.6.0"
  private val autoBarsXsdVersion = "9.12.0"
  private val jacksonModuleScalaVersion = "2.16.0"
  private val guiceUtilsVersion = "6.0.0"
  private val catsEffectVersion = "3.5.2"
  private val jerichoHtmlVersion = "3.4"
  private val httpComponentsVersion = "4.5.14"
  private val xercesVersion = "2.12.2"
  private val apachePOIVersion = "5.2.5"

  // Test dependencies
  private val scalaTestPlusPlayVersion = "7.0.0"
  private val scalaTestVersion = "3.2.17"
  private val testPlusScalaCheckVersion = "3.2.17.0"
  private val mockitoScalatestVersion = "1.17.30"
  private val wiremockVersion = "3.3.1"
  private val xmlunitVersion = "2.9.1"
  private val flexMarkVersion = "0.64.8"

  private val compile = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "uk.gov.hmrc"                  %% "autobars-xsd"              % autoBarsXsdVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % jacksonModuleScalaVersion,
    "net.codingwell"               %% "scala-guice"               % guiceUtilsVersion,
    "org.typelevel"                %% "cats-effect"               % catsEffectVersion,
    "net.htmlparser.jericho"       % "jericho-html"               % jerichoHtmlVersion,
    "org.apache.httpcomponents"    % "httpmime"                   % httpComponentsVersion,
    "xerces"                       % "xercesImpl"                 % xercesVersion,
    "org.apache.poi"               % "poi"                        % apachePOIVersion
  )

  private val commonTests = Seq(
    "org.scalatestplus.play"       %% "scalatestplus-play"        % scalaTestPlusPlayVersion % Test,
    "org.playframework"            %% "play-test"                 % PlayVersion.current % Test,
    "org.scalatest"                %% "scalatest"                 % scalaTestVersion % Test,
    "org.scalatestplus"            %% "scalacheck-1-17"           % testPlusScalaCheckVersion % Test,
    "com.vladsch.flexmark"         % "flexmark-all"               % flexMarkVersion % Test // for scalatest 3.2.x
  )

  private val testOnly = Seq(
    "org.mockito"                  %% "mockito-scala-scalatest"   % mockitoScalatestVersion % Test,
    "org.xmlunit"                  % "xmlunit-core"               % xmlunitVersion % Test
  )

  private val integrationTestOnly = Seq(
    "org.wiremock"                 % "wiremock"                   % wiremockVersion % Test
  )

  val appDependencies: Seq[ModuleID] = compile ++ commonTests ++ testOnly

  val itDependencies: Seq[ModuleID] = commonTests ++ integrationTestOnly

}
