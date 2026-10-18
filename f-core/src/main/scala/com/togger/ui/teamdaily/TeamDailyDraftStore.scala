package com.togger.ui.daily

import com.togger.logentry.LogEntryStatus
import com.togger.ui.localstorage.LocalStorage
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

import java.time.LocalDate
import java.util.UUID
import scala.util.Try

case class DailyEntryKey(memberId: UUID, taskId: UUID, date: LocalDate)

/** What was filled in for one day. A day nobody touched has no draft and is not saved. */
case class DailyEntryDraft(status: LogEntryStatus, textInternal: String, textExternal: String, skipped: Boolean) {
  def willBeSaved: Boolean = !skipped
}

case class DailyEntryRecord(key: DailyEntryKey, draft: DailyEntryDraft)

/** Everything of an unfinished daily. Where the user was is not kept; they start again at choosing members. */
case class DailyDraft(projectId: Option[UUID], memberIds: Seq[UUID], todayFirst: Boolean, entries: Seq[DailyEntryRecord]) {
  def isEmpty: Boolean = entries.isEmpty
}

/** The draft in the browser's local storage, one per account and project, so an unfinished daily survives a refresh. */
class DailyDraftStore(localStorage: LocalStorage, accountId: UUID, projectId: UUID) {

  import DailyDraftStore._

  private val key = s"$KeyPrefix$accountId-$projectId"

  def load(): Option[DailyDraft] =
    Try(localStorage.getItem(key)).toOption.flatten
      .flatMap(stored => decode[Envelope](stored).toOption)
      .filter(_.version == Version)
      .map(_.draft)
      .filterNot(_.isEmpty)

  /** Saving an empty draft removes it, so there is never a stored draft with nothing in it. */
  def save(draft: DailyDraft): Unit =
    if (draft.isEmpty) clear()
    else Try(localStorage.setItem(key, Envelope(Version, draft).asJson.noSpaces)): Unit

  def clear(): Unit = Try(localStorage.removeItem(key)): Unit
}

object DailyDraftStore {

  val KeyPrefix = "tg-daily-draft-"

  /** Bumped when the stored shape changes in a way old drafts can't be read, which then makes them disappear. */
  val Version = 1

  private case class Envelope(version: Int, draft: DailyDraft)

  // a status is stored as its number, so renaming one doesn't invalidate saved drafts
  private implicit val statusEncoder: Encoder[LogEntryStatus] = Encoder[Int].contramap(_.value)
  private implicit val statusDecoder: Decoder[LogEntryStatus] =
    Decoder[Int].emap(value => LogEntryStatus.withValueOpt(value).toRight(s"No log entry status $value"))
}
