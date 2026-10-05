package com.togger.projectlabel

import enumeratum._

sealed abstract class DefaultProgrammingLanguage(val i: Int, val languageName: String, val description: String)
    extends EnumEntry

object DefaultProgrammingLanguage extends Enum[DefaultProgrammingLanguage] {
  case object Scala
      extends DefaultProgrammingLanguage(0, "Scala", "Statically typed JVM/JS/Native language blending OO and FP")
  case object Java extends DefaultProgrammingLanguage(1, "Java", "Statically typed, class-based, JVM language")
  case object Kotlin
      extends DefaultProgrammingLanguage(2, "Kotlin", "Statically typed JVM/Android/multiplatform language")
  case object Python
      extends DefaultProgrammingLanguage(3, "Python", "Dynamically typed, general-purpose scripting language")
  case object JavaScript
      extends DefaultProgrammingLanguage(
        4,
        "JavaScript",
        "Dynamically typed language of the web browser and Node.js"
      )
  case object TypeScript
      extends DefaultProgrammingLanguage(5, "TypeScript", "Statically typed superset of JavaScript")
  case object Ruby
      extends DefaultProgrammingLanguage(6, "Ruby", "Dynamically typed, object-oriented scripting language")
  case object Go extends DefaultProgrammingLanguage(7, "Go", "Statically typed, compiled language from Google")
  case object Rust
      extends DefaultProgrammingLanguage(8, "Rust", "Statically typed systems language focused on memory safety")
  case object C extends DefaultProgrammingLanguage(9, "C", "Low-level, statically typed systems programming language")
  case object CPlusPlus
      extends DefaultProgrammingLanguage(10, "C++", "Statically typed systems/general-purpose language, superset of C")
  case object CSharp extends DefaultProgrammingLanguage(11, "C#", "Statically typed, object-oriented .NET language")
  case object Swift extends DefaultProgrammingLanguage(12, "Swift", "Statically typed language for Apple platforms")
  case object ObjectiveC
      extends DefaultProgrammingLanguage(
        13,
        "Objective-C",
        "Object-oriented superset of C used historically on Apple platforms"
      )
  case object Php
      extends DefaultProgrammingLanguage(14, "PHP", "Dynamically typed scripting language for server-side web development")
  case object Perl
      extends DefaultProgrammingLanguage(15, "Perl", "Dynamically typed scripting language known for text processing")
  case object Haskell extends DefaultProgrammingLanguage(16, "Haskell", "Statically typed, purely functional language")
  case object Elixir
      extends DefaultProgrammingLanguage(17, "Elixir", "Dynamically typed, functional language on the Erlang VM")
  case object Erlang extends DefaultProgrammingLanguage(18, "Erlang", "Dynamically typed, functional, concurrent language")
  case object Clojure
      extends DefaultProgrammingLanguage(19, "Clojure", "Dynamically typed, functional Lisp dialect on the JVM")
  case object FSharp
      extends DefaultProgrammingLanguage(20, "F#", "Statically typed, functional-first .NET language")
  case object Dart extends DefaultProgrammingLanguage(21, "Dart", "Statically typed language used by Flutter")
  case object R extends DefaultProgrammingLanguage(22, "R", "Dynamically typed language for statistical computing")
  case object Julia
      extends DefaultProgrammingLanguage(23, "Julia", "Dynamically typed language for numerical/scientific computing")
  case object Lua
      extends DefaultProgrammingLanguage(24, "Lua", "Lightweight, dynamically typed embeddable scripting language")
  case object Groovy extends DefaultProgrammingLanguage(25, "Groovy", "Dynamically typed language for the JVM")
  case object ShellBash
      extends DefaultProgrammingLanguage(26, "Shell/Bash", "Scripting language for Unix-like command shells")
  case object Sql
      extends DefaultProgrammingLanguage(27, "SQL", "Declarative language for querying/managing relational databases")
  case object Html extends DefaultProgrammingLanguage(28, "HTML", "Markup language for structuring web content")
  case object Css extends DefaultProgrammingLanguage(29, "CSS", "Stylesheet language for formatting web content")
  case object Assembly
      extends DefaultProgrammingLanguage(30, "Assembly", "Low-level language that maps closely to machine code")
  case object Matlab
      extends DefaultProgrammingLanguage(31, "MATLAB", "Language for numerical computing and matrix operations")
  case object Scheme extends DefaultProgrammingLanguage(32, "Scheme", "Minimalist dialect of Lisp")
  case object OCaml
      extends DefaultProgrammingLanguage(33, "OCaml", "Statically typed, functional/imperative language of the ML family")
  case object Fortran
      extends DefaultProgrammingLanguage(34, "Fortran", "Statically typed language for scientific/numeric computing")
  case object Cobol
      extends DefaultProgrammingLanguage(
        35,
        "COBOL",
        "Statically typed language historically used in business/finance systems"
      )
  case object Ada extends DefaultProgrammingLanguage(36, "Ada", "Statically typed language used in safety-critical systems")
  case object Prolog extends DefaultProgrammingLanguage(37, "Prolog", "Declarative, logic programming language")
  case object Zig extends DefaultProgrammingLanguage(38, "Zig", "Statically typed systems programming language")
  case object Nim
      extends DefaultProgrammingLanguage(39, "Nim", "Statically typed, compiled language with Python-like syntax")
  case object Crystal extends DefaultProgrammingLanguage(40, "Crystal", "Statically typed language with Ruby-like syntax")
  case object Elm
      extends DefaultProgrammingLanguage(41, "Elm", "Statically typed functional language that compiles to JavaScript")
  case object Solidity
      extends DefaultProgrammingLanguage(42, "Solidity", "Statically typed language for Ethereum smart contracts")
  case object VisualBasicNet
      extends DefaultProgrammingLanguage(43, "Visual Basic .NET", "Statically typed, object-oriented .NET language")
  case object Powershell
      extends DefaultProgrammingLanguage(44, "PowerShell", "Scripting language and shell for task automation")

  override def values: IndexedSeq[DefaultProgrammingLanguage] = findValues
}
