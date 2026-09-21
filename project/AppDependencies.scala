import sbt._

object AppDependencies {

  val bootstrapVersion = "10.7.0"
  val hmrcDomainVersion = "13.0.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-frontend-play-30" % bootstrapVersion,
    "uk.gov.hmrc"   %% "domain-play-30"             % hmrcDomainVersion,
    "uk.gov.hmrc"   %% "play-frontend-hmrc-play-30" % "13.14.0",
    "org.typelevel" %% "cats-core"                  % "2.13.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-test-play-30" % bootstrapVersion % Test,
    "org.scalactic" %% "scalactic"              % "3.2.19"         % Test,
    "org.jsoup"      % "jsoup"                  % "1.21.1"         % Test
  )
}
