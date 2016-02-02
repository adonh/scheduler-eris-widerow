# eris-widerow [![Build Status](https://travis-ci.org/PagerDuty/eris-widerow.svg?branch=master)](https://travis-ci.org/PagerDuty/eris-widerow/builds)

This is an open source project!

## Description

Eris-widerow is an implementation of [WideRow API](https://github.com/PagerDuty/widerow) using [Eris driver](https://github.com/PagerDuty/eris-core).


## Installation

This library is published to PagerDuty Bintray OSS Maven repository:
```scala
resolvers += "bintray-pagerduty-oss-maven" at "https://dl.bintray.com/pagerduty/oss-maven"
```

Adding the dependency to your SBT build file:
```scala
libraryDependencies += "com.pagerduty" %% "eris-widerow" % "1.4.1"
```

## Contact

This library is primarily maintained by the Core Team at PagerDuty.

## Contributing

Contributions are welcome in the form of pull-requests based on the master branch. We ask that your changes are covered by unit tests.

Before opening a pull-request, remember to apply auto-formatting to your code. You can trigger auto-formatting by running `sbt test` or `sbt ";compile;test:compile"`.

## Release

Follow these steps to release a new version:
 - Update version.sbt in your PR
 - Update CHANGELOG.md in your PR
 - When the PR is approved, merge it to master, and delete the branch
 - Travis will run all tests, publish artifacts to Bintray, and create a new version tag in Github

## Changelog

See [CHANGELOG.md](./CHANGELOG.md)
