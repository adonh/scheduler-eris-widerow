organization := "com.pagerduty"

name := "eris-widerow"

scalaVersion := "2.11.12"

crossScalaVersions := Seq("2.10.4", "2.11.12")

resolvers += "bintray-pagerduty-oss-maven" at "https://dl.bintray.com/pagerduty/oss-maven"

// Prevents logging configuration from being included in the test jar.
mappings in (Test, packageBin) ~= { _.filterNot(_._2.endsWith("logback-test.xml")) }
mappings in (IntegrationTest, packageBin) ~= { _.filterNot(_._2.endsWith("logback-it.xml")) }

// Dependencies in this configuration are not exported.
ivyConfigurations += config("transient").hide

fullClasspath in Test ++= update.value.select(configurationFilter("transient"))

lazy val root = (project in file(".")).
  configs(IntegrationTest extend (Test)).
  settings(Defaults.itSettings: _*).
  settings(

  libraryDependencies ++= Seq(
    "com.pagerduty" %% "eris-core" % "3.0.1",
    "com.pagerduty" %% "widerow" % "0.5.2"),

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.0.13" % "transient",
    "com.pagerduty" %% "eris-core-tests" % "3.0.1" % Test,
    "org.scalatest" %% "scalatest" % "2.2.4" % "it,test",
    "org.scalamock" %% "scalamock-scalatest-support" % "3.2" % "it,test",
    "org.scalacheck" %% "scalacheck" % "1.12.2" % "it,test")
  )
