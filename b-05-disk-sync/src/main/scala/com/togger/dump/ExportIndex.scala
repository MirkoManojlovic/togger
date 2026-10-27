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

/** `index.json`: the hash of every entity file by entity folder and id, plus a root hash over all of them. */
case class ExportIndex(
  rootHash: String,
  exportedAt: Instant,
  exportedBy: AccountDumpData,
  entities: Map[String, Map[String, ExportIndexEntry]]
) {

  def entriesOf(entityType: DataExportEntityType): Map[UUID, ExportIndexEntry] =
    entities.getOrElse(entityType.folder, Map.empty).map { case (id, entry) => UUID.fromString(id) -> entry }

}

/** Hashes an entity by the canonical form of its json, so key order and whitespace never change a hash. */
object ExportHasher {

  def hashJson(json: Json): String = sha256(canonical(json).noSpaces)

  def canonical(json: Json): Json =
    json.arrayOrObject(
      json,
      values => Json.fromValues(values.map(canonical)),
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

  def rootHash(entities: Map[String, Map[String, ExportIndexEntry]]): String =
    sha256(
      entities.toList.sortBy(_._1).flatMap { case (folder, entries) =>
        entries.toList.sortBy(_._1).map { case (id, entry) => s"$folder/$id:${entry.hash}" }
      }.mkString("\n")
    )

  private def sha256(value: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map("%02x".format(_))
      .mkString

}
