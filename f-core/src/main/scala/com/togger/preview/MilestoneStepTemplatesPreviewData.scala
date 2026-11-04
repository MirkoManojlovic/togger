package com.togger.preview

import com.togger.common.TgClock
import com.togger.demo.DemoSeedIds
import com.togger.dto.MilestoneStepTemplateVersionDto
import com.togger.milestone.DefaultMilestoneStepTemplates
import io.circe.generic.auto._

import java.time.Instant
import java.util.UUID

case class PreviewMilestoneStepTemplate(id: UUID, projectId: UUID, createdAt: Instant)

/** The milestone step templates (with their versions) of the projects; every new project gets the default ones. */
class MilestoneStepTemplatesPreviewData(db: PreviewDb, projects: ProjectsPreviewData, clock: TgClock) {

  val templates: PreviewCollection[PreviewMilestoneStepTemplate] =
    db.collection[PreviewMilestoneStepTemplate](MilestoneStepTemplatesPreviewData.TemplatesName, _.id)

  /** Versions are stored oldest first, the last one of a template is its current content. */
  val templateVersions: PreviewCollection[MilestoneStepTemplateVersionDto] =
    db.collection[MilestoneStepTemplateVersionDto](MilestoneStepTemplatesPreviewData.VersionsName, _.version)

  projects.onProjectCreated(createDefaultTemplates)
  projects.onProjectDeleted(deleteProjectData)

  /** For a project created at runtime: new ids every time. */
  def createDefaultTemplates(projectId: UUID): Unit =
    createDefaultTemplates(projectId, (_, _) => UUID.randomUUID())

  /** For the demo project with the given index: the same ids every time, so that seeding twice changes nothing. */
  def seedDefaultTemplates(projectId: UUID, projectIndex: Int): Unit =
    createDefaultTemplates(
      projectId,
      (group, position) =>
        DemoSeedIds.id(group, projectIndex * MilestoneStepTemplatesPreviewData.IdsPerProject + position)
    )

  private def createDefaultTemplates(projectId: UUID, idOf: (Int, Int) => UUID): Unit = {
    val now = clock.currentInstant
    DefaultMilestoneStepTemplates.defaults.zipWithIndex.foreach { case ((name, description), position) =>
      val template = templates.upsert(
        PreviewMilestoneStepTemplate(idOf(DemoSeedIds.MilestoneStepTemplate, position), projectId, now)
      )
      templateVersions.upsert(
        MilestoneStepTemplateVersionDto(
          idOf(DemoSeedIds.MilestoneStepTemplateVersion, position),
          template.id,
          name,
          description,
          now
        )
      )
    }
  }

  def latestVersion(templateId: UUID): Option[MilestoneStepTemplateVersionDto] =
    templateVersions.filter(_.milestoneStepTemplateId == templateId).lastOption

  private var deletionBlockers: List[UUID => List[String]] = List.empty

  private var deletionHooks: List[UUID => Unit] = List.empty

  /** Lets the milestone templates name themselves when they use a step template, which blocks deleting it. */
  def onDeletionCheck(blocker: UUID => List[String]): Unit = deletionBlockers = deletionBlockers :+ blocker

  /** Lets the milestone templates drop a deleted step template from their steps. */
  def onTemplateDeleted(hook: UUID => Unit): Unit = deletionHooks = deletionHooks :+ hook

  /** The names of the milestone templates that use the step template; empty when it can be deleted. */
  def namesBlockingDeletion(id: UUID): List[String] = deletionBlockers.flatMap(_(id)).distinct.sorted

  def deleteTemplate(id: UUID): Boolean = {
    deletionHooks.foreach(_(id))
    templateVersions.deleteWhere(_.milestoneStepTemplateId == id)
    templates.delete(id)
  }

  private def deleteProjectData(projectId: UUID): Unit =
    templates.filter(_.projectId == projectId).foreach(template => deleteTemplate(template.id))
}

object MilestoneStepTemplatesPreviewData {
  val TemplatesName = "milestone-step-templates"
  val VersionsName = "milestone-step-template-versions"

  /** Room for the ids of the default templates of one demo project. */
  val IdsPerProject = 100
}
