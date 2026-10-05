package com.togger.projectlabel

import enumeratum._

sealed abstract class DefaultProjectLabelCategory(val i: Int, val categoryName: String) extends EnumEntry

object DefaultProjectLabelCategory extends Enum[DefaultProjectLabelCategory] {
  case object ProgrammingLanguage extends DefaultProjectLabelCategory(0, "Programming Language")
  case object Framework extends DefaultProjectLabelCategory(1, "Framework")
  case object Infrastructure extends DefaultProjectLabelCategory(2, "Infrastructure")
  case object Domain extends DefaultProjectLabelCategory(3, "Domain")

  override def values: IndexedSeq[DefaultProjectLabelCategory] = findValues
}
