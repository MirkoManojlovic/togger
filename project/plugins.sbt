addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.12.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.9")

addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.7")

// jsdom test environment for the frontend (Laminar components need a DOM). The env still calls
// `virtualConsole.sendTo`, dropped in jsdom 24, so the npm `jsdom` dependency stays pinned below 24.
// There is no Scala 3 build of the env, so the 2.13 one is used; its Scala.js dependencies are already provided
// by sbt-scalajs itself (Scala 3), so the 2.13 copies are excluded to avoid conflicting cross-version suffixes.
libraryDependencies += ("org.scala-js" % "scalajs-env-jsdom-nodejs" % "1.1.0")
  .cross(CrossVersion.for3Use2_13)
  .excludeAll(
    ExclusionRule("org.scala-js", "scalajs-env-nodejs_2.13"),
    ExclusionRule("org.scala-js", "scalajs-js-envs_2.13"),
    ExclusionRule("org.scala-js", "scalajs-logging_2.13")
  )
