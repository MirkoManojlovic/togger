package com.togger.dump

import cats.implicits._
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
      entityType == DataExportEntityType.TaskTemplate || entityType == DataExportEntityType.MilestoneStepTemplate ||
      entityType == DataExportEntityType.MilestoneTemplate

  /** The keys of the milestone step templates by id (their latest name and description), for the milestone templates to
    * resolve their steps through.
    */
  def stepTemplateKeys(stepTemplates: Map[UUID, Json]): Map[UUID, Key] =
    stepTemplates.flatMap { case (id, json) => stepTemplate(json).toOption.map(id -> _) }

  /** The keys of the task labels by id, for the templates to resolve their label through. */
  def labelKeys(labels: Map[UUID, Json]): Map[UUID, Key] =
    labels.flatMap { case (id, json) => nameDescriptionEmoji(json).toOption.map(id -> _) }

  def of(entityType: DataExportEntityType, json: Json, labelKeys: Map[UUID, Key]): Either[String, Key] =
    entityType match {
      case DataExportEntityType.TaskTemplate          => template(json, labelKeys)
      case DataExportEntityType.MilestoneStepTemplate => stepTemplate(json)
      case DataExportEntityType.MilestoneTemplate     => milestoneTemplate(json, labelKeys)
      case _                                          => nameDescriptionEmoji(json)
    }

  private def nameDescriptionEmoji(json: Json): Either[String, Key] = {
    val cursor = json.hcursor
    (for {
      name <- cursor.get[String]("name")
      description <- cursor.get[String]("description")
      emoji <- cursor.get[String]("emoji")
    } yield List(name, description, emoji)).left.map(error =>
      s"cannot read name, description and emoji: ${error.message}"
    )
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

  private def latestVersion(json: Json)(createdAt: Json => Instant): Either[String, Json] =
    for {
      versions <- json.hcursor.get[List[Json]]("versions").left.map(_.message)
      latest <- versions.maxByOption(createdAt).toRight("template has no version")
    } yield latest

  private def stepTemplate(json: Json): Either[String, Key] =
    for {
      latest <- latestVersion(json)(_.hcursor.get[Instant]("createdAt").getOrElse(Instant.MIN))
      name <- latest.hcursor.get[String]("name").left.map(_.message)
      description <- latest.hcursor.get[String]("description").left.map(_.message)
    } yield List(name, description)

  /** A milestone template is identified by the content of its latest version and of the step templates it lists. */
  private def milestoneTemplate(json: Json, stepTemplateKeys: Map[UUID, Key]): Either[String, Key] =
    for {
      latest <- latestVersion(json)(_.hcursor.downField("version").get[Instant]("createdAt").getOrElse(Instant.MIN))
      name <- latest.hcursor.downField("version").get[String]("name").left.map(_.message)
      description <- latest.hcursor.downField("version").get[String]("description").left.map(_.message)
      stages <- latest.hcursor
        .get[List[List[UUID]]]("stages")
        .orElse(latest.hcursor.get[List[UUID]]("stepTemplateIds").map(_.map(List(_))))
        .left
        .map(_.message)
      keyed <- stages.traverse(
        _.traverse(id => stepTemplateKeys.get(id).toRight(s"milestone step template $id is unknown"))
      )
      // the steps of a stage run in parallel, so their order does not matter
    } yield List(name, description) ++
      keyed.flatMap(stage => "stage" :: stage.sortBy(_.mkString("\u0000")).flatMap("step" :: _))

}
