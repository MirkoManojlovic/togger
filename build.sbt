import Libraries.com.*
import Libraries.io.*
import Libraries.org.*
import org.typelevel.scalacoptions.ScalacOptions
import sbt.Keys.libraryDependencies

ThisBuild / scalaVersion := "2.13.18"
ThisBuild / organization := "com.togger"
ThisBuild / organizationName := "Togger"
// The version (from git, see sbt-dynver) is part of the manifest of every jar, and sbt 2 passes jars between projects,
// so with a version that changes per commit the cache key of every project that depends on another one changes too and
// nothing is reused. The PR checks pin the version to keep their sbt cache useful.
ThisBuild / version ~= (gitVersion => sys.env.getOrElse("TOGGER_FIXED_VERSION", gitVersion))
ThisBuild / javacOptions ++= Seq("-source", "21", "-target", "21")

Global / tpolecatExcludeOptions ++= Set(ScalacOptions.warnNumericWiden) ++ ScalacOptions.fatalWarningOptions

Global / onChangedBuildSource := ReloadOnSourceChanges
// parallel DB specs each clone a database and open a pool; keep it well below the postgres connection limit
Global / concurrentRestrictions += Tags.limit(Tags.Test, 4)

// `io.github.edadma:markdown` (used by frontendCore) is a Scala 3 library and is consumed from Scala 2.13
// through the TASTy reader. It is built against the Scala 3 "Next" standard library, which ships as
// `scalajs-scalalib_2.13:3.x` (ScalaJS IR only) plus `scala-library:3.x` (JVM signatures). These overrides keep
// Scala 2.13 signatures on the compile classpath while letting the Next scalalib provide the ScalaJS IR:
//   - scala-library stays at our 2.13 version, so the SIP-51 check does not see a newer scala-library than the
//     compiler,
//   - scala3-library_3 is pinned to the last release that still ships Scala-3-only class files
//     (scala.annotation.internal.*, scala.runtime.*, ...); 3.8.x publishes an empty jar, and the ScalaJS variant
//     cannot be used because its IR would shadow the Next scalalib's.
ThisBuild / dependencyOverrides ++= Seq(
  `org.scala-lang` % "scala-library" % scalaVersion.value,
  `org.scala-lang` % "scala3-library_3" % `scala3-library-version`
)

val Cctt: String =
  "compile->compile;test->test"

// Applied to every ScalaJS project that compiles against or links `markdown`. Cross-version-shifted
// dependencies are not carried over an inter-project `dependsOn`, so frontendMain has to declare it as well or
// the library's ScalaJS IR is missing at link time.
lazy val markdownSettings: Seq[Setting[?]] = Seq(
  // `markdown` is a Scala 3 library; the TASTy reader lets a Scala 2.13 module consume it
  scalacOptions += "-Ytasty-reader",
  libraryDependencies ++= Seq(
    // Scala 3 artifact, resolved for 2.13 (see the dependencyOverrides note at the top of the build).
    // Its Scala 3 build of scala-java-time is dropped in favour of the 2.13 one already on this classpath,
    // otherwise both cross-versions of java.time end up in the link.
    (`io.github.edadma` %% markdown % `markdown-version`)
      .cross(CrossVersion.for2_13Use3)
      .excludeAll(ExclusionRule(organization = `io.github.cquiroz`)),
    // supplies the Scala-3-only class files the TASTy reader needs (scala.annotation.internal.* and friends)
    `org.scala-lang` % "scala3-library_3" % `scala3-library-version`
  )
)

lazy val frontendCore = (project in file("f-core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(markdownSettings)
  .settings(
    name := "frontend-core",
    // Laminar builds real DOM nodes, so component tests need a document
    Test / jsEnv := Def.uncached(new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()),
    libraryDependencies ++= Seq(
      // HTML
      `com.raquo` %% laminar % `laminar-version`,
      `com.raquo` %% waypoint % `waypoint-version`,
      `com.github.japgolly.scalacss` %% core % `scalacss-version`,

      // Cats
      `org.typelevel` %% `cats-effect` % `cats-effect-version`,
      `org.typelevel` %% `cats-core` % `cats-version`,

      // Circe
      `io.circe` %% `circe-core` % `circe-version`,
      `io.circe` %% `circe-generic` % `circe-version`,
      `io.circe` %% `circe-parser` % `circe-version`,
      `io.circe` %% `circe-generic-extras` % `circe-version`,
      // Enum
      `com.beachape` %% enumeratum % `enumeration-version`,
      // Java time
      `io.github.cquiroz` %% `scala-java-time` % `scala-java-time-version`,

      // FIX: REMOVE THIS
      `com.softwaremill.sttp.tapir` %% `tapir-sttp-client` % `tapir-version`,
      `com.softwaremill.sttp.client3` %% cats % `client3-version`,

      // Chimney
      `io.scalaland` %% chimney % `chimney-version`,

      // Futures
      `org.scala-js` %% `scala-js-macrotask-executor` % `scala-js-macrotask-executor-version`,
      // Test
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      `org.scalamock` %% scalamock % `scalamock-version` % Test,
      `org.scalatestplus` %% `scalacheck-1-15` % `scalacheck1-15-version` % Test,
      // Logging
      `com.outr` %% scribe % `scribe-version`
    )
  )
  .dependsOn(httpEndpoints.js % Cctt)

val fastLinkOutputDir = taskKey[String]("output dir for frontend app `npm run dev`")
val fullLinkOutputDir = taskKey[String]("output dir for frontent app `npm run build`")

lazy val frontendMain = (project in file("f-main"))
  .enablePlugins(ScalaJSPlugin)
  .settings(markdownSettings)
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
    fastLinkOutputDir := Def.uncached {
      // Ensure that fastLinkJS has run, then return its output directory
      (Compile / fastLinkJS).value
      (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.getAbsolutePath
    },
    fullLinkOutputDir := Def.uncached {
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

lazy val backendAi = (project in file("b-05-ai"))
  .settings(
    name := "backend-ai",
    dependencyOverrides += `org.slf4j` % `slf4j-api` % `slf4-api-version`,
    libraryDependencies ++= Seq(
      // sttp-ai (OpenAI)
      `com.softwaremill.sttp.ai` %% openai % `sttp-ai-version`,
      `com.softwaremill.sttp.ai` %% `fs2` % `sttp-ai-version`,
      `com.softwaremill.sttp.client4` %% `fs2` % `client4-version`,
      // cats
      `org.typelevel` %% `cats-core` % `cats-version`,
      `org.typelevel` %% `cats-effect` % `cats-effect-version`,
      // circe
      `io.circe` %% `circe-core` % `circe-version`,
      `io.circe` %% `circe-generic` % `circe-version`,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      `org.scalamock` %% scalamock % `scalamock-version` % Test,
      `org.typelevel` %% `cats-effect-testing-scalatest` % `cats-effect-testing-scalatest-version` % Test
    )
  )
  .dependsOn(backendCore % Cctt)

lazy val backendMain = (project in file("b-07-main"))
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    name := "backend-main",
    Test / parallelExecution := true, // every spec works on its own database cloned from togger-test (TgTestDatabase)
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
  .dependsOn(backendAi % Cctt)

lazy val backendDiskSync = (project in file("b-05-disk-sync"))
  .settings(
    name := "backend-disk-sync",
    Test / parallelExecution := false, // because I'm using devenv and one postgres for testing I need tests to run seq
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
    Test / parallelExecution := true, // every spec works on its own database cloned from togger-test (TgTestDatabase)
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
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test
    )
  )
  .dependsOn(httpEndpoints.jvm % Cctt)
  .dependsOn(tgCommon.jvm % Cctt)
  .dependsOn(backendCore % Cctt)
  .dependsOn(backendDiskSync % Cctt)
  .dependsOn(backendAi % Cctt)

//  .dependsOn(backendCommon % Cctt)

lazy val httpEndpoints = (crossProject(JSPlatform, JVMPlatform).in(file("c-03-http-endpoints")))
  .settings(
    name := "http-endpoints",
    libraryDependencies ++= Seq(
      // Tapir
      `com.softwaremill.sttp.tapir` %% `tapir-core` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-json-circe` % `tapir-version`,
      // Cats
      `org.typelevel` %% `cats-effect` % `cats-effect-version`,
      // Circe
      `io.circe` %% `circe-core` % `circe-version`,
      `io.circe` %% `circe-generic` % `circe-version`,
      `io.circe` %% `circe-parser` % `circe-version`,
      // Test
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      `org.scalamock` %% scalamock % `scalamock-version` % Test,
      `org.scalatestplus` %% `scalacheck-1-15` % `scalacheck1-15-version` % Test,
      // Logging
      `com.outr` %% scribe % `scribe-version`
    )
  )
  // `%%` carries the platform suffix in sbt 2, and the API docs libraries are only needed (and published) for the JVM
  .jvmSettings(
    libraryDependencies ++= Seq(
      // Http API docs
      `com.softwaremill.sttp.tapir` %% `tapir-swagger-ui-bundle` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-redoc-bundle` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-openapi-docs` % `tapir-version`,
      `com.softwaremill.sttp.apispec` %% `openapi-circe` % `openapi-version`,
      `com.softwaremill.sttp.apispec` %% `openapi-circe-yaml` % `openapi-version`
    )
  )
  .dependsOn(tgCommon % Cctt)

lazy val tgCommon = (crossProject(JSPlatform, JVMPlatform).in(file("c-01-common")))
  .settings(
    name := "tg-common",
    libraryDependencies ++= Seq(
      // Enumeration
      `com.beachape` %% enumeratum % `enumeration-version`,
      // java.time on ScalaJS
      `io.github.cquiroz` %% `scala-java-time` % `scala-java-time-version`,
      // cats
      `org.typelevel` %% `cats-core` % `cats-version`,
      `org.typelevel` %% `cats-effect` % `cats-effect-version`,
      `org.typelevel` %% `cats-effect-testing-scalatest` % `cats-effect-testing-scalatest-version` % Test,
      // circe
      `io.circe` %% `circe-core` % `circe-version`,
      `io.circe` %% `circe-generic` % `circe-version`,
      `io.circe` %% `circe-parser` % `circe-version`,
      // Tapir
      `com.softwaremill.sttp.tapir` %% `tapir-core` % `tapir-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-json-circe` % `tapir-version`,
      // JWT
      `com.github.jwt-scala` %% `jwt-circe` % `jwt-circe-version`,
      // Test
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      `org.scalamock` %% scalamock % `scalamock-version` % Test,
      `org.scalatestplus` %% `scalacheck-1-15` % `scalacheck1-15-version` % Test,
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-cats` % `scribe-version`
    )
  )
  // `%%` carries the platform suffix in sbt 2, and scribe-file only exists for the JVM
  .jvmSettings(libraryDependencies += `com.outr` %% `scribe-file` % `scribe-version`)
  .dependsOn(tgMacros % Cctt)

lazy val backendAuth = (project in file("b-auth-app-core"))
  .enablePlugins(JavaServerAppPackaging)
//  .settings(commonSettings)
  .settings(
    name := "backend-auth-app-core",
    Test / parallelExecution := true, // every spec works on its own database cloned from togger-test (TgTestDatabase)
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
      `io.scalaland` %% chimney % `chimney-version`,
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

lazy val tgMacros = (crossProject(JSPlatform, JVMPlatform).in(file("c-00-tg-macros")))
  .settings(
    name := "tg-macros",
    libraryDependencies ++= Seq(
      `org.scala-lang` % `scala-reflect` % scalaVersion.value,
      // Test
      `org.scalatest` %% scalatest % `scalatest-version` % Test,
      `org.scalamock` %% scalamock % `scalamock-version` % Test,
      `org.scalatestplus` %% `scalacheck-1-15` % `scalacheck1-15-version` % Test,
      // Logging
      `com.outr` %% scribe % `scribe-version`
    )
  )

// Scala 3 module on purpose: Chimp is Scala 3 only. It does not depend on any other Togger project and talks to the
// backend over HTTP only.
lazy val backendMcp = (project in file("b-08-mcp"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "backend-mcp",
    Compile / mainClass := Some("com.togger.mcp.McpApp"),
    // Chimp and its tapir/sttp dependencies are compiled with Scala 3.9, so this module needs a 3.9 compiler.
    scalaVersion := "3.9.0",
    Test / parallelExecution := false,
    // scalatest assertions are values; several of them in one test body are normal
    Test / scalacOptions += "-Wconf:msg=unused value of type org.scalatest.Assertion:s",
    // Replaces (does not extend) the ThisBuild overrides, which pin the Scala 2.13 scala-library and scala3-library
    // 3.7.4 for the rest of the build and would break a 3.9 module.
    dependencyOverrides := Seq(`org.slf4j` % `slf4j-api` % `slf4-api-version`),
    libraryDependencies ++= Seq(
      // Chimp (MCP server on top of Tapir)
      `com.softwaremill.chimp` %% `chimp-server` % `chimp-version`,
      `com.softwaremill.sttp.tapir` %% `tapir-netty-server-sync` % `tapir-version`,
      // Backend calls
      `com.softwaremill.sttp.client4` %% `sttp-client4-core` % `client4-version`,
      `io.circe` %% `circe-parser` % "0.14.14",
      // Logging
      `com.outr` %% scribe % `scribe-version`,
      `com.outr` %% `scribe-slf4j` % `scribe-version`,
      // Tests
      `org.scalatest` %% scalatest % `scalatest-version` % Test
    )
  )
