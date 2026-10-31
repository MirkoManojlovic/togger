package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, PageRequest, Unauthenticated}
import com.togger.demo.DemoIdentity
import com.togger.dto._
import com.togger.ui.milestonesteptemplate.MilestoneStepTemplateFeServicePreview
import com.togger.ui.milestonetemplate.MilestoneTemplateFeServicePreview
import com.togger.ui.project.ProjectServicePreview

import java.util.UUID
import scala.concurrent.Future

class PreviewMilestoneTemplatesDomainTest extends AsyncJsWordSpec {

  private val defaultNames = List("Bug fix", "Migration", "New feature", "Refactoring", "Release", "Research spike")

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val templates = new MilestoneTemplateFeServicePreview(m.context, m.milestoneTemplates, m.milestoneStepTemplates)
    val stepTemplates = new MilestoneStepTemplateFeServicePreview(m.context, m.milestoneStepTemplates)
    val projects = new ProjectServicePreview(m.context, m.accounts, m.organizations, m.projects, m.projectLabels.labelsOf)
    env.loginAsDemo()

    def stepTemplateIds(project: UUID = DemoIdentity.ProjectId): Future[Map[String, UUID]] =
      env.run(stepTemplates.findAllByProjectId(project)).map(_.toOption.get.map(t => t.name -> t.id).toMap)
  }

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "answer unauthenticated without a session" in { _ =>
      val env = new PreviewTestEnv
      val m = env.module
      val templates = new MilestoneTemplateFeServicePreview(m.context, m.milestoneTemplates, m.milestoneStepTemplates)
      env.run(templates.findByProjectId(DemoIdentity.ProjectId, PageRequest(1, 50))).map(_ shouldBe Left(Unauthenticated))
    }

    "seed the six default templates with their steps in order" in { _ =>
      val s = new Services
      s.env.run(s.templates.findAllByProjectId(DemoIdentity.ProjectId)).map { result =>
        val byName = result.toOption.get.map(t => t.name -> t.steps.map(_.name)).toMap
        result.map(_.map(_.name)) shouldBe Right(defaultNames)
        byName("New feature") shouldBe List("Design", "Prototype", "Development", "Staging", "Production")
        byName("Bug fix") shouldBe List("Development", "Staging", "Production")
        byName("Research spike") shouldBe List("Design", "Prototype")
        result.toOption.get.flatMap(_.steps).forall(_.description.contains("Exit criteria")) shouldBe true
      }
    }

    "create a template with its steps and save a new version only when something changed" in { _ =>
      val s = new Services
      for {
        ids <- s.stepTemplateIds()
        created <- s.env.run(
          s.templates.createMilestoneTemplate(
            CreateMilestoneTemplateReq(DemoIdentity.ProjectId, "QA", "d", List(ids("Staging"), ids("Design")))
          )
        )
        id = created.toOption.get.id
        same <- s.env.run(
          s.templates.updateMilestoneTemplate(UpdateMilestoneTemplateReq(id, "QA", "d", List(ids("Staging"), ids("Design"))))
        )
        reordered <- s.env.run(
          s.templates.updateMilestoneTemplate(UpdateMilestoneTemplateReq(id, "QA", "d", List(ids("Design"), ids("Staging"))))
        )
        versions <- s.env.run(s.templates.listAllVersions(id))
      } yield {
        created.map(_.steps.map(_.name)) shouldBe Right(List("Staging", "Design"))
        same.map(_.latestVersion) shouldBe created.map(_.latestVersion)
        reordered.map(_.steps.map(_.name)) shouldBe Right(List("Design", "Staging"))
        versions.map(_.map(_.steps.map(_.name))) shouldBe Right(List(List("Design", "Staging"), List("Staging", "Design")))
      }
    }

    "reject a step template of another project or one that does not exist" in { _ =>
      val s = new Services
      for {
        ids <- s.stepTemplateIds()
        foreignIds <- s.stepTemplateIds(DemoIdentity.SecondProjectId)
        foreign <- s.env.run(
          s.templates.createMilestoneTemplate(
            CreateMilestoneTemplateReq(DemoIdentity.ProjectId, "X", "d", List(foreignIds("Design")))
          )
        )
        missing <- s.env.run(
          s.templates.createMilestoneTemplate(
            CreateMilestoneTemplateReq(DemoIdentity.ProjectId, "X", "d", List(UUID.randomUUID()))
          )
        )
        templates <- s.env.run(s.templates.findAllByProjectId(DemoIdentity.ProjectId))
      } yield {
        isBadRequest(foreign) shouldBe true
        isBadRequest(missing) shouldBe true
        templates.map(_.map(_.name)) shouldBe Right(defaultNames)
        ids.keySet should contain("Design")
      }
    }

    "block deleting a step template that a milestone template uses, until that one is gone" in { _ =>
      val s = new Services
      for {
        ids <- s.stepTemplateIds()
        created <- s.env.run(
          s.templates.createMilestoneTemplate(
            CreateMilestoneTemplateReq(DemoIdentity.ProjectId, "Only prototype", "d", List(ids("Prototype")))
          )
        )
        blocked <- s.env.runEmpty(s.stepTemplates.deleteMilestoneStepTemplateById(ids("Prototype")))
        _ <- s.env.runEmpty(s.templates.deleteMilestoneTemplateById(created.toOption.get.id))
        // The defaults use the prototype too: delete those templates, then the step template can go.
        all <- s.env.run(s.templates.findAllByProjectId(DemoIdentity.ProjectId))
        _ <- Future.sequence(
          all.toOption.get.map(t => s.env.runEmpty(s.templates.deleteMilestoneTemplateById(t.id)))
        )
        deleted <- s.env.runEmpty(s.stepTemplates.deleteMilestoneStepTemplateById(ids("Prototype")))
      } yield {
        isBadRequest(blocked) shouldBe true
        blocked.left.exists(_.toString.contains("New feature")) shouldBe true
        deleted.isRight shouldBe true
      }
    }

    "drop a deleted step template from the older versions of the milestone templates" in { _ =>
      val s = new Services
      for {
        ids <- s.stepTemplateIds()
        created <- s.env.run(
          s.templates.createMilestoneTemplate(
            CreateMilestoneTemplateReq(DemoIdentity.ProjectId, "Old", "d", List(ids("Staging"), ids("Design")))
          )
        )
        id = created.toOption.get.id
        _ <- s.env.run(
          s.templates.updateMilestoneTemplate(UpdateMilestoneTemplateReq(id, "Old", "d", List(ids("Design"))))
        )
        // Staging is only in the older version of this template, but still used by the default templates.
        all <- s.env.run(s.templates.findAllByProjectId(DemoIdentity.ProjectId))
        _ <- Future.sequence(
          all.toOption.get.filterNot(_.id == id).map(t => s.env.runEmpty(s.templates.deleteMilestoneTemplateById(t.id)))
        )
        deleted <- s.env.runEmpty(s.stepTemplates.deleteMilestoneStepTemplateById(ids("Staging")))
        versions <- s.env.run(s.templates.listAllVersions(id))
      } yield {
        deleted.isRight shouldBe true
        versions.map(_.map(_.steps.map(_.name))) shouldBe Right(List(List("Design"), List("Design")))
      }
    }

    "delete a template and answer bad request for one that does not exist" in { _ =>
      val s = new Services
      for {
        all <- s.env.run(s.templates.findAllByProjectId(DemoIdentity.ProjectId))
        id = all.toOption.get.head.id
        deleted <- s.env.runEmpty(s.templates.deleteMilestoneTemplateById(id))
        again <- s.env.runEmpty(s.templates.deleteMilestoneTemplateById(id))
        missing <- s.env.run(s.templates.findMilestoneTemplateById(UUID.randomUUID()))
      } yield {
        deleted.isRight shouldBe true
        isBadRequest(again) shouldBe true
        isBadRequest(missing) shouldBe true
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
        templates.map(_.find(_.name == "Release").map(_.steps.map(_.name))) shouldBe Right(Some(List("Staging", "Production")))
        after.map(_.size) shouldBe Right(0)
      }
    }
  }
}
