package com.togger.dump

import io.circe.Json

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** The hash of one exported entity file; the name only labels the entity in status reports and is not hashed. */
case class ExportIndexEntry(hash: String, name: Option[String])

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
