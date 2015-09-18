organization := "com.pagerduty"

name := "eris-widerow"

scalaVersion := "2.10.4"

// Prevents logging configuration from being included in the test jar.
mappings in (Test, packageBin) ~= { _.filterNot(_._2.endsWith("logback-test.xml")) }

// Dependencies in this configuration are not exported.
ivyConfigurations += config("transient").hide

fullClasspath in Test ++= update.value.select(configurationFilter("transient"))

libraryDependencies ++= Seq(
  "com.pagerduty" %% "eris-core" % "1.3.0" % "compile->compile;test->test",
  "com.pagerduty" %% "widerow" % "0.4.3")

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.0.13" % "transient",
  "org.scalatest" %% "scalatest" % "2.2.4" % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2" % Test,
  "org.scalacheck" %% "scalacheck" % "1.12.2" % Test)
