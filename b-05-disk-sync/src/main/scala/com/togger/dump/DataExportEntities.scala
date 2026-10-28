package com.togger.dump

import com.togger.dump.DayOfWeekCodec._
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._

import java.util.UUID

/** Turns a project snapshot into the files of the export tree. */
object DataExportEntities {

  def build(snapshot: ProjectExportSnapshot, referencedAccounts: List[AccountDumpData]): List[ExportEntity] = {
    val projectId = snapshot.project.id
    val projectData = ProjectDumpData(
      project = snapshot.project,
      projectLabelIds = snapshot.projectLabels.map(_.label.id),
      referencedAccounts = referencedAccounts.sortBy(_.id)
    )

    List(
      entity(DataExportEntityType.Project, projectId, Some(snapshot.project.name), projectData),
      entity(DataExportEntityType.Members, projectId, None, MembersDumpData(snapshot.memberIds))
    ) ++
      snapshot.taskStatuses.map(s => entity(DataExportEntityType.TaskStatus, s.id, Some(s.name), s)) ++
      snapshot.taskLabels.map(l => entity(DataExportEntityType.TaskLabel, l.id, Some(l.name), l)) ++
      snapshot.projectLabels.map(l => entity(DataExportEntityType.ProjectLabel, l.label.id, Some(l.label.name), l)) ++
      snapshot.milestones.map(m =>
        entity(DataExportEntityType.Milestone, m.milestone.id, m.versions.lastOption.map(_.name), m)
      ) ++
      snapshot.tasks.map(t =>
        entity(DataExportEntityType.Task, t.task.id, t.taskVersions.lastOption.map(_.name), t)
      ) ++
      snapshot.logEntries.map(le => entity(DataExportEntityType.LogEntry, le.logEntry.id, None, le)) ++
      snapshot.taskTemplates.map(t =>
        entity(DataExportEntityType.TaskTemplate, t.template.id, t.versions.lastOption.map(_.name), t)
      ) ++
      snapshot.timeboxes.map(t => entity(DataExportEntityType.Timebox, t.timebox.id, None, t)) ++
      snapshot.tgPromptTemplates.map(p =>
        entity(DataExportEntityType.TgPromptTemplate, p.tgPromptTemplate.id, p.versions.lastOption.map(_.title), p)
      )
  }

  private def entity[A: Encoder](
    entityType: DataExportEntityType,
    id: UUID,
    name: Option[String],
    data: A
  ): ExportEntity =
    ExportEntity(entityType, id, name, data.asJson)

}
