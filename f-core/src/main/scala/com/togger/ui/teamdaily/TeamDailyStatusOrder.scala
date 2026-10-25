package com.togger.ui.teamdaily

import com.togger.task.DefaultTaskStatus

/** Which task statuses a daily asks about, and in what order. A status is recognised by its default name, ignoring the
  * emoji in front of it, as statuses have no type of their own.
  */
object TeamDailyStatusOrder {

  private val Ordered: Seq[DefaultTaskStatus] =
    Seq(DefaultTaskStatus.InProgress, DefaultTaskStatus.Testing, DefaultTaskStatus.Waiting, DefaultTaskStatus.Blocked)

  val StatusCount: Int = Ordered.size

  def normalise(name: String): String =
    name.dropWhile(!_.isLetter).trim.toLowerCase

  private val RankByName: Map[String, Int] =
    Ordered.zipWithIndex.map { case (status, rank) => normalise(status.statusName) -> rank }.toMap

  /** 0 for In progress up to 3 for Blocked; none for any other status. */
  def rank(statusName: String): Option[Int] = RankByName.get(normalise(statusName))

  private val OnHold = Set(DefaultTaskStatus.Waiting, DefaultTaskStatus.Blocked).map(status => normalise(status.statusName))

  /** A task that is waiting or blocked most likely still is, for the same reason, the next day. */
  def isOnHold(statusName: String): Boolean = OnHold.contains(normalise(statusName))
}
