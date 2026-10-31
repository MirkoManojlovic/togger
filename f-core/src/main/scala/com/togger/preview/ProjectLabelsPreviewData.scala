package com.togger.preview

import com.togger.dto.{FindProjectLabelCategoryResp, FindProjectLabelResp}
import io.circe.generic.auto._

import java.util.UUID

/** Global (not project scoped) project label categories and labels; projects reference labels by id. */
class ProjectLabelsPreviewData(db: PreviewDb, projects: ProjectsPreviewData) {

  val categories: PreviewCollection[FindProjectLabelCategoryResp] =
    db.collection[FindProjectLabelCategoryResp](ProjectLabelsPreviewData.CategoriesName, _.id)

  val labels: PreviewCollection[FindProjectLabelResp] =
    db.collection[FindProjectLabelResp](ProjectLabelsPreviewData.LabelsName, _.id)

  def labelsOf(ids: Seq[UUID]): List[FindProjectLabelResp] =
    labels.filter(label => ids.contains(label.id)).sortBy(label => (label.name, label.id.toString))

  def deleteLabel(id: UUID): Boolean = {
    projects.projects.all.filter(_.projectLabelIds.contains(id)).foreach { project =>
      projects.projects.upsert(project.copy(projectLabelIds = project.projectLabelIds.filterNot(_ == id)))
    }
    labels.delete(id)
  }
}

object ProjectLabelsPreviewData {
  val CategoriesName = "project-label-categories"
  val LabelsName = "project-labels"
}
