package com.togger.ui.task.bulk

import com.togger.task.TaskPriority
import com.togger.ui.localstorage.LocalStorage
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

import java.util.UUID
import scala.util.Try

/** Everything of an unfinished bulk add: what was typed and what was done to the tasks it turned into. */
case class BulkDraft(projectId: Option[UUID], rawInput: String, tasks: Seq[BulkTask]) {
  def isEmpty: Boolean = rawInput.trim.isEmpty && tasks.isEmpty
}

/** The draft in the browser's local storage, one per account, so an unfinished bulk add survives a refresh. */
class BulkDraftStore(localStorage: LocalStorage, accountId: UUID) {

  import BulkDraftStore._

  private val key = s"$KeyPrefix$accountId"

  /** The saved draft, unless there is none, it is empty, or it was written by a version that can't be read any more. */
  def load(): Option[BulkDraft] =
    Try(localStorage.getItem(key)).toOption.flatten
      .flatMap(stored => decode[Envelope](stored).toOption)
      .filter(_.version == Version)
      .map(_.draft)
      .filterNot(_.isEmpty)

  /** Saving an empty draft removes it, so there is never a stored draft with nothing in it. */
  def save(draft: BulkDraft): Unit =
    if (draft.isEmpty) clear()
    else Try(localStorage.setItem(key, Envelope(Version, draft).asJson.noSpaces)): Unit

  def clear(): Unit = Try(localStorage.removeItem(key)): Unit
}

object BulkDraftStore {

  val KeyPrefix = "tg-bulk-add-draft-"

  /** Bumped when the stored shape changes in a way old drafts can't be read, which then makes them disappear. */
  val Version = 1

  private case class Envelope(version: Int, draft: BulkDraft)

  // a priority is stored as its number, so renaming one doesn't invalidate saved drafts
  private implicit val priorityEncoder: Encoder[TaskPriority] = Encoder[Int].contramap(_.value)
  private implicit val priorityDecoder: Decoder[TaskPriority] =
    Decoder[Int].emap(value => TaskPriority.withValueOpt(value).toRight(s"No task priority $value"))
}
