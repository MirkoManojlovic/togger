package com.togger.preview

import com.togger.demo.DemoProjectLabel
import com.togger.demo.DemoProjectLabelCategory
import com.togger.dto.FindProjectLabelCategoryResp
import com.togger.dto.FindProjectLabelResp

/** Seeds the project label categories and labels; the labels of each project come with the seeded projects. */
class ProjectLabelsPreviewSeed(
  data: ProjectLabelsPreviewData,
  categories: List[DemoProjectLabelCategory],
  labels: List[DemoProjectLabel]
) extends PreviewSeeder {

  override def seed(): Unit = {
    categories.foreach(category => data.categories.upsert(FindProjectLabelCategoryResp(category.id, category.name)))
    labels.foreach { label =>
      data.labels.upsert(FindProjectLabelResp(label.id, label.name, label.description, label.categoryId))
    }
  }
}
