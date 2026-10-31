package com.togger.preview

import com.togger.common.TgClock
import com.togger.demo.DemoSeedIds
import com.togger.milestone.DefaultMilestoneTemplates
import io.circe.generic.auto._

import java.time.Instant
import java.util.UUID

case class PreviewMilestoneTemplate(id: UUID, projectId: UUID, createdAt: Instant)

/** A version keeps the ids of its step templates in order; names and descriptions are read from the step templates. */
case class PreviewMilestoneTemplateVersion(
  version: UUID,
  milestoneTemplateId: UUID,
  name: String,
  description: String,
  stepTemplateIds: List[UUID],
  createdAt: Instant
)

/** The milestone templates (with their versions) of the projects; every new project gets the default ones. */
class MilestoneTemplatesPreviewData(
  db: PreviewDb,
  projects: ProjectsPreviewData,
  stepTemplates: MilestoneStepTemplatesPreviewData,
  clock: TgClock
) {

  val templates: PreviewCollection[PreviewMilestoneTemplate] =
    db.collection[PreviewMilestoneTemplate](MilestoneTemplatesPreviewData.TemplatesName, _.id)

  /** Versions are stored oldest first, the last one of a template is its current content. */
  val templateVersions: PreviewCollection[PreviewMilestoneTemplateVersion] =
    db.collection[PreviewMilestoneTemplateVersion](MilestoneTemplatesPreviewData.VersionsName, _.version)

  projects.onProjectCreated(createDefaultTemplates)
  projects.onProjectDeleted(deleteProjectData)
  stepTemplates.onDeletionCheck(namesUsingStepTemplate)
  stepTemplates.onTemplateDeleted(dropStepTemplate)

  def latestVersion(templateId: UUID): Option[PreviewMilestoneTemplateVersion] =
    templateVersions.filter(_.milestoneTemplateId == templateId).lastOption

  def deleteTemplate(id: UUID): Boolean = {
    templateVersions.deleteWhere(_.milestoneTemplateId == id)
    templates.delete(id)
  }

  /** For a project created at runtime: new ids every time. */
  def createDefaultTemplates(projectId: UUID): Unit =
    createDefaultTemplates(projectId, (_, _) => UUID.randomUUID())

  /** For the demo project with the given index: the same ids every time, so that seeding twice changes nothing. */
  def seedDefaultTemplates(projectId: UUID, projectIndex: Int): Unit =
    createDefaultTemplates(
      projectId,
      (group, position) => DemoSeedIds.id(group, projectIndex * MilestoneTemplatesPreviewData.IdsPerProject + position)
    )

  private def createDefaultTemplates(projectId: UUID, idOf: (Int, Int) => UUID): Unit = {
    val now = clock.currentInstant
    val stepTemplateIdsByName = stepTemplates.templates
      .filter(_.projectId == projectId)
      .flatMap(template => stepTemplates.latestVersion(template.id).map(_.name -> template.id))
      .toMap
    DefaultMilestoneTemplates.defaults.zipWithIndex.foreach { case (default, position) =>
      val template = templates.upsert(
        PreviewMilestoneTemplate(idOf(DemoSeedIds.MilestoneTemplate, position), projectId, now)
      )
      templateVersions.upsert(
        PreviewMilestoneTemplateVersion(
          idOf(DemoSeedIds.MilestoneTemplateVersion, position),
          template.id,
          default.name,
          default.description,
          default.stepNames.flatMap(stepTemplateIdsByName.get),
          now
        )
      )
    }
  }

  private def namesUsingStepTemplate(stepTemplateId: UUID): List[String] =
    templates.all.flatMap(template =>
      latestVersion(template.id).filter(_.stepTemplateIds.contains(stepTemplateId)).map(_.name)
    )

  /** Like the backend: older versions lose the deleted step template from their steps. */
  private def dropStepTemplate(stepTemplateId: UUID): Unit =
    templateVersions.all
      .filter(_.stepTemplateIds.contains(stepTemplateId))
      .foreach(version => templateVersions.upsert(version.copy(stepTemplateIds = version.stepTemplateIds.filterNot(_ == stepTemplateId))))

  private def deleteProjectData(projectId: UUID): Unit =
    templates.filter(_.projectId == projectId).foreach(template => deleteTemplate(template.id))
}

object MilestoneTemplatesPreviewData {
  val TemplatesName = "milestone-templates"
  val VersionsName = "milestone-template-versions"

  /** Room for the ids of the default templates of one demo project. */
  val IdsPerProject = 100
}
