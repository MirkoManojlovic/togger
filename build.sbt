import Libraries.com.*
import Libraries.io.*
import Libraries.org.*
import org.typelevel.scalacoptions.ScalacOptions
import sbt.Keys.libraryDependencies

ThisBuild / scalaVersion := "2.13.18"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.togger"
ThisBuild / organizationName := "Togger"
ThisBuild / javacOptions ++= Seq("-source", "21", "-target", "21")

Global / tpolecatExcludeOptions ++= Set(
  ScalacOptions.warnNumericWiden,
  ScalacOptions.fatalWarnings
)

Global / onChangedBuildSource := ReloadOnSourceChanges

val Cctt: String =
  "compile->compile;test->test"

lazy val frontendCore = (project in file("f-core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "frontend-core",
    libraryDependencies ++= Seq(
      // HTML
      `com.raquo` %%% laminar % `laminar-version`,
      `com.raquo` %%% waypoint % `waypoint-version`,
      `com.github.japgolly.scalacss` %%% core % `scalacss-version`,

      // Cats
      `org.typelevel` %%% `cats-effect` % `cats-effect-version`,
      `org.typelevel` %%% `cats-core` % `cats-version`,

      // Circe
      `io.circe` %%% `circe-core` % `circe-version`,
      `io.circe` %%% `circe-generic` % `circe-version`,
      `io.circe` %%% `circe-parser` % `circe-version`,
      `io.circe` %%% `circe-generic-extras` % `circe-version`,
      // Enum
      `com.beachape` %%% enumeratum % `enumeration-version`,
      // Java time
      `io.github.cquiroz` %%% `scala-java-time` % `scala-java-time-version`,

      // FIX: REMOVE THIS
      `com.softwaremill.sttp.tapir` %%% `tapir-sttp-client` % `tapir-version`,
      `com.softwaremill.sttp.client3` %%% cats % `client3-version`,

      // Chimney
      `io.scalaland` %%% chimney % `chimney-version`,

      // Futures
      `org.scala-js` %%% `scala-js-macrotask-executor` % `scala-js-macrotask-executor-version`,
      // Test
      `org.scalatest` %%% scalatest % `scalatest-version` % Test,
      `org.scalamock` %%% scalamock % `scalamock-version` % Test,
      `org.scalatestplus` %%% `scalacheck-1-15` % `scalacheck1-15-version` % Test,
      // Logging
      `com.outr` %%% scribe % `scribe-version`
    )
  )
  .dependsOn(httpEndpoints.js % Cctt)

val fastLinkOutputDir = taskKey[String]("output dir for frontend app `npm run dev`")
val fullLinkOutputDir = taskKey[String]("output dir for frontent app `npm run build`")

lazy val frontendMain = (project in file("f-main"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "frontend-main",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
      //        .withModuleSplitStyle(
      //          ModuleSplitStyle.SmallModulesFor(List("com.togger")))
      //       .withModuleSplitStyle(ModuleSplitStyle.SmallestModules)
      /* .withModuleSplitStyle( */
      /*   // TODO: find smallest for package */
      /*   ModuleSplitStyle.SmallestModules */
      /* ) */
    },
    fastLinkOutputDir := {
      // Ensure that fastLinkJS has run, then return its output directory
      (Compile / fastLinkJS).value
      (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.getAbsolutePath
    },
    fullLinkOutputDir := {
      // Ensure that fullLinkJS has run, then return its output directory
      (Compile / fullLinkJS).value
      (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value.getAbsolutePath
    }
  )
  .dependsOn(frontendCore % Cctt)

lazy val backendCore = (project in file("b-05-core"))
  .settings(
    name := "business-core",
    dependencyOverrides += `org.slf4j` % `slf4j-api` % `slf4-api-version`,
    libraryDependencies ++= Seq(
      // cats
      `org.typelevel` %% `cats-core` % `cats-version`,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Doobie
      `org.tpolecat` %% `doobie-core` % `doobie-version`,
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test
    )
  )
  .dependsOn(tgCommon.jvm % Cctt)

lazy val backendMain = (project in file("b-07-main"))
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    name := "backend-main",
    dependencyOverrides += `org.slf4j` % `slf4j-api` % `slf4-api-version`,
    libraryDependencies ++= Seq(
      // Http4s
      `org.http4s` %% `http4s-ember-server` % `http4s-version`,
      `org.http4s` %% `http4s-ember-client` % `http4s-version`,
      `org.http4s` %% `http4s-dsl` % `http4s-version`,
      `org.http4s` %% `http4s-circe` % `http4s-version`,
      // Tapir
      `com.softwaremill.sttp.tapir` %% `tapir-core` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-http4s-server` % `tapir-version`,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Http API docs
      `com.softwaremill.sttp.tapir` %% `tapir-swagger-ui-bundle` % `tapir-version`,
      // Liquibase
      `org.liquibase` % `liquibase-core` % `liquibase-version`,
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      // TODO: check if this is needed
      "com.lihaoyi" %% "sourcecode" % "0.4.2"
    )
  )
  .dependsOn(backendDeliveryHttp4s % Cctt)
  .dependsOn(backendDatabase % Cctt)
  .dependsOn(backendAuth % Cctt)
  .dependsOn(backendDiskSync % Cctt)

lazy val backendDiskSync = (project in file("b-05-disk-sync"))
  .settings(
    name := "backend-disk-sync",
    dependencyOverrides += `org.slf4j` % `slf4j-api` % `slf4-api-version`,
    libraryDependencies ++= Seq(
      // Postgres
      `com.opentable.components` % `otj-pg-embedded` % `otj-pg-embedded-version` % Test,
      `org.postgresql` % `postgresql` % `postgresql-version`,
      `org.tpolecat` %% `doobie-core` % `doobie-version`,
      `org.tpolecat` %% `doobie-postgres` % `doobie-version`,
      // Enumeration
      `com.beachape` %% `enumeratum` % `enumeration-version`,
      // Liquibase
      `org.liquibase` % `liquibase-core` % `liquibase-version`,
      // cats
      `org.typelevel` %% `cats-core` % `cats-version`,
      // circe
      `io.circe` %% `circe-core` % `circe-version`,
      `io.circe` %% `circe-generic` % `circe-version`,
      `io.circe` %% `circe-parser` % `circe-version`,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Files
      "com.lihaoyi" %% "os-lib" % "0.11.6",

      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      `org.tpolecat` %% `doobie-scalatest` % `doobie-version` % Test
    )
  )
  .dependsOn(tgCommon.jvm % Cctt)
  .dependsOn(backendCore % Cctt)
  .dependsOn(backendCommon % Cctt)
  .dependsOn(backendAuth % Cctt)

lazy val backendDatabase = (project in file("b-05-database"))
  .settings(
    name := "backend-database",
    test / parallelExecution := false, // because I'm using devenv and one postgres for testing I need tests to run seq
    Test / test := (Test / test).dependsOn(backendAuth / Test / test).value, // run backendAuth tests first, never concurrently, same shared postgres
    dependencyOverrides += `org.slf4j` % `slf4j-api` % `slf4-api-version`,
    libraryDependencies ++= Seq(
      // Postgres
      `com.opentable.components` % `otj-pg-embedded` % `otj-pg-embedded-version` % Test,
      `org.postgresql` % `postgresql` % `postgresql-version`,
      `org.tpolecat` %% `doobie-core` % `doobie-version`,
      `org.tpolecat` %% `doobie-postgres` % `doobie-version`,
      // Enumeration
      `com.beachape` %% `enumeratum` % `enumeration-version`,
      // Liquibase
      `org.liquibase` % `liquibase-core` % `liquibase-version`,
      // cats
      `org.typelevel` %% `cats-core` % `cats-version`,
      // circe
      `io.circe` %% `circe-core` % `circe-version`,
      `io.circe` %% `circe-generic` % `circe-version`,
      `io.circe` %% `circe-parser` % `circe-version`,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      `org.tpolecat` %% `doobie-scalatest` % `doobie-version` % Test
    )
  )
  .dependsOn(tgCommon.jvm % Cctt)
  .dependsOn(backendCore % Cctt)
  .dependsOn(backendCommon % Cctt)
  .dependsOn(backendAuth % Cctt)

lazy val backendDeliveryHttp4s = (project in file("b-06-delivery-http4s"))
  .enablePlugins(JavaServerAppPackaging)
  .settings(
    name := "bu-backend-delivery",
    dependencyOverrides += `org.slf4j` % `slf4j-api` % `slf4-api-version`,
    libraryDependencies ++= Seq(
      // Http4s
      `org.http4s` %% `http4s-ember-server` % `http4s-version`,
      `org.http4s` %% `http4s-ember-client` % `http4s-version`,
      `org.http4s` %% `http4s-dsl` % `http4s-version`,
      `org.http4s` %% `http4s-circe` % `http4s-version`,
      // Tapir
      `com.softwaremill.sttp.tapir` %% `tapir-core` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-http4s-server` % `tapir-version`,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Files
      "com.lihaoyi" %% "os-lib" % "0.11.6",
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test
    )
  )
  .dependsOn(httpEndpoints.jvm % Cctt)
  .dependsOn(tgCommon.jvm % Cctt)
  .dependsOn(backendCore % Cctt)
  .dependsOn(backendDiskSync % Cctt)

//  .dependsOn(backendCommon % Cctt)

lazy val httpEndpoints = (crossProject(JSPlatform, JVMPlatform) in file("c-03-http-endpoints"))
  .settings(
    name := "http-endpoints",
    libraryDependencies ++= Seq(
      // Tapir
      `com.softwaremill.sttp.tapir` %% `tapir-core` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-json-circe` % `tapir-version`,
      // Cats
      `org.typelevel` %% `cats-effect` % `cats-effect-version`,
      // Circe
      `io.circe` %%% `circe-core` % `circe-version`,
      `io.circe` %%% `circe-generic` % `circe-version`,
      `io.circe` %%% `circe-parser` % `circe-version`,
      // Test
      `org.scalatest` %%% scalatest % `scalatest-version` % Test,
      `org.scalamock` %%% scalamock % `scalamock-version` % Test,
      `org.scalatestplus` %%% `scalacheck-1-15` % `scalacheck1-15-version` % Test,
      // Http API docs
      `com.softwaremill.sttp.tapir` %% `tapir-swagger-ui-bundle` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-redoc-bundle` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-openapi-docs` % `tapir-version`,
      `com.softwaremill.sttp.apispec` %% `openapi-circe` % `openapi-version`,
      `com.softwaremill.sttp.apispec` %% `openapi-circe-yaml` % `openapi-version`,
      // Logging
      `com.outr` %%% scribe % `scribe-version`
    )
  )
  .dependsOn(tgCommon % Cctt)

lazy val tgCommon = (crossProject(JSPlatform, JVMPlatform) in file("c-01-common"))
  .settings(
    name := "tg-common",
    libraryDependencies ++= Seq(
      // Enumeration
      `com.beachape` %%% enumeratum % `enumeration-version`,
      // cats
      `org.typelevel` %%% `cats-core` % `cats-version`,
      `org.typelevel` %%% `cats-effect` % `cats-effect-version`,
      `org.typelevel` %%% `cats-effect-testing-scalatest` % `cats-effect-testing-scalatest-version` % Test,
      // circe
      `io.circe` %%% `circe-core` % `circe-version`,
      `io.circe` %%% `circe-generic` % `circe-version`,
      `io.circe` %%% `circe-parser` % `circe-version`,
      // Tapir
      `com.softwaremill.sttp.tapir` %%% `tapir-core` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %%% `tapir-json-circe` % `tapir-version`,
      // JWT
      `com.github.jwt-scala` %%% `jwt-circe` % `jwt-circe-version`,
      // Test
      `org.scalatest` %%% scalatest % `scalatest-version` % Test,
      `org.scalamock` %%% scalamock % `scalamock-version` % Test,
      `org.scalatestplus` %%% `scalacheck-1-15` % `scalacheck1-15-version` % Test,
      // Logging
      `com.outr` %%% scribe % `scribe-version`,
      `com.outr` %% `scribe-file` % `scribe-version`,
      `com.outr` %%% `scribe-cats` % `scribe-version`
    )
  )
  .dependsOn(tgMacros % Cctt)

lazy val backendAuth = (project in file("b-auth-app-core"))
  .enablePlugins(JavaServerAppPackaging)
//  .settings(commonSettings)
  .settings(
    name := "backend-auth-app-core",
    test / parallelExecution := false, // because I'm using devenv and one postgres for testing I need tests to run seq
    dependencyOverrides += `org.slf4j` % `slf4j-api` % `slf4-api-version`,
    libraryDependencies ++= Seq(
      // Enumeration
      `com.beachape` %% `enumeratum` % `enumeration-version`,
      // Liquibase
      `org.liquibase` % `liquibase-core` % `liquibase-version`,
      // Postgres
//      `com.opentable.components` % `otj-pg-embedded` % `otj-pg-embedded-version` % Test,
      `org.postgresql` % `postgresql` % `postgresql-version`,
      `org.tpolecat` %% `doobie-hikari` % `doobie-version`,
      `org.tpolecat` %% `doobie-core` % `doobie-version`,
      `org.tpolecat` %% `doobie-postgres` % `doobie-version`,

      // Http4s
      `org.http4s` %% `http4s-ember-server` % `http4s-version`,
      `org.http4s` %% `http4s-ember-client` % `http4s-version`,
      `org.http4s` %% `http4s-dsl` % `http4s-version`,
      `org.http4s` %% `http4s-circe` % `http4s-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-http4s-client` % `tapir-version`,
      // Chimney
      `io.scalaland` %%% chimney % `chimney-version`,
      // Cats
      `org.typelevel` %% `cats-effect` % `cats-effect-version`,
      // Tapir
      `com.softwaremill.sttp.tapir` %% `tapir-core` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-http4s-server` % `tapir-version`,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      `com.opentable.components` % `otj-pg-embedded` % `otj-pg-embedded-version` % Test,
      // Passwords
      `com.outr` %% `scalapass` % `scalapass-version`,
      // JWT
      `com.github.jwt-scala` %% `jwt-circe` % `jwt-circe-version`,
      // Pureconfig
      `com.github.pureconfig` %% pureconfig % `pureconfig-version`,
      "com.lihaoyi" %% "os-lib" % "0.11.8"
    )
  )
  .dependsOn(tgCommon.jvm % Cctt)
  .dependsOn(httpEndpoints.jvm % Cctt)
  .dependsOn(backendCommon % Cctt)
  .dependsOn(backendCore % Cctt)

lazy val backendCommon = (project in file("b-05-common"))
  .settings(
    name := "backend-common",
    libraryDependencies ++= Seq(
      // Tapir
      `com.softwaremill.sttp.tapir` %% `tapir-core` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-http4s-server` % `tapir-version`,
      // Postgres
      `com.opentable.components` % `otj-pg-embedded` % `otj-pg-embedded-version` % Test,
      `org.postgresql` % `postgresql` % `postgresql-version`,
      `org.tpolecat` %% `doobie-hikari` % `doobie-version`,
      `org.tpolecat` %% `doobie-core` % `doobie-version`,
      `org.tpolecat` %% `doobie-postgres` % `doobie-version`,
      // Http4s
      `org.http4s` %% `http4s-ember-server` % `http4s-version`,
      `org.http4s` %% `http4s-ember-client` % `http4s-version`,
      `org.http4s` %% `http4s-dsl` % `http4s-version`,
      `org.http4s` %% `http4s-circe` % `http4s-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-http4s-client` % `tapir-version`,
      // Cats
      `org.typelevel` %% `cats-core` % `cats-version`,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test
    )
  )
  .dependsOn(tgCommon.jvm % Cctt)
  .dependsOn(httpEndpoints.jvm % Cctt)

lazy val tgMacros = (crossProject(JSPlatform, JVMPlatform) in file("c-00-tg-macros"))
  .settings(
    name := "tg-macros",
    libraryDependencies ++= Seq(
      `org.scala-lang` % `scala-reflect` % scalaVersion.value,
      // Test
      `org.scalatest` %%% scalatest % `scalatest-version` % Test,
      `org.scalamock` %%% scalamock % `scalamock-version` % Test,
      `org.scalatestplus` %%% `scalacheck-1-15` % `scalacheck1-15-version` % Test,
      // Logging
      `com.outr` %%% scribe % `scribe-version`
    )
  )
