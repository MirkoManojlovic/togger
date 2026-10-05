package com.togger.techstack

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class TechStackCategory(val value: Int, val categoryName: String) extends IntEnumEntry

object TechStackCategory extends IntEnum[TechStackCategory] {
  case object Language extends TechStackCategory(1, "Language")
  case object Framework extends TechStackCategory(2, "Framework")

  def namesToValuesMapString: Map[String, String] = this.values.map(c => c.value.toString -> c.categoryName).toMap

  override def values: IndexedSeq[TechStackCategory] = findValues
}
