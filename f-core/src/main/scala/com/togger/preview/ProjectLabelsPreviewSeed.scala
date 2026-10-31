package com.togger.preview

import com.togger.dto.{FindProjectLabelCategoryResp, FindProjectLabelResp}

import java.util.UUID

/** Seeds the project label categories and labels and tags the demo projects; needs the projects to be seeded first. */
class ProjectLabelsPreviewSeed(data: ProjectLabelsPreviewData, projects: ProjectsPreviewData) extends PreviewSeeder {
  import ProjectLabelsPreviewSeed._

  override def seed(): Unit = {
    categories.foreach { case (id, name) => data.categories.upsert(FindProjectLabelCategoryResp(id, name)) }
    labels.foreach { case (id, name, description, categoryId) =>
      data.labels.upsert(FindProjectLabelResp(id, name, description, categoryId))
    }
    projectLabels.foreach { case (projectId, labelIds) =>
      projects.projects.update(projectId)(_.copy(projectLabelIds = labelIds))
    }
  }
}

object ProjectLabelsPreviewSeed {
  private def categoryId(n: Int): UUID = PreviewSeedIds.id(PreviewSeedIds.ProjectLabelCategory, n)
  private def labelId(n: Int): UUID = PreviewSeedIds.id(PreviewSeedIds.ProjectLabel, n)

  val TechnologyCategoryId: UUID = categoryId(1)
  val PlatformCategoryId: UUID = categoryId(2)
  val AudienceCategoryId: UUID = categoryId(3)

  val ScalaLabelId: UUID = labelId(1)
  val TypeScriptLabelId: UUID = labelId(2)
  val KotlinLabelId: UUID = labelId(3)
  val WebLabelId: UUID = labelId(4)
  val MobileLabelId: UUID = labelId(5)
  val BackendLabelId: UUID = labelId(6)
  val CustomerFacingLabelId: UUID = labelId(7)
  val InternalLabelId: UUID = labelId(8)

  private val categories = List(
    TechnologyCategoryId -> "Technology",
    PlatformCategoryId -> "Platform",
    AudienceCategoryId -> "Audience"
  )

  private val labels = List(
    (ScalaLabelId, "Scala", "Written mostly in Scala", TechnologyCategoryId),
    (TypeScriptLabelId, "TypeScript", "Written mostly in TypeScript", TechnologyCategoryId),
    (KotlinLabelId, "Kotlin", "Written mostly in Kotlin", TechnologyCategoryId),
    (WebLabelId, "Web", "Runs in the browser", PlatformCategoryId),
    (MobileLabelId, "Mobile", "Runs on iOS and Android", PlatformCategoryId),
    (BackendLabelId, "Backend", "Services and APIs without a user interface", PlatformCategoryId),
    (CustomerFacingLabelId, "Customer facing", "Used directly by customers", AudienceCategoryId),
    (InternalLabelId, "Internal", "Used by the team and the community", AudienceCategoryId)
  )

  private val projectLabels = List(
    PreviewDemo.ProjectId -> List(ScalaLabelId, WebLabelId, BackendLabelId, CustomerFacingLabelId),
    PreviewDemo.SecondProjectId -> List(KotlinLabelId, MobileLabelId, CustomerFacingLabelId),
    OrganizationsPreviewSeed.SideProjectId -> List(TypeScriptLabelId, WebLabelId, InternalLabelId)
  )
}
