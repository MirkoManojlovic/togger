package com.togger.dump

import io.circe.Json

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import scala.util.Try

/** The hash of one exported entity file; the name only labels the entity in status reports and is not hashed.
  * `changedAt` is the newest creation or update time inside the entity, `None` for entities without timestamps.
  */
case class ExportIndexEntry(hash: String, name: Option[String], changedAt: Option[Instant])

/** `index.json`: the hash of every entity file by entity folder and id, the tombstones of the entities deleted from the
  * project (when, by entity folder and id), plus a root hash over all of them. An index written before tombstones
  * existed has none.
  */
case class ExportIndex(
  rootHash: String,
  exportedAt: Instant,
  exportedBy: AccountDumpData,
  entities: Map[String, Map[String, ExportIndexEntry]],
  deleted: Map[String, Map[String, Instant]] = Map.empty
) {

  def entriesOf(entityType: DataExportEntityType): Map[UUID, ExportIndexEntry] =
    entities.getOrElse(entityType.folder, Map.empty).map { case (id, entry) => UUID.fromString(id) -> entry }

}

object ExportIndex {
  import io.circe.Decoder
  import io.circe.generic.auto._

  implicit val exportIndexDecoder: Decoder[ExportIndex] = Decoder.instance { cursor =>
    for {
      rootHash <- cursor.get[String]("rootHash")
      exportedAt <- cursor.get[Instant]("exportedAt")
      exportedBy <- cursor.get[AccountDumpData]("exportedBy")
      entities <- cursor.get[Map[String, Map[String, ExportIndexEntry]]]("entities")
      deleted <- cursor.getOrElse[Map[String, Map[String, Instant]]]("deleted")(Map.empty)
    } yield ExportIndex(rootHash, exportedAt, exportedBy, entities, deleted)
  }
}

/** Hashes an entity by the canonical form of its json, so key order, whitespace and the order of array elements never
  * change a hash.
  */
object ExportHasher {

  def hashJson(json: Json): String = sha256(canonical(json).noSpaces)

  def canonical(json: Json): Json =
    json.arrayOrObject(
      json,
      values => Json.fromValues(values.map(canonical).sortBy(_.noSpaces)),
      obj => Json.fromFields(obj.toList.sortBy(_._1).map { case (key, value) => key -> canonical(value) })
    )

  private val timestampKeys = Set("createdAt", "updatedAt")

  /** The newest value of any `createdAt`/`updatedAt` field anywhere in the entity's json. */
  def latestTimestamp(json: Json): Option[Instant] =
    json.fold(
      None,
      _ => None,
      _ => None,
      _ => None,
      values => values.flatMap(latestTimestamp).maxOption,
      obj =>
        obj.toList.flatMap {
          case (key, value) if timestampKeys.contains(key) => value.asString.flatMap(parseInstant)
          case (_, value)                                  => latestTimestamp(value)
        }.maxOption
    )

  private def parseInstant(value: String): Option[Instant] = Try(Instant.parse(value)).toOption

  /** Covers the entities and the tombstones, so that a deletion alone changes the root hash. */
  def rootHash(
    entities: Map[String, Map[String, ExportIndexEntry]],
    deleted: Map[String, Map[String, Instant]] = Map.empty
  ): String =
    sha256(
      (entities.toList
        .sortBy(_._1)
        .flatMap { case (folder, entries) =>
          entries.toList.sortBy(_._1).map { case (id, entry) => s"$folder/$id:${entry.hash}" }
        } ++ deleted.toList
        .sortBy(_._1)
        .flatMap { case (folder, entries) =>
          entries.toList.sortBy(_._1).map { case (id, deletedAt) => s"deleted/$folder/$id:$deletedAt" }
        }).mkString("\n")
    )

  private def sha256(value: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map("%02x".format(_))
      .mkString

}
