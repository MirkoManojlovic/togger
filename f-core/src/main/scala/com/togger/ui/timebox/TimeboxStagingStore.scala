package com.togger.ui.timebox

import com.raquo.laminar.api.L._
import com.togger.dto.FindTaskRespFull
import com.togger.ui.localstorage.LocalStorage
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps

import java.util.UUID
import scala.util.Try

/** The tasks picked for the timebox being built, per project, in the order they were staged. The form and the staged
  * area share them through this store, and they are kept in the browser's local storage so a half-built timebox
  * survives a refresh.
  */
class TimeboxStagingStore(localStorage: LocalStorage) {

  import TimeboxStagingStore._

  private val stagedVar = Var(Map.empty[UUID, Seq[FindTaskRespFull]])

  /** Reads what is stored for the project; call it before showing the project's staged tasks. */
  def open(projectId: UUID): Unit =
    stagedVar.update(staged => staged.updated(projectId, load(projectId)))

  def staged(projectId: UUID): Signal[Seq[FindTaskRespFull]] =
    stagedVar.signal.map(_.getOrElse(projectId, Seq.empty)).distinct

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
  }

  /** Swaps each staged task for its current state, and drops the ones that no longer exist. */
  def refresh(projectId: UUID, current: Seq[FindTaskRespFull]): Unit =
    set(projectId, refreshed(now(projectId), current))

  def clear(projectId: UUID): Unit = set(projectId, Seq.empty)

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

  private case class Envelope(version: Int, tasks: Seq[FindTaskRespFull])

  private def key(projectId: UUID): String = s"$KeyPrefix$projectId"

  def refreshed(staged: Seq[FindTaskRespFull], current: Seq[FindTaskRespFull]): Seq[FindTaskRespFull] =
    staged.flatMap(task => current.find(_.id == task.id))

  /** The task is unstaged if it is staged, and staged last otherwise. */
  def toggled(staged: Seq[FindTaskRespFull], task: FindTaskRespFull): Seq[FindTaskRespFull] =
    if (staged.exists(_.id == task.id)) staged.filterNot(_.id == task.id) else staged :+ task
}
