package com.togger.techstack

import enumeratum._

sealed abstract class DefaultTechStackCategory(val i: Int, val categoryName: String) extends EnumEntry

object DefaultTechStackCategory extends Enum[DefaultTechStackCategory] {
  case object Language extends DefaultTechStackCategory(0, "Language")
  case object Framework extends DefaultTechStackCategory(1, "Framework")
  case object Infrastructure extends DefaultTechStackCategory(2, "Infrastructure")

  override def values: IndexedSeq[DefaultTechStackCategory] = findValues
}
