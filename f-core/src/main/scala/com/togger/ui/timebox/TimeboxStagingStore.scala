package com.togger.ui.timebox

import com.raquo.laminar.api.L._
import com.togger.dto.FindTaskRespFull
import com.togger.ui.localstorage.LocalStorage
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps

import java.time.LocalDate
import java.util.UUID
import scala.util.Try

/** The tasks picked for the timebox being built, per project, in the order they were staged. The form and the staged
  * area share them through this store, and they are kept in the browser's local storage so a half-built timebox
  * survives a refresh.
  */
class TimeboxStagingStore(localStorage: LocalStorage) {

  import TimeboxStagingStore._

  private val stagedVar = Var(Map.empty[UUID, Seq[FindTaskRespFull]])

  private val overridesVar = Var(Map.empty[UUID, Map[UUID, DateOverride]])

  /** Reads what is stored for the project; call it before showing the project's staged tasks. */
  def open(projectId: UUID): Unit = {
    stagedVar.update(staged => staged.updated(projectId, load(projectId)))
    overridesVar.update(_.updated(projectId, loadOverrides(projectId)))
  }

  def staged(projectId: UUID): Signal[Seq[FindTaskRespFull]] =
    stagedVar.signal.map(_.getOrElse(projectId, Seq.empty)).distinct

  /** The start and due dates changed on the Gantt chart, by task id. They only live here until the timebox is saved. */
  def dateOverrides(projectId: UUID): Signal[Map[UUID, DateOverride]] =
    overridesVar.signal.map(_.getOrElse(projectId, Map.empty)).distinct

  /** The staged tasks with the changed dates in place of their own. */
  def stagedWithDates(projectId: UUID): Signal[Seq[FindTaskRespFull]] =
    staged(projectId).combineWith(dateOverrides(projectId)).map { case (tasks, overrides) =>
      withOverrides(tasks, overrides)
    }

  def stagedWithDatesNow(projectId: UUID): Seq[FindTaskRespFull] =
    withOverrides(now(projectId), overridesNow(projectId))

  /** The staged tasks that have changed dates, with those dates in place: what is left to save on the tasks. */
  def withChangedDatesNow(projectId: UUID): Seq[FindTaskRespFull] = {
    val overrides = overridesNow(projectId)
    withOverrides(now(projectId).filter(task => overrides.contains(task.id)), overrides)
  }

  /** Changes the dates of a staged task; dates equal to the task's own take the change back. */
  def setDates(projectId: UUID, taskId: UUID, startDate: LocalDate, dueDate: LocalDate): Unit =
    setOverrides(projectId, overridesNow(projectId).updated(taskId, DateOverride(startDate, dueDate)))

  def stagedIds(projectId: UUID): Signal[Set[UUID]] =
    staged(projectId).map(_.map(_.id).toSet).distinct

  def now(projectId: UUID): Seq[FindTaskRespFull] = stagedVar.now().getOrElse(projectId, Seq.empty)

  def toggle(projectId: UUID, task: FindTaskRespFull): Unit =
    set(projectId, toggled(now(projectId), task))

  def unstage(projectId: UUID, taskId: UUID): Unit =
    set(projectId, now(projectId).filterNot(_.id == taskId))

  def set(projectId: UUID, tasks: Seq[FindTaskRespFull]): Unit = {
    stagedVar.update(_.updated(projectId, tasks))
    save(projectId, tasks)
    setOverrides(projectId, overridesNow(projectId))
  }

  /** Swaps each staged task for its current state, and drops the ones that no longer exist. */
  def refresh(projectId: UUID, current: Seq[FindTaskRespFull]): Unit =
    set(projectId, refreshed(now(projectId), current))

  def clear(projectId: UUID): Unit = set(projectId, Seq.empty)

  private def overridesNow(projectId: UUID): Map[UUID, DateOverride] =
    overridesVar.now().getOrElse(projectId, Map.empty)

  /** Keeps only the changes that still differ from a staged task. */
  private def setOverrides(projectId: UUID, overrides: Map[UUID, DateOverride]): Unit = {
    val kept = pruned(now(projectId), overrides)
    overridesVar.update(_.updated(projectId, kept))
    saveOverrides(projectId, kept)
  }

  private def loadOverrides(projectId: UUID): Map[UUID, DateOverride] =
    Try(localStorage.getItem(overridesKey(projectId))).toOption.flatten
      .flatMap(stored => decode[OverridesEnvelope](stored).toOption)
      .filter(_.version == Version)
      .map(_.overrides.map(entry => entry.taskId -> entry.dates).toMap)
      .getOrElse(Map.empty)

  private def saveOverrides(projectId: UUID, overrides: Map[UUID, DateOverride]): Unit =
    Try(
      if (overrides.isEmpty) localStorage.removeItem(overridesKey(projectId))
      else
        localStorage.setItem(
          overridesKey(projectId),
          OverridesEnvelope(Version, overrides.map { case (id, dates) => StoredOverride(id, dates) }.toSeq).asJson.noSpaces
        )
    ): Unit

  private def load(projectId: UUID): Seq[FindTaskRespFull] =
    Try(localStorage.getItem(key(projectId))).toOption.flatten
      .flatMap(stored => decode[Envelope](stored).toOption)
      .filter(_.version == Version)
      .map(_.tasks)
      .getOrElse(Seq.empty)

  /** Staging nothing removes the entry, so there is never a stored envelope with no tasks in it. */
  private def save(projectId: UUID, tasks: Seq[FindTaskRespFull]): Unit =
    Try(
      if (tasks.isEmpty) localStorage.removeItem(key(projectId))
      else localStorage.setItem(key(projectId), Envelope(Version, tasks).asJson.noSpaces)
    ): Unit
}

object TimeboxStagingStore {

  val KeyPrefix = "tg-timebox-staged-"

  /** Bumped when the stored shape changes in a way old entries can't be read, which then makes them disappear. */
  val Version = 1

  val DatesKeyPrefix = "tg-timebox-dates-"

  /** New start and due dates for a task. */
  case class DateOverride(startDate: LocalDate, dueDate: LocalDate)

  private case class Envelope(version: Int, tasks: Seq[FindTaskRespFull])

  private case class StoredOverride(taskId: UUID, dates: DateOverride)

  private case class OverridesEnvelope(version: Int, overrides: Seq[StoredOverride])

  private def key(projectId: UUID): String = s"$KeyPrefix$projectId"

  private def overridesKey(projectId: UUID): String = s"$DatesKeyPrefix$projectId"

  def withOverrides(tasks: Seq[FindTaskRespFull], overrides: Map[UUID, DateOverride]): Seq[FindTaskRespFull] =
    tasks.map(task =>
      overrides.get(task.id).fold(task)(dates => task.copy(startDate = dates.startDate, dueDate = dates.dueDate))
    )

  /** Drops the changes for tasks that are not staged, and those that put a task back on its own dates. */
  def pruned(tasks: Seq[FindTaskRespFull], overrides: Map[UUID, DateOverride]): Map[UUID, DateOverride] =
    overrides.filter { case (id, dates) =>
      tasks.exists(task => task.id == id && DateOverride(task.startDate, task.dueDate) != dates)
    }

  def refreshed(staged: Seq[FindTaskRespFull], current: Seq[FindTaskRespFull]): Seq[FindTaskRespFull] =
    staged.flatMap(task => current.find(_.id == task.id))

  /** The task is unstaged if it is staged, and staged last otherwise. */
  def toggled(staged: Seq[FindTaskRespFull], task: FindTaskRespFull): Seq[FindTaskRespFull] =
    if (staged.exists(_.id == task.id)) staged.filterNot(_.id == task.id) else staged :+ task
}
