import sbt.*

object AppDependencies {

  private val bootstrapVersion          = "10.8.0"
  private val hmrcMongoVersion          = "2.13.0"
  private val autoBarsXsdVersion        = "9.20.0"
  private val jacksonModuleScalaVersion = "2.22.2"
  private val catsEffectVersion         = "3.7.0"
  private val jerichoHtmlVersion        = "3.4"
  private val httpComponentsVersion     = "4.5.14"
  private val xercesVersion             = "2.12.2"
  private val apachePOIVersion          = "5.5.1"

  // Test dependencies
  private val voTestVersion   = "0.6.0"
  private val wiremockVersion = "3.13.2"
  private val xmlunitVersion  = "2.13.0"

  private val compile = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "uk.gov.hmrc"                  %% "autobars-xsd"              % autoBarsXsdVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % jacksonModuleScalaVersion,
    "org.typelevel"                %% "cats-effect"               % catsEffectVersion,
    "net.htmlparser.jericho"        % "jericho-html"              % jerichoHtmlVersion,
    "org.apache.httpcomponents"     % "httpmime"                  % httpComponentsVersion,
    "xerces"                        % "xercesImpl"                % xercesVersion,
    "org.apache.poi"                % "poi"                       % apachePOIVersion
  )

  private val commonTests = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test
  )

  private val testOnly = Seq(
    "uk.gov.hmrc" %% "vo-unit-test" % voTestVersion  % Test,
    "org.xmlunit"  % "xmlunit-core" % xmlunitVersion % Test
  )

  private val integrationTestOnly = Seq(
    "uk.gov.hmrc" %% "vo-integration-test" % voTestVersion   % Test,
    "org.wiremock" % "wiremock"            % wiremockVersion % Test
  )

  val appDependencies: Seq[ModuleID] = compile ++ commonTests ++ testOnly

  val itDependencies: Seq[ModuleID] = commonTests ++ integrationTestOnly

}
