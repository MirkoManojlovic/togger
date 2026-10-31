package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, PageRequest, Unauthenticated}
import com.togger.demo.DemoIdentity
import com.togger.dto._
import com.togger.ui.milestonesteptemplate.MilestoneStepTemplateFeServicePreview
import com.togger.ui.project.ProjectServicePreview

import java.util.UUID

class PreviewMilestoneStepTemplatesDomainTest extends AsyncJsWordSpec {

  private val defaultNames = List("Design", "Development", "Production", "Prototype", "Staging")

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val templates = new MilestoneStepTemplateFeServicePreview(m.context, m.milestoneStepTemplates)
    val projects = new ProjectServicePreview(m.context, m.accounts, m.organizations, m.projects, m.projectLabels.labelsOf)
    env.loginAsDemo()
  }

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "answer unauthenticated without a session" in { _ =>
      val env = new PreviewTestEnv
      val templates = new MilestoneStepTemplateFeServicePreview(env.module.context, env.module.milestoneStepTemplates)
      env.run(templates.findByProjectId(DemoIdentity.ProjectId, PageRequest(1, 50))).map(_ shouldBe Left(Unauthenticated))
    }

    "seed the five default templates of the demo project, sorted by name" in { _ =>
      val s = new Services
      s.env.run(s.templates.findAllByProjectId(DemoIdentity.ProjectId)).map { result =>
        result.map(_.map(_.name)) shouldBe Right(defaultNames)
        result.map(_.forall(_.description.contains("Exit criteria"))) shouldBe Right(true)
      }
    }

    "paginate the templates" in { _ =>
      val s = new Services
      for {
        second <- s.env.run(s.templates.findByProjectId(DemoIdentity.ProjectId, PageRequest(2, 2)))
      } yield {
        second.map(_.items.map(_.name)) shouldBe Right(List("Production", "Prototype"))
        second.map(_.totalNumberOfItems) shouldBe Right(5L)
      }
    }

    "create a template and save a new version only when the content changed" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(
          s.templates.createMilestoneStepTemplate(CreateMilestoneStepTemplateReq(DemoIdentity.ProjectId, "QA", "v1"))
        )
        id = created.toOption.get.id
        same <- s.env.run(s.templates.updateMilestoneStepTemplate(UpdateMilestoneStepTemplateReq(id, "QA", "v1")))
        changed <- s.env.run(s.templates.updateMilestoneStepTemplate(UpdateMilestoneStepTemplateReq(id, "QA", "v2")))
        versions <- s.env.run(s.templates.listAllVersions(id))
        found <- s.env.run(s.templates.findMilestoneStepTemplateById(id))
      } yield {
        same.map(_.latestVersion) shouldBe created.map(_.latestVersion)
        changed.map(_.description) shouldBe Right("v2")
        versions.map(_.map(_.description)) shouldBe Right(List("v2", "v1"))
        found.map(_.description) shouldBe Right("v2")
      }
    }

    "delete a template and answer bad request for one that does not exist" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(
          s.templates.createMilestoneStepTemplate(CreateMilestoneStepTemplateReq(DemoIdentity.ProjectId, "QA", "v1"))
        )
        id = created.toOption.get.id
        deleted <- s.env.runEmpty(s.templates.deleteMilestoneStepTemplateById(id))
        again <- s.env.runEmpty(s.templates.deleteMilestoneStepTemplateById(id))
        missing <- s.env.run(s.templates.findMilestoneStepTemplateById(UUID.randomUUID()))
        update <- s.env.run(
          s.templates.updateMilestoneStepTemplate(UpdateMilestoneStepTemplateReq(UUID.randomUUID(), "x", "y"))
        )
      } yield {
        deleted.isRight shouldBe true
        isBadRequest(again) shouldBe true
        isBadRequest(missing) shouldBe true
        isBadRequest(update) shouldBe true
      }
    }

    "give a project created at runtime the default templates and drop them with the project" in { _ =>
      val s = new Services
      for {
        project <- s.env.run(
          s.projects.createProject(CreateProjectReq(DemoIdentity.OrganizationId, "Fresh", "", "🆕", Seq.empty, Seq.empty))
        )
        created = project.toOption.get
        templates <- s.env.run(s.templates.findAllByProjectId(created.id))
        _ <- s.env.runEmpty(s.projects.deleteProject(created.id))
        after <- s.env.run(s.templates.findAllByProjectId(created.id))
      } yield {
        templates.map(_.map(_.name)) shouldBe Right(defaultNames)
        after.map(_.size) shouldBe Right(0)
        s.env.module.milestoneStepTemplates.templateVersions.all.exists(_.name == "Design") shouldBe true
      }
    }
  }
}
