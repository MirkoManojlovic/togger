package com.togger.preview

import com.togger.demo.DemoProject

/** Gives every demo project the default milestone step templates; needs the projects to be seeded first. */
class MilestoneStepTemplatesPreviewSeed(data: MilestoneStepTemplatesPreviewData, demoProjects: List[DemoProject])
    extends PreviewSeeder {

  override def seed(): Unit = demoProjects.zipWithIndex.foreach { case (project, index) =>
    data.seedDefaultTemplates(project.id, index)
  }
}
