package com.togger.demo

import java.util.UUID

object DemoProjectLabelsData {
  private def categoryId(n: Int): UUID = DemoSeedIds.id(DemoSeedIds.ProjectLabelCategory, n)
  private def labelId(n: Int): UUID = DemoSeedIds.id(DemoSeedIds.ProjectLabel, n)

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

  val categories: List[DemoProjectLabelCategory] = List(
    DemoProjectLabelCategory(TechnologyCategoryId, "Technology"),
    DemoProjectLabelCategory(PlatformCategoryId, "Platform"),
    DemoProjectLabelCategory(AudienceCategoryId, "Audience")
  )

  val labels: List[DemoProjectLabel] = List(
    DemoProjectLabel(ScalaLabelId, "Scala", "Written mostly in Scala", TechnologyCategoryId),
    DemoProjectLabel(TypeScriptLabelId, "TypeScript", "Written mostly in TypeScript", TechnologyCategoryId),
    DemoProjectLabel(KotlinLabelId, "Kotlin", "Written mostly in Kotlin", TechnologyCategoryId),
    DemoProjectLabel(WebLabelId, "Web", "Runs in the browser", PlatformCategoryId),
    DemoProjectLabel(MobileLabelId, "Mobile", "Runs on iOS and Android", PlatformCategoryId),
    DemoProjectLabel(BackendLabelId, "Backend", "Services and APIs without a user interface", PlatformCategoryId),
    DemoProjectLabel(CustomerFacingLabelId, "Customer facing", "Used directly by customers", AudienceCategoryId),
    DemoProjectLabel(InternalLabelId, "Internal", "Used by the team and the community", AudienceCategoryId)
  )

  def projectLabelIds(projectId: UUID): List[UUID] = projectLabels.getOrElse(projectId, List.empty)

  private val projectLabels: Map[UUID, List[UUID]] = Map(
    DemoIdentity.ProjectId -> List(ScalaLabelId, WebLabelId, BackendLabelId, CustomerFacingLabelId),
    DemoIdentity.SecondProjectId -> List(KotlinLabelId, MobileLabelId, CustomerFacingLabelId),
    DemoIdentity.SideProjectId -> List(TypeScriptLabelId, WebLabelId, InternalLabelId)
  )
}
