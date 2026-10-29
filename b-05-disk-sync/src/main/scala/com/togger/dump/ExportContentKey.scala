package com.togger.dump

import io.circe.Json

import java.time.Instant
import java.util.UUID

/** What identifies a task status, label or template across projects: the import matches them by content and gives
  * imported rows new ids and timestamps, so the import check cannot match them by id or hash. A template is identified
  * by the content of its label and of its latest version.
  */
object ExportContentKey {

  type Key = List[String]

  def applies(entityType: DataExportEntityType): Boolean =
    entityType == DataExportEntityType.TaskStatus || entityType == DataExportEntityType.TaskLabel ||
      entityType == DataExportEntityType.TaskTemplate

  /** The keys of the task labels by id, for the templates to resolve their label through. */
  def labelKeys(labels: Map[UUID, Json]): Map[UUID, Key] =
    labels.flatMap { case (id, json) => nameDescriptionEmoji(json).toOption.map(id -> _) }

  def of(entityType: DataExportEntityType, json: Json, labelKeys: Map[UUID, Key]): Either[String, Key] =
    if (entityType == DataExportEntityType.TaskTemplate) template(json, labelKeys) else nameDescriptionEmoji(json)

  private def nameDescriptionEmoji(json: Json): Either[String, Key] = {
    val cursor = json.hcursor
    (for {
      name <- cursor.get[String]("name")
      description <- cursor.get[String]("description")
      emoji <- cursor.get[String]("emoji")
    } yield List(name, description, emoji)).left.map(error => s"cannot read name, description and emoji: ${error.message}")
  }

  private def template(json: Json, labelKeys: Map[UUID, Key]): Either[String, Key] = {
    val cursor = json.hcursor
    for {
      labelId <- cursor.downField("template").get[Option[UUID]]("taskLabelId").left.map(_.message)
      label <- labelId.fold[Either[String, Key]](Right(List("no label")))(id =>
        labelKeys.get(id).map("label" :: _).toRight(s"task label $id of the template is unknown")
      )
      versions <- cursor.get[List[Json]]("versions").left.map(_.message)
      latest <- versions
        .maxByOption(_.hcursor.get[Instant]("createdAt").getOrElse(Instant.MIN))
        .toRight("template has no version")
      name <- latest.hcursor.get[String]("name").left.map(_.message)
      body <- latest.hcursor.get[String]("body").left.map(_.message)
    } yield label ++ List(name, body)
  }

}
