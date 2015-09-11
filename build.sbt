organization := "com.pagerduty"

name := "eris-widerow"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.pagerduty" %% "eris-core" % "1.0.0" % "compile->compile;test->test",
  "com.pagerduty" %% "widerow" % "0.4.2")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2" % Test,
  "org.scalacheck" %% "scalacheck" % "1.12.2" % Test)
