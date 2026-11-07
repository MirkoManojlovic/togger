package com.togger.dump

import io.circe.Json

import java.time.Instant
import java.util.UUID

/** When a log entry really changed. Its `createdAt`/`updatedAt` hold the day the entry is logged for, which can lie in
  * the future, so they cannot be compared with the time of a deletion. The ids of the entry and of its versions are
  * uuid7, which carry the real creation time; a new version is a change. Ids that are not uuid7 fall back to the
  * timestamps.
  */
object LogEntryChangedAt {

  def of(entryId: UUID, versionIds: Iterable[UUID], timestamps: => Iterable[Instant]): Option[Instant] =
    (entryId :: versionIds.toList).flatMap(uuid7Time).maxOption.orElse(timestamps.maxOption)

  /** The change time of a log entry as the export dumps it: `{"logEntry": {...}, "versions": [...]}`. */
  def ofJson(json: Json): Option[Instant] = {
    val cursor = json.hcursor
    val entryId = cursor.downField("logEntry").get[UUID]("id").toOption
    val versionIds = cursor.downField("versions").values.toList.flatten.flatMap(_.hcursor.get[UUID]("version").toOption)
    entryId.flatMap(of(_, versionIds, ExportHasher.latestTimestamp(json))).orElse(ExportHasher.latestTimestamp(json))
  }

  private def uuid7Time(id: UUID): Option[Instant] =
    Option.when(id.version == 7)(Instant.ofEpochMilli(id.getMostSignificantBits >>> 16))
}
