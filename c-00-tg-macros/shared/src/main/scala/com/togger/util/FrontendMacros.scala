package com.togger.util

//import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object FrontendMacros {

  case class SelectAndUpdate[T, U](selector: T => U, updater: (T, U) => T)
  case class SelectAndUpdateAndError[T, U](selector: T => U, updater: (T, U) => T, errorFn: T => Seq[String])

  // This is developed before selectorAndUpdaterMacro, could be useful in the future
  def updateFnMacro[T, U](selector: T => U): (T, U) => T =
    macro updateFnMacroImpl[T, U]

  def updateFnMacroImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[T => U]): c.Expr[(T, U) => T] = {
    import c.universe._

    val filedToCopy = selector.tree match {
      case q"(..$_) => $body" =>
        body.asInstanceOf[Select].name
      case _ =>
        c.abort(c.enclosingPosition, "There is an error with a function passed")
    }

    val filedToCopyTermName = filedToCopy.toTermName

    val caseClassType = c.weakTypeTag[T].tpe
    val fieldNameType = c.weakTypeTag[U].tpe

    c.Expr[(T, U) => T](
      q"""
       (caseClass: $caseClassType, fieldOfCaseClass: $fieldNameType) => caseClass.copy($filedToCopyTermName = fieldOfCaseClass)
       """
    )

  }

  def selectorAndUpdaterMacro[T, U](selector: T => U): FrontendMacros.SelectAndUpdate[T, U] =
    macro selectorAndUpdaterMacroImpl[T, U]

  def selectorAndUpdaterMacroImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[T => U]): c.Expr[FrontendMacros.SelectAndUpdate[T, U]] = {
    import c.universe._

    val filedToCopy = selector.tree match {
      case q"(..$_) => $body" =>
        body.asInstanceOf[Select].name
      case _ =>
        c.abort(c.enclosingPosition, "There is an error with a function passed")
    }

    val filedToCopyTermName = filedToCopy.toTermName

    val caseClassType = c.weakTypeTag[T].tpe
    val fieldNameType = c.weakTypeTag[U].tpe

    c.Expr[FrontendMacros.SelectAndUpdate[T, U]](
      q"""
       com.togger.util.FrontendMacros.SelectAndUpdate[$caseClassType, $fieldNameType](
        $selector,
        (caseClass: $caseClassType, fieldOfCaseClass: $fieldNameType) => caseClass.copy($filedToCopyTermName = fieldOfCaseClass)
       )
       """
    )

  }

  def sue[T, U](
    selector: T => U
  ): FrontendMacros.SelectAndUpdateAndError[T, U] =
    macro selectorAndUpdaterAndErrorMacroImpl[T, U]

  def selectorAndUpdaterAndErrorMacroImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[T => U]): c.Expr[FrontendMacros.SelectAndUpdateAndError[T, U]] = {
    import c.universe._

    val filedToCopy = selector.tree match {
      case q"(..$_) => $body" =>
        body.asInstanceOf[Select].name
      case _ =>
        c.abort(c.enclosingPosition, "There is an error with a function passed")
    }

    val filedToCopyTermName = filedToCopy.toTermName

    val caseClassType = c.weakTypeTag[T].tpe
    val fieldNameType = c.weakTypeTag[U].tpe

    val validationFunctionName = TermName(filedToCopyTermName.toString + "Valid")
    val errorFunction = q"(caseClass: $caseClassType) => caseClass.$validationFunctionName"

    c.Expr[FrontendMacros.SelectAndUpdateAndError[T, U]](
      q"""
       com.togger.util.FrontendMacros.SelectAndUpdateAndError[$caseClassType, $fieldNameType](
        $selector,
        (caseClass: $caseClassType, fieldOfCaseClass: $fieldNameType) => caseClass.copy($filedToCopyTermName = fieldOfCaseClass),
        $errorFunction
       )
       """
    )

  }

}
