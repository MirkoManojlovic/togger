package com.togger.util

//import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object SplunkMacro {

  def sp(v: Any): String = macro spImpl

  def spImpl(c: blackbox.Context)(v: c.Expr[Any]): c.Expr[String] = {
    import c.universe._
    c.Expr[String](q""" "[" + ${show(v.tree)} + "=" + $v + "]" """)
  }

}
