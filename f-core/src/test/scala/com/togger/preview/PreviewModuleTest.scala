package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.PageRequest
import com.togger.ui.dataexportconfig.DataExportConfigFeServicePreview
import com.togger.ui.organization.OrganizationServiceFePreview
import com.togger.ui.project.ProjectServicePreview
import com.togger.ui.task.{TaskLabelFeServicePreview, TaskStatusFeServicePreview}

class PreviewModuleTest extends AsyncJsWordSpec {

  private def snapshot(env: PreviewTestEnv): Map[String, Option[String]] =
    env.module.db.registeredCollectionNames.map { name =>
      name -> env.storage.getItem(PreviewDb.keyFor(name))
    }.toMap

  testNameFromClassName should {

    "decode every seeded collection" in { _ =>
      val env = new PreviewTestEnv
      env.module.resetAndReseed()
      env.module.db.registeredCollectionNames.size should be > 10
      env.module.db.undecodableCollectionNames shouldBe Nil
      all(snapshot(env).values.toList) shouldBe defined
    }

    "seed idempotently" in { _ =>
      val env = new PreviewTestEnv
      val before = snapshot(env)
      val seeders = env.module.seeders
      seeders.foreach(_.seed())
      val after = snapshot(env)
      after.keys.filter(name => after(name) != before(name)).toList shouldBe Nil
    }

    "restore the demo data on reset" in { _ =>
      val env = new PreviewTestEnv
      env.loginAsDemo()
      val m = env.module
      val projects = new ProjectServicePreview(m.context, m.accounts, m.organizations, m.projects, m.projectLabels.labelsOf)
      val before = snapshot(env)
      m.projects.deleteProject(PreviewDemo.ProjectId)
      snapshot(env) should not be before
      m.resetAndReseed()
      for {
        found <- env.run(projects.findById(PreviewDemo.ProjectId))
      } yield {
        found.isRight shouldBe true
        snapshot(env).keySet shouldBe before.keySet
        m.db.isSeeded shouldBe true
      }
    }

    "serve one call per service from the seed" in { _ =>
      val env = new PreviewTestEnv
      env.loginAsDemo()
      val m = env.module
      val page = PageRequest(1, 50)
      val organizations = new OrganizationServiceFePreview(m.context, m.accounts, m.organizations, m.projects)
      val statuses = new TaskStatusFeServicePreview(m.context, m.projects, m.taskConfig, m.taskStats)
      val labels = new TaskLabelFeServicePreview(m.context, m.projects, m.taskConfig)
      val exportConfigs = new DataExportConfigFeServicePreview(m.context, m.projects, m.dataSync)
      for {
        orgs <- env.run(organizations.findAll(page))
        taskStatuses <- env.run(statuses.findByProjectId(PreviewDemo.ProjectId, page))
        taskLabels <- env.run(labels.findByProjectId(PreviewDemo.ProjectId, page))
        configs <- env.run(exportConfigs.findByProjectId(PreviewDemo.ProjectId, page))
      } yield {
        orgs.map(_.items.nonEmpty) shouldBe Right(true)
        taskStatuses.map(_.items.nonEmpty) shouldBe Right(true)
        taskLabels.map(_.items.nonEmpty) shouldBe Right(true)
        configs.map(_.items.nonEmpty) shouldBe Right(true)
      }
    }
  }
}
