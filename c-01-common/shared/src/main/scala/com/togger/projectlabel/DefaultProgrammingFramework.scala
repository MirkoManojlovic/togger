package com.togger.projectlabel

import enumeratum._

sealed abstract class DefaultProgrammingFramework(val i: Int, val frameworkName: String, val description: String)
    extends EnumEntry

object DefaultProgrammingFramework extends Enum[DefaultProgrammingFramework] {
  case object RubyOnRails
      extends DefaultProgrammingFramework(0, "Ruby on Rails", "Full-stack MVC web framework for Ruby")
  case object Spring
      extends DefaultProgrammingFramework(1, "Spring", "Application framework and IoC container for Java/Kotlin")
  case object SpringBoot
      extends DefaultProgrammingFramework(2, "Spring Boot", "Convention-over-configuration layer on top of Spring")
  case object PlayFramework
      extends DefaultProgrammingFramework(3, "Play Framework", "Reactive web framework for Scala/Java")
  case object Http4s
      extends DefaultProgrammingFramework(4, "http4s", "Purely functional, typeful HTTP library/framework for Scala")
  case object Akka
      extends DefaultProgrammingFramework(5, "Akka", "Toolkit for building concurrent, distributed JVM applications")
  case object Django extends DefaultProgrammingFramework(6, "Django", "Full-stack web framework for Python")
  case object Flask extends DefaultProgrammingFramework(7, "Flask", "Lightweight web framework for Python")
  case object FastApi
      extends DefaultProgrammingFramework(8, "FastAPI", "Modern, high-performance Python web framework")
  case object ExpressJs
      extends DefaultProgrammingFramework(9, "Express.js", "Minimalist web framework for Node.js")
  case object NestJs
      extends DefaultProgrammingFramework(10, "NestJS", "Opinionated, TypeScript-first Node.js framework")
  case object Angular
      extends DefaultProgrammingFramework(11, "Angular", "TypeScript-based web application framework")
  case object React extends DefaultProgrammingFramework(12, "React", "JavaScript library for building user interfaces")
  case object VueJs
      extends DefaultProgrammingFramework(13, "Vue.js", "Progressive JavaScript framework for user interfaces")
  case object Svelte extends DefaultProgrammingFramework(14, "Svelte", "Compiler-based JavaScript UI framework")
  case object NextJs
      extends DefaultProgrammingFramework(15, "Next.js", "React framework for server-rendered/static web apps")
  case object Laravel extends DefaultProgrammingFramework(16, "Laravel", "Full-stack web framework for PHP")
  case object Symfony extends DefaultProgrammingFramework(17, "Symfony", "Component-based web framework for PHP")
  case object AspNetCore
      extends DefaultProgrammingFramework(18, "ASP.NET Core", "Cross-platform web framework for .NET")
  case object Phoenix
      extends DefaultProgrammingFramework(19, "Phoenix", "Web framework for Elixir, built on Erlang/OTP")
  case object Gin extends DefaultProgrammingFramework(20, "Gin", "Lightweight HTTP web framework for Go")
  case object Echo extends DefaultProgrammingFramework(21, "Echo", "Minimalist, high-performance web framework for Go")
  case object Actix extends DefaultProgrammingFramework(22, "Actix", "High-performance web framework for Rust")
  case object Axum extends DefaultProgrammingFramework(23, "Axum", "Ergonomic, modular web framework for Rust")
  case object Hibernate
      extends DefaultProgrammingFramework(24, "Hibernate", "Object-relational mapping framework for Java")
  case object Qt extends DefaultProgrammingFramework(25, "Qt", "Cross-platform application/UI framework for C++")
  case object Flutter
      extends DefaultProgrammingFramework(
        26,
        "Flutter",
        "UI toolkit for natively compiled multi-platform apps, using Dart"
      )
  case object SwiftUi
      extends DefaultProgrammingFramework(27, "SwiftUI", "Declarative UI framework for Apple platforms")
  case object Vapor extends DefaultProgrammingFramework(28, "Vapor", "Server-side Swift web framework")
  case object Ktor
      extends DefaultProgrammingFramework(29, "Ktor", "Asynchronous framework for building servers/clients in Kotlin")
  case object Micronaut
      extends DefaultProgrammingFramework(30, "Micronaut", "JVM framework for microservices/serverless apps")
  case object Quarkus extends DefaultProgrammingFramework(31, "Quarkus", "Kubernetes-native Java framework")

  override def values: IndexedSeq[DefaultProgrammingFramework] = findValues
}
