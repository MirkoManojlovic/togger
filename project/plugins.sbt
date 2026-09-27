addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.9")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.13.0")

addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.2")

// jsdom test environment for the frontend (Laminar components need a DOM). The env still calls
// `virtualConsole.sendTo`, dropped in jsdom 24, so the npm `jsdom` dependency stays pinned below 24.
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
