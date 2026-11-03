package com.togger.milestone

import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry

/** The steps every milestone gets when it is created, recognised by their name. They are read-only: they cannot be
  * edited or deleted, and no other step can take their name. Tasks go into the Shelf step when only the milestone is
  * chosen.
  */
sealed abstract class DefaultMilestoneStep(val value: Int, val stepName: String, val description: String)
    extends IntEnumEntry

object DefaultMilestoneStep extends IntEnum[DefaultMilestoneStep] {

  case object Shelf extends DefaultMilestoneStep(0, "Shelf", "")

  override def values: IndexedSeq[DefaultMilestoneStep] = findValues

  /** Whether the name is one of a default step; the case and the surrounding spaces do not matter. */
  def isDefaultName(name: String): Boolean = values.exists(_.stepName.equalsIgnoreCase(name.trim))
}
