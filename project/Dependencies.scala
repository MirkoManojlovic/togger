object Libraries {
  case object org {
    val `org.scalatest` = "org.scalatest"
    val scalatest = "scalatest"
    val `scalatest-version` = "3.2.18"

    val `org.scalatestplus` = "org.scalatestplus"
    val `scalacheck-1-15` = "scalacheck-1-15"
    val `scalacheck1-15-version` = "3.2.9.0"

    val `org.scalatestplus.play` = "org.scalatestplus.play"
    val `scalatestplus-play` = "scalatestplus-play"
    val `scalatestplus-play-version` = "5.0.0"

    val `org.scalamock` = "org.scalamock"
    val scalamock = "scalamock"
    val `scalamock-version` = "6.0.0"

    val `org.typelevel` = "org.typelevel"
    val cats = "cats"
    val `cats-core` = "cats-core"
    val `cats-version` = "2.9.0"
    val `cats-effect` = "cats-effect"
    val `cats-effect-version` = "3.5.4"
    val mouse = "mouse"
    val `mouse-version` = "1.2.1"

    val `cats-effect-testing-scalatest` = "cats-effect-testing-scalatest"
    val `cats-effect-testing-scalatest-version` = "1.7.0"

    val `org.http4s` = "org.http4s"
    val `http4s-dsl` = "http4s-dsl"
    val `http4s-ember-server` = "http4s-ember-server"
    val `http4s-ember-client` = "http4s-ember-client"
    val `http4s-circe` = "http4s-circe"
    val `http4s-version` = "0.23.16"

    val `org.liquibase` = "org.liquibase"
    val `liquibase-core` = "liquibase-core"
    val `liquibase-version` = "4.18.0"

    val `org.postgresql` = "org.postgresql"
    val postgresql = "postgresql"
    val `postgresql-version` = "42.5.1"

    val `org.tpolecat` = "org.tpolecat"
    val `doobie-version` = "1.0.0-RC6"

    val `doobie-hikari` = "doobie-hikari"
    val `doobie-core` = "doobie-core"
    val `doobie-scalatest` = "doobie-scalatest"
    val `doobie-postgres` = "doobie-postgres"

    val `org.slf4j` = "org.slf4j"
    val `slf4j-api` = "slf4j-api"
    val `slf4-api-version` = "1.7.36"

    val `com.github.pureconfig` = "com.github.pureconfig"
    val pureconfig = "pureconfig"
    val `pureconfig-version` = "0.17.3"

    val `org.scala-lang` = "org.scala-lang"
    val `scala-reflect` = "scala-reflect"
    // last scala3-library release that still ships Scala-3-only class files (3.8.x publishes an empty jar)
    val `scala3-library-version` = "3.7.4"

    val `org.scala-js` = "org.scala-js"
    val `scala-js-macrotask-executor` = "scala-js-macrotask-executor"
    val `scala-js-macrotask-executor-version` = "1.1.1"
  }

  case object com {
    val `com.lihaoyi` = "com.lihaoyi"
    val upickle = "upickle"
    val `upickle-version` = "1.3.8"

    val `ammonite-ops` = "ammonite-ops"
    val `ammonite-ops-version` = "2.4.1"

    val `os-lib` = "os-lib"
    val `os-lib-version` = "0.7.8"

    val `com.beachape` = "com.beachape"
    val enumeratum = "enumeratum"
    val `enumeration-version` = "1.7.4"

    val `com.raquo` = "com.raquo"
    val domtestutils = "domtestutils"
    val `domtestutils-version` = "0.15.1"

    val laminar = "laminar"
    val `laminar-version` = "17.1.0"

    val domtypes = "domtypes"
    val `domtypes-version` = "0.14.3"

    val waypoint = "waypoint"
    val `waypoint-version` = "8.0.1"

    val `com.softwaremill.sttp.client3` = "com.softwaremill.sttp.client3"
    val core = "core"
    val `client3-version` = "3.11.0"

    val `com.softwaremill.sttp.ai` = "com.softwaremill.sttp.ai"
    val openai = "openai"
    val `sttp-ai-version` = "0.12.0"

    val `com.softwaremill.sttp.client4` = "com.softwaremill.sttp.client4"
    val `fs2` = "fs2"
    val `client4-version` = "4.1.0"

    val `com.github.japgolly.scalacss` = "com.github.japgolly.scalacss"
    val `scalacss-version` = "1.0.0"

    val `com.outr` = "com.outr"
    val scribe = "scribe"
    val `scribe-slf4j` = "scribe-slf4j"
    val `scribe-cats` = "scribe-cats"
    val `scribe-file` = "scribe-file"
    val `scribe-version` = "3.15.2"
    val scalapass = "scalapass"
    val `scalapass-version` = "1.2.5"

    val `com.softwaremill.sttp.tapir` = "com.softwaremill.sttp.tapir"
    val `tapir-core` = "tapir-core"
    val `tapir-http4s-server` = "tapir-http4s-server"
    val `tapir-http4s-client` = "tapir-http4s-client"
    val `tapir-json-circe` = "tapir-json-circe"
    val `tapir-sttp-client` = "tapir-sttp-client"
    val `tapir-version` = "1.14.0"

    val `tapir-swagger-ui-bundle` = "tapir-swagger-ui-bundle"
    val `tapir-redoc-bundle` = "tapir-redoc-bundle"

    // Tapir open api
    val `com.softwaremill.sttp.apispec` = "com.softwaremill.sttp.apispec"
    val `tapir-openapi-docs` = "tapir-openapi-docs"
    val `openapi-circe` = "openapi-circe"
    val `openapi-circe-yaml` = "openapi-circe-yaml"
    val `openapi-version` = "0.12.0"

    val `com.opentable.components` = "com.opentable.components"
    val `otj-pg-embedded` = "otj-pg-embedded"
    val `otj-pg-embedded-version` = "1.0.1"

//    val `com.sendgrid` = "com.sendgrid"
//    val `sendgrid-java` = "sendgrid-java"
//    val `sendgrid-version` = "4.9.3"

    val `com.github.jwt-scala` = "com.github.jwt-scala"
    val `jwt-circe` = "jwt-circe"
    val `jwt-circe-version` = "9.4.4"

    val `com.fasterxml.uuid` = "com.fasterxml.uuid"
    val `java-uuid-generator` = "java-uuid-generator"
    val `java-uuid-generator-version` = "5.2.0"
  }

  case object io {
    val `io.circe` = "io.circe"
    val `circe-core` = "circe-core"
    val `circe-generic` = "circe-generic"
    val `circe-parser` = "circe-parser"
    val `circe-generic-extras` = "circe-generic-extras"
    val `circe-version` = "0.14.3"

    val `io.github.cquiroz` = "io.github.cquiroz"
    val `scala-java-time` = "scala-java-time"
    val `scala-java-time-version` = "2.5.0"

    val `io.github.edadma` = "io.github.edadma"
    val markdown = "markdown"
    val `markdown-version` = "0.4.9"

    val `io.getquill` = "io.getquill"
    val `quill-doobie` = "quill-doobie"
    val `quill-doobie-version` = "4.8.4"

    val `io.scalaland` = "io.scalaland"
    val chimney = "chimney"
    val `chimney-version` = "0.7.5"
  }

}
