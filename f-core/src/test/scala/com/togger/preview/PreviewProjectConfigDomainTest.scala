package com.togger.preview

import com.togger.demo.DemoProjectLabelsData
import com.togger.demo.DemoTaskConfigData

import com.togger.demo.DemoIdentity

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, NotFound, PageRequest, Unauthenticated}
import com.togger.dto._
import com.togger.ui.project.ProjectServicePreview
import com.togger.ui.projectlabel.{ProjectLabelCategoryFeServicePreview, ProjectLabelFeServicePreview}
import com.togger.ui.task.{TaskLabelFeServicePreview, TaskStatusFeServicePreview}
import com.togger.ui.tasktemplate.TaskTemplateFeServicePreview

import java.util.UUID

class PreviewProjectConfigDomainTest extends AsyncJsWordSpec {

  private val firstPage = PageRequest(1, 50)

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val statuses = new TaskStatusFeServicePreview(m.context, m.projects, m.taskConfig, m.taskStats)
    val labels = new TaskLabelFeServicePreview(m.context, m.projects, m.taskConfig)
    val templates = new TaskTemplateFeServicePreview(m.context, m.taskConfig)
    val projectLabels = new ProjectLabelFeServicePreview(m.context, m.projectLabels)
    val categories = new ProjectLabelCategoryFeServicePreview(m.context, m.projectLabels)
    val projects = new ProjectServicePreview(m.context, m.accounts, m.organizations, m.projects, m.projectLabels.labelsOf)
    env.loginAsDemo()
  }

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "answer unauthenticated without a session" in { _ =>
      val env = new PreviewTestEnv
      val statuses = new TaskStatusFeServicePreview(env.module.context, env.module.projects, env.module.taskConfig, env.module.taskStats)
      env.run(statuses.findByProjectId(DemoIdentity.ProjectId, firstPage)).map(_ shouldBe Left(Unauthenticated))
    }

    "seed the default statuses sorted by name and paginate them" in { _ =>
      val s = new Services
      for {
        all <- s.env.run(s.statuses.findByProjectId(DemoIdentity.ProjectId, firstPage))
        second <- s.env.run(s.statuses.findByProjectId(DemoIdentity.ProjectId, PageRequest(2, 3)))
      } yield {
        all.map(_.items.map(_.name)) shouldBe Right(
          List("Backlog", "Blocked", "Done", "In progress", "Testing", "Todo", "Waiting", "Won't do")
        )
        second.map(r => (r.items.size, r.totalNumberOfItems, r.totalNumberOfPages)) shouldBe Right((3, 8L, 3L))
      }
    }

    "create, update and delete a status but keep Done" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(s.statuses.createTaskStatus(CreateTaskStatusReq(DemoIdentity.ProjectId, "Review", "In review", "👀")))
        updated <- s.env.run(
          s.statuses.updateTaskStatus(UpdateTaskStatusResp(created.toOption.get.id, DemoIdentity.ProjectId, "Code review", "In review", "👀"))
        )
        found <- s.env.run(s.statuses.findTaskStatusById(created.toOption.get.id))
        deleted <- s.env.runEmpty(s.statuses.deleteTaskStatusById(created.toOption.get.id))
        gone <- s.env.run(s.statuses.findTaskStatusById(created.toOption.get.id))
        done = s.env.module.taskConfig.statuses.all.find(st => st.projectId == DemoIdentity.ProjectId && st.name == "Done").get
        doneDelete <- s.env.runEmpty(s.statuses.deleteTaskStatusById(done.id))
        unknownProject <- s.env.run(s.statuses.createTaskStatus(CreateTaskStatusReq(UUID.randomUUID(), "X", "", "")))
      } yield {
        updated.map(_.name) shouldBe Right("Code review")
        found.map(_.name) shouldBe Right("Code review")
        deleted.isRight shouldBe true
        gone shouldBe Left(NotFound)
        isBadRequest(doneDelete) shouldBe true
        unknownProject shouldBe Left(NotFound)
      }
    }

    "refuse to delete a status that tasks use" in { _ =>
      val s = new Services
      val used = s.env.module.taskConfig.statuses.all.find(_.name == "Todo").get.id
      s.env.module.taskStats.source = new TaskStatsSource {
        override def statusInUse(statusId: UUID): Boolean = statusId == used
        override def activityOf(milestoneId: UUID, from: java.time.LocalDate, to: java.time.LocalDate) = Nil
      }
      s.env.runEmpty(s.statuses.deleteTaskStatusById(used)).map(result => isBadRequest(result) shouldBe true)
    }

    "give a project created at runtime the default statuses and drop them with the project" in { _ =>
      val s = new Services
      for {
        project <- s.env.run(
          s.projects.createProject(CreateProjectReq(DemoIdentity.OrganizationId, "Fresh", "", "🆕", Seq.empty, Seq.empty))
        )
        created = project.toOption.get
        statuses <- s.env.run(s.statuses.findAllByProjectId(created.id))
        _ <- s.env.runEmpty(s.projects.deleteProject(created.id))
        after <- s.env.run(s.statuses.findAllByProjectId(created.id))
      } yield {
        statuses.map(_.size) shouldBe Right(8)
        after.map(_.size) shouldBe Right(0)
      }
    }

    "manage task labels per project" in { _ =>
      val s = new Services
      for {
        seeded <- s.env.run(s.labels.findAllByProjectId(DemoIdentity.ProjectId))
        created <- s.env.run(s.labels.createTaskLabel(CreateTaskLabelReq(DemoIdentity.ProjectId, "security", "Security work", "🔒")))
        updated <- s.env.run(
          s.labels.updateTaskLabel(UpdateTaskLabelResp(created.toOption.get.id, DemoIdentity.ProjectId, "sec", "Security work", "🔒"))
        )
        deleted <- s.env.runEmpty(s.labels.deleteTaskLabelById(created.toOption.get.id))
        missing <- s.env.runEmpty(s.labels.deleteTaskLabelById(created.toOption.get.id))
      } yield {
        seeded.map(_.map(_.name)) shouldBe Right(List("bug", "chore", "docs", "feature", "refactor", "test"))
        updated.map(_.name) shouldBe Right("sec")
        deleted.isRight shouldBe true
        missing shouldBe Left(NotFound)
      }
    }

    "fill the labels of the projects and clean them when a label is deleted" in { _ =>
      val s = new Services
      for {
        project <- s.env.run(s.projects.findById(DemoIdentity.ProjectId))
        deleted <- s.env.runEmpty(s.projectLabels.deleteProjectLabelById(DemoProjectLabelsData.ScalaLabelId))
        after <- s.env.run(s.projects.findById(DemoIdentity.ProjectId))
      } yield {
        project.map(_.projectLabels.map(_.name)) shouldBe Right(List("Backend", "Customer facing", "Scala", "Web"))
        deleted.isRight shouldBe true
        after.map(_.projectLabels.map(_.name)) shouldBe Right(List("Backend", "Customer facing", "Web"))
      }
    }

    "validate project labels and categories" in { _ =>
      val s = new Services
      for {
        category <- s.env.run(s.categories.createProjectLabelCategory(CreateProjectLabelCategoryReq("Domain")))
        label <- s.env.run(s.projectLabels.createProjectLabel(CreateProjectLabelReq("Payments", "", category.toOption.get.id)))
        noCategory <- s.env.run(s.projectLabels.createProjectLabel(CreateProjectLabelReq("Orphan", "", UUID.randomUUID())))
        blocked <- s.env.runEmpty(s.categories.deleteProjectLabelCategoryById(category.toOption.get.id))
        _ <- s.env.runEmpty(s.projectLabels.deleteProjectLabelById(label.toOption.get.id))
        removed <- s.env.runEmpty(s.categories.deleteProjectLabelCategoryById(category.toOption.get.id))
        categories <- s.env.run(s.categories.findEvery())
      } yield {
        noCategory shouldBe Left(NotFound)
        isBadRequest(blocked) shouldBe true
        removed.isRight shouldBe true
        categories.map(_.map(_.name)) shouldBe Right(List("Audience", "Platform", "Technology"))
      }
    }

    "link one template per label, version it and list the versions" in { _ =>
      val s = new Services
      val bugLabel = DemoTaskConfigData.labelId(0, 1)
      val choreLabel = DemoTaskConfigData.labelId(0, 5)
      for {
        seeded <- s.env.run(s.templates.findAllByProjectId(DemoIdentity.ProjectId))
        clash <- s.env.run(s.templates.createTaskTemplate(CreateTaskTemplateReq(DemoIdentity.ProjectId, "Another bug", "x", Some(bugLabel))))
        foreign <- s.env.run(
          s.templates.createTaskTemplate(CreateTaskTemplateReq(DemoIdentity.ProjectId, "Foreign", "x", Some(DemoTaskConfigData.labelId(1, 5))))
        )
        created <- s.env.run(s.templates.createTaskTemplate(CreateTaskTemplateReq(DemoIdentity.ProjectId, "Chore", "v1", Some(choreLabel))))
        id = created.toOption.get.id
        same <- s.env.run(s.templates.updateTaskTemplate(UpdateTaskTemplateReq(id, "Chore", "v1", Some(choreLabel))))
        changed <- s.env.run(s.templates.updateTaskTemplate(UpdateTaskTemplateReq(id, "Chore", "v2", None)))
        versions <- s.env.run(s.templates.listAllVersions(id))
        deleted <- s.env.runEmpty(s.templates.deleteTaskTemplateById(id))
        again <- s.env.runEmpty(s.templates.deleteTaskTemplateById(id))
      } yield {
        seeded.map(_.map(_.name)) shouldBe Right(List("Bug report", "Definition of done", "Feature request"))
        seeded.map(_.find(_.name == "Bug report").flatMap(_.taskLabelName)) shouldBe Right(Some("bug"))
        isBadRequest(clash) shouldBe true
        isBadRequest(foreign) shouldBe true
        created.map(_.taskLabelName) shouldBe Right(Some("chore"))
        same.map(_.latestVersion) shouldBe created.map(_.latestVersion)
        changed.map(_.body) shouldBe Right("v2")
        versions.map(_.map(_.body)) shouldBe Right(List("v2", "v1"))
        deleted.isRight shouldBe true
        isBadRequest(again) shouldBe true
      }
    }
  }
}
