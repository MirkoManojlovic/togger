package com.togger.preview

import com.togger.demo.DemoProject

/** Gives every demo project the default milestone templates; needs the step templates to be seeded first. */
class MilestoneTemplatesPreviewSeed(data: MilestoneTemplatesPreviewData, demoProjects: List[DemoProject])
    extends PreviewSeeder {

  override def seed(): Unit =
    demoProjects.zipWithIndex.foreach { case (project, index) => data.seedDefaultTemplates(project.id, index) }
}
