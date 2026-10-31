package com.togger.preview

import com.togger.dto.{FindTaskLabelResp, FindTaskStatusResp, TaskTemplateVersionDto}
import com.togger.task.DefaultTaskStatus
import io.circe.generic.auto._

import java.time.Instant
import java.util.UUID

case class PreviewTaskTemplate(
  id: UUID,
  projectId: UUID,
  taskLabelId: Option[UUID],
  createdBy: UUID,
  createdAt: Instant
)

/** The task statuses, task labels and task templates (with their versions) of the projects. */
class TaskConfigPreviewData(db: PreviewDb, projects: ProjectsPreviewData) {

  val statuses: PreviewCollection[FindTaskStatusResp] =
    db.collection[FindTaskStatusResp](TaskConfigPreviewData.StatusesName, _.id)

  val labels: PreviewCollection[FindTaskLabelResp] =
    db.collection[FindTaskLabelResp](TaskConfigPreviewData.LabelsName, _.id)

  val templates: PreviewCollection[PreviewTaskTemplate] =
    db.collection[PreviewTaskTemplate](TaskConfigPreviewData.TemplatesName, _.id)

  /** Versions are stored oldest first, the last one of a template is its current content. */
  val templateVersions: PreviewCollection[TaskTemplateVersionDto] =
    db.collection[TaskTemplateVersionDto](TaskConfigPreviewData.TemplateVersionsName, _.version)

  private var labelDeletionHooks: List[UUID => Unit] = List.empty

  /** Lets the tasks domain drop a deleted label from its tasks. */
  def onTaskLabelDeleted(hook: UUID => Unit): Unit = labelDeletionHooks = labelDeletionHooks :+ hook

  projects.onProjectCreated(createDefaultStatuses)
  projects.onProjectDeleted(deleteProjectData)

  def createDefaultStatuses(projectId: UUID): Unit =
    DefaultTaskStatus.values.foreach { status =>
      statuses.upsert(
        FindTaskStatusResp(UUID.randomUUID(), projectId, status.statusName, status.description, status.emoji)
      )
    }

  def latestVersion(templateId: UUID): Option[TaskTemplateVersionDto] =
    templateVersions.filter(_.taskTemplateId == templateId).lastOption

  def deleteLabel(id: UUID): Boolean = {
    labelDeletionHooks.foreach(_(id))
    templates.all.filter(_.taskLabelId.contains(id)).foreach(template => templates.upsert(template.copy(taskLabelId = None)))
    labels.delete(id)
  }

  def deleteTemplate(id: UUID): Boolean = {
    templateVersions.deleteWhere(_.taskTemplateId == id)
    templates.delete(id)
  }

  private def deleteProjectData(projectId: UUID): Unit = {
    templates.filter(_.projectId == projectId).foreach(template => deleteTemplate(template.id))
    labels.filter(_.projectId == projectId).foreach(label => deleteLabel(label.id))
    statuses.deleteWhere(_.projectId == projectId)
  }
}

object TaskConfigPreviewData {
  val StatusesName = "task-statuses"
  val LabelsName = "task-labels"
  val TemplatesName = "task-templates"
  val TemplateVersionsName = "task-template-versions"
}
