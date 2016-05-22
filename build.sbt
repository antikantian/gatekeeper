name := "gatekeeper"

organization := "co.quine"

version := "0.0.2"

scalaVersion := "2.11.8"

isSnapshot := true

publishTo := Some("Quine snapshots" at "s3://snapshots.repo.quine.co")

resolvers ++= Seq[Resolver](
  "Quine Releases"                   at "s3://releases.repo.quine.co",
  "Quine Snapshots"                  at "s3://snapshots.repo.quine.co",
  "Typesafe repository snapshots"    at "http://repo.typesafe.com/typesafe/snapshots/",
  "Typesafe repository releases"     at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype repo"                    at "https://oss.sonatype.org/content/groups/scala-tools/",
  "Sonatype releases"                at "https://oss.sonatype.org/content/repositories/releases",
  "Sonatype snapshots"               at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype staging"                 at "http://oss.sonatype.org/content/repositories/staging",
  "Sonatype release Repository"      at "http://oss.sonatype.org/service/local/staging/deploy/maven2/",
  "Java.net Maven2 Repository"       at "http://download.java.net/maven/2/",
  "Spray repo"                       at "http://repo.spray.io"
)

lazy val versions = new {
  val akka = "2.4.3"
  val nscalatime = "2.12.0"
  val config = "1.3.0"
  val redis = "1.6.0"
}

libraryDependencies ++= Seq(
  "com.github.nscala-time"      %% "nscala-time" % versions.nscalatime,
  "com.github.etaty"            %% "rediscala" % versions.redis,
  "com.typesafe"                 % "config" % versions.config,
  "com.typesafe.akka"           %% "akka-actor" % versions.akka,
  "org.scalaj"                  %% "scalaj-http" % "2.3.0",
  "io.argonaut"                 %% "argonaut" % "6.1"
)

evictionWarningOptions in update := EvictionWarningOptions.default
  .withWarnTransitiveEvictions(false)
  .withWarnDirectEvictions(false)
  .withWarnScalaVersionEviction(false)
    