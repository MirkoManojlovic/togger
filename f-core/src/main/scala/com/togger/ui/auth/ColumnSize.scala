package com.togger.ui.auth

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class ColumnSize(val value: Int) extends IntEnumEntry {
  private def toColumnText = s"col-$value"
  private def toColumnInput = s"col-${12 - value}"

  def withSmColumnText = s"${toColumnText} col-sm-12"
  def withSmColumnInput = s"${toColumnInput} col-sm-12"
}

object ColumSize extends IntEnum[ColumnSize] {

  override def values: IndexedSeq[ColumnSize] = findValues

  case object Column1 extends ColumnSize(value = 1)
  case object Column2 extends ColumnSize(value = 2)
  case object Column3 extends ColumnSize(value = 3)
  case object Column4 extends ColumnSize(value = 4)
  case object Column5 extends ColumnSize(value = 5)
  case object Column6 extends ColumnSize(value = 6)
  case object Column7 extends ColumnSize(value = 7)
  case object Column8 extends ColumnSize(value = 8)
  case object Column9 extends ColumnSize(value = 9)
  case object Column10 extends ColumnSize(value = 10)
  case object Column11 extends ColumnSize(value = 11)

}
