package com.togger.ui.timebox

import com.togger.ui.localstorage.LocalStorage
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps

import java.util.UUID
import scala.util.Try

/** The fields of the timebox being created, per project, in the browser's local storage so a half-filled form survives
  * a refresh or a trip to another page. The staged tasks are kept by [[TimeboxStagingStore]].
  */
class TimeboxFormDraftStore(localStorage: LocalStorage) {

  import TimeboxFormDraftStore._

  /** The saved form, unless there is none or it was written by a version that can't be read any more. */
  def load(projectId: UUID): Option[TimeboxForm] =
    Try(localStorage.getItem(key(projectId))).toOption.flatten
      .flatMap(stored => decode[Envelope](stored).toOption)
      .filter(_.version == Version)
      .map(_.form)

  /** Saving an untouched form removes it, so there is never a stored form with nothing in it. */
  def save(projectId: UUID, form: TimeboxForm): Unit =
    if (form == TimeboxForm()) clear(projectId)
    else Try(localStorage.setItem(key(projectId), Envelope(Version, form).asJson.noSpaces)): Unit

  def clear(projectId: UUID): Unit = Try(localStorage.removeItem(key(projectId))): Unit
}

object TimeboxFormDraftStore {

  val KeyPrefix = "tg-timebox-form-"

  /** Bumped when the stored shape changes in a way old entries can't be read, which then makes them disappear. */
  val Version = 1

  private case class Envelope(version: Int, form: TimeboxForm)

  private def key(projectId: UUID): String = s"$KeyPrefix$projectId"
}
