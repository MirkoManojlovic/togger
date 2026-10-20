package com.togger.ui.slacker

import com.togger.logentry.LogEntryStatus
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.teamdaily.TeamDailyEntryDraft
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

import java.util.UUID
import scala.util.Try

case class SlackerEntryRecord(key: SlackerEntryKey, draft: TeamDailyEntryDraft)

/** Everything of an unfinished slacker. Where the user was is not kept; they start again at choosing tasks. */
case class SlackerDraft(
  projectId: Option[UUID],
  taskIds: Option[Seq[UUID]],
  pastFirst: Seq[UUID],
  entries: Seq[SlackerEntryRecord]
) {
  def isEmpty: Boolean = entries.isEmpty
}

/** The draft in the browser's local storage, one per account and project, so an unfinished slacker survives a refresh. */
class SlackerDraftStore(localStorage: LocalStorage, accountId: UUID, projectId: UUID) {

  import SlackerDraftStore._

  private val key = s"$KeyPrefix$accountId-$projectId"

  def load(): Option[SlackerDraft] =
    Try(localStorage.getItem(key)).toOption.flatten
      .flatMap(stored => decode[Envelope](stored).toOption)
      .filter(_.version == Version)
      .map(_.draft)
      .filterNot(_.isEmpty)

  /** Saving an empty draft removes it, so there is never a stored draft with nothing in it. */
  def save(draft: SlackerDraft): Unit =
    if (draft.isEmpty) clear()
    else Try(localStorage.setItem(key, Envelope(Version, draft).asJson.noSpaces)): Unit

  def clear(): Unit = Try(localStorage.removeItem(key)): Unit
}

object SlackerDraftStore {

  val KeyPrefix = "tg-slacker-draft-"

  val Version = 1

  private case class Envelope(version: Int, draft: SlackerDraft)

  private implicit val statusEncoder: Encoder[LogEntryStatus] = Encoder[Int].contramap(_.value)
  private implicit val statusDecoder: Decoder[LogEntryStatus] =
    Decoder[Int].emap(value => LogEntryStatus.withValueOpt(value).toRight(s"No log entry status $value"))
}
