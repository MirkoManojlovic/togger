package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, PageRequest, Unauthenticated}
import com.togger.dto._
import com.togger.logentry.LogEntryStatus
import com.togger.task.TaskPriority
import com.togger.ui.contributions.ContributionsFeServicePreview
import com.togger.ui.task.TaskFeServicePreview
import com.togger.ui.timebox.TimeboxFeServicePreview

import java.time.{Instant, LocalDate}
import java.util.UUID

class PreviewTasksDomainTest extends AsyncJsWordSpec {

  private val firstPage = PageRequest(1, 100)
  private val monday = LocalDate.parse("2026-06-15")

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val tasks = new TaskFeServicePreview(m.context, m.projects, m.taskConfig, m.tasks)
    val timeboxes = new TimeboxFeServicePreview(m.context, m.tasks)
    val contributions =
      new ContributionsFeServicePreview(m.context, m.projects, m.milestones, m.taskConfig, m.accounts, m.tasks)
    env.loginAsDemo()

    val todoStatus: UUID = TaskConfigPreviewSeed.statusId(0, 4)
    val doneStatus: UUID = TaskConfigPreviewSeed.statusId(0, 6)

    def newTask(name: String, start: LocalDate = monday.plusDays(1), due: LocalDate = monday.plusDays(3)): CreateTaskAndLinksReq =
      CreateTaskAndLinksReq(
        CreateTaskReq(
          PreviewDemo.ProjectId,
          name,
          "desc",
          Some(PreviewDemo.SecondAccountId),
          PreviewDemo.AccountId,
          todoStatus,
          Instant.parse("2026-06-15T10:00:00Z"),
          Instant.parse("2026-06-15T10:00:00Z"),
          TaskPriority.High,
          None,
          start,
          due,
          Seq(TaskConfigPreviewSeed.labelId(0, 1))
        ),
        Seq(CreateLinkReq("Doc", "https://example.com"), CreateLinkReq("", ""))
      )
  }

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "answer unauthenticated without a session" in { _ =>
      val env = new PreviewTestEnv
      val tasks = new TaskFeServicePreview(env.module.context, env.module.projects, env.module.taskConfig, env.module.tasks)
      env.run(tasks.findByProjectId(PreviewDemo.ProjectId, TaskFilter.empty, firstPage)).map(_ shouldBe Left(Unauthenticated))
    }

    "list the seeded tasks newest first without the archived ones" in { _ =>
      val s = new Services
      for {
        all <- s.env.run(s.tasks.findByProjectId(PreviewDemo.ProjectId, TaskFilter.empty, firstPage))
        withArchived <- s.env.run(
          s.tasks.findByProjectId(PreviewDemo.ProjectId, TaskFilter(includeArchived = true), firstPage)
        )
        paged <- s.env.run(s.tasks.findByProjectId(PreviewDemo.ProjectId, TaskFilter.empty, PageRequest(2, 10)))
      } yield {
        all.map(_.totalNumberOfItems) shouldBe Right(21L)
        all.toOption.get.items.map(_.createdAt.toEpochMilli) shouldBe all.toOption.get.items.map(_.createdAt.toEpochMilli).sorted.reverse
        withArchived.map(_.totalNumberOfItems) shouldBe Right(22L)
        paged.map(r => (r.items.size, r.totalNumberOfPages)) shouldBe Right((10, 3L))
      }
    }

    "filter by status, priority, label, assignee, milestone and search" in { _ =>
      val s = new Services
      def count(filter: TaskFilter) =
        s.env.run(s.tasks.findByProjectId(PreviewDemo.ProjectId, filter, firstPage)).map(_.toOption.get.items)
      for {
        done <- count(TaskFilter(statusIds = List(s.doneStatus)))
        critical <- count(TaskFilter(priorities = List(TaskPriority.Critical)))
        bugs <- count(TaskFilter(labelIds = List(TaskConfigPreviewSeed.labelId(0, 1))))
        unassigned <- count(TaskFilter(assigneeIds = List(com.togger.common.TgIds.UNASSIGNED_UUID)))
        noMilestone <- count(TaskFilter(milestoneIds = List(com.togger.common.TgIds.NO_MILESTONE_UUID)))
        beta <- count(TaskFilter(milestoneIds = List(MilestonesPreviewSeed.BetaId)))
        searched <- count(TaskFilter(search = Some(" TIMEZONE ")))
      } yield {
        done.forall(_.taskStatus.id == s.doneStatus) shouldBe true
        done.nonEmpty shouldBe true
        critical.map(_.priority).distinct shouldBe List(TaskPriority.Critical)
        bugs.forall(_.taskLabels.exists(_.name == "bug")) shouldBe true
        unassigned.map(_.assignedTo) .distinct shouldBe List(None)
        noMilestone.forall(_.milestone.isEmpty) shouldBe true
        beta.forall(_.milestone.contains(MilestonesPreviewSeed.BetaId)) shouldBe true
        searched.map(_.name) shouldBe List("Fix the timezone bug in the daily log")
      }
    }

    "create a task with links, find it, update it into a second version and delete it" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(s.tasks.createTask(s.newTask("Brand new")))
        id = created.toOption.get.task.id
        found <- s.env.run(s.tasks.findTaskById(id))
        sameUpdate <- s.env.run(
          s.tasks.updateTask(
            UpdateTaskReq(
              created.toOption.get.task.copy(name = "Brand new").into,
              Seq.empty,
              Seq.empty,
              Seq.empty
            )
          )
        )
        versionsAfterSame <- s.env.run(s.tasks.listTaskVersions(id, firstPage))
        updated <- s.env.run(
          s.tasks.updateTask(
            UpdateTaskReq(
              created.toOption.get.task.copy(name = "Renamed", taskStatusId = s.doneStatus, endDate = Some(monday.plusDays(2))).into,
              Seq.empty,
              Seq(CreateLinkReq("Second", "https://example.org")),
              created.toOption.get.links.map(_.id)
            )
          )
        )
        versions <- s.env.run(s.tasks.listTaskVersions(id, firstPage))
        afterUpdate <- s.env.run(s.tasks.findTaskById(id))
        deleted <- s.env.runEmpty(s.tasks.deleteTask(id))
        gone <- s.env.run(s.tasks.findTaskById(id))
      } yield {
        created.map(_.links.map(_.name)) shouldBe Right(List("Doc"))
        found.map(_.task.taskLabelIds) shouldBe Right(Seq(TaskConfigPreviewSeed.labelId(0, 1)))
        sameUpdate.isRight shouldBe true
        versionsAfterSame.map(_.totalNumberOfItems) shouldBe Right(1L)
        updated.isRight shouldBe true
        versions.map(_.items.map(_.name)) shouldBe Right(List("Renamed", "Brand new"))
        afterUpdate.map(_.links.map(_.name)) shouldBe Right(List("Second"))
        deleted.isRight shouldBe true
        gone.isLeft shouldBe true
      }
    }

    "reject a task on a day that is not a work day" in { _ =>
      val s = new Services
      s.env.run(s.tasks.createTask(s.newTask("Weekend", start = monday.plusDays(5), due = monday.plusDays(6)))).map { result =>
        isBadRequest(result) shouldBe true
      }
    }

    "create tasks in bulk and reject an empty batch" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(s.tasks.createTasks(CreateTasksBulkReq(Seq(s.newTask("One"), s.newTask("Two")))))
        empty <- s.env.run(s.tasks.createTasks(CreateTasksBulkReq(Seq.empty)))
      } yield {
        created.map(_.map(_.task.name)) shouldBe Right(Seq("One", "Two"))
        isBadRequest(empty) shouldBe true
      }
    }

    "edit and delete only old task versions" in { _ =>
      val s = new Services
      val taskId = PreviewSeedIds.id(PreviewSeedIds.Task, 2)
      for {
        versions <- s.env.run(s.tasks.listTaskVersions(taskId, firstPage))
        items = versions.toOption.get.items
        editLatest <- s.env.run(s.tasks.updateTaskVersion(items.head))
        editOld <- s.env.run(s.tasks.updateTaskVersion(items.last.copy(name = "Edited history")))
        deleteOld <- s.env.runEmpty(s.tasks.deleteTaskVersion(items.last.version))
        after <- s.env.run(s.tasks.listTaskVersions(taskId, firstPage))
      } yield {
        items.size shouldBe 4
        isBadRequest(editLatest) shouldBe true
        editOld.map(_.name) shouldBe Right("Edited history")
        deleteOld.isRight shouldBe true
        after.map(_.totalNumberOfItems) shouldBe Right(3L)
      }
    }

    "save a log entry once per day and edit its history" in { _ =>
      val s = new Services
      val taskId = PreviewSeedIds.id(PreviewSeedIds.Task, 13)
      val at = Instant.parse("2026-06-15T11:00:00Z")
      val entry = LogEntryDto(
        UUID.randomUUID(),
        taskId,
        PreviewDemo.AccountId,
        at,
        UUID.randomUUID(),
        "internal",
        "external",
        LogEntryStatus.InProgress,
        at
      )
      for {
        saved <- s.env.run(s.tasks.saveLogEntry(entry))
        again <- s.env.run(s.tasks.saveLogEntry(entry))
        updated <- s.env.run(s.tasks.updateLogEntry(saved.toOption.get.copy(textInternal = "changed", status = LogEntryStatus.Blocked)))
        versions <- s.env.run(s.tasks.listLogEntryVersions(saved.toOption.get.id, firstPage))
        editLatest <- s.env.run(s.tasks.updateLogEntryVersion(versions.toOption.get.items.head))
        editOld <- s.env.run(s.tasks.updateLogEntryVersion(versions.toOption.get.items.last.copy(textInternal = "fixed")))
        deleteLatest <- s.env.runEmpty(s.tasks.deleteLogEntryVersion(versions.toOption.get.items.head.version))
        byTask <- s.env.run(s.tasks.findLogEntriesByTaskId(taskId, firstPage))
      } yield {
        saved.map(_.createdByName) shouldBe Right("Alex Morgan")
        isBadRequest(again) shouldBe true
        updated.map(_.status) shouldBe Right(LogEntryStatus.Blocked)
        versions.map(_.totalNumberOfItems) shouldBe Right(2L)
        isBadRequest(editLatest) shouldBe true
        editOld.map(_.textInternal) shouldBe Right("fixed")
        deleteLatest.isRight shouldBe true
        byTask.map(_.items.headOption.map(_.latestVersion)) shouldBe Right(Some(versions.toOption.get.items.last.version))
      }
    }

    "filter project log entries by the creation time range" in { _ =>
      val s = new Services
      val from = Instant.parse("2026-06-08T00:00:00Z")
      val to = Instant.parse("2026-06-15T00:00:00Z")
      for {
        all <- s.env.run(s.tasks.findAllLogEntriesByProjectId(PreviewDemo.ProjectId))
        week <- s.env.run(s.tasks.findAllLogEntriesByProjectId(PreviewDemo.ProjectId, LogEntryFilter(Some(from), Some(to))))
      } yield {
        week.toOption.get.nonEmpty shouldBe true
        all.toOption.get.size should be > week.toOption.get.size
        week.toOption.get.forall(e => !e.createdAt.isBefore(from) && e.createdAt.isBefore(to)) shouldBe true
        all.toOption.get.map(_.createdAt.toEpochMilli) shouldBe all.toOption.get.map(_.createdAt.toEpochMilli).sorted.reverse
      }
    }

    "manage link versions" in { _ =>
      val s = new Services
      val taskId = PreviewSeedIds.id(PreviewSeedIds.Task, 5)
      for {
        task <- s.env.run(s.tasks.findTaskById(taskId))
        linkId = task.toOption.get.links.head.id
        versions <- s.env.run(s.tasks.listLinkVersions(linkId))
        editLatest <- s.env.run(s.tasks.updateLinkVersion(versions.toOption.get.head))
      } yield {
        task.toOption.get.links.size shouldBe 2
        versions.toOption.get.nonEmpty shouldBe true
        isBadRequest(editLatest) shouldBe true
      }
    }

    "block the deletion of a status that tasks use and detach deleted milestones and labels" in { _ =>
      val s = new Services
      val m = s.env.module
      m.taskStats.source.statusInUse(s.doneStatus) shouldBe true
      m.milestones.deleteMilestone(MilestonesPreviewSeed.BetaId)
      m.taskConfig.deleteLabel(TaskConfigPreviewSeed.labelId(0, 1))
      s.env.run(s.tasks.findByProjectId(PreviewDemo.ProjectId, TaskFilter.empty, firstPage)).map { result =>
        val items = result.toOption.get.items
        items.exists(_.milestone.contains(MilestonesPreviewSeed.BetaId)) shouldBe false
        items.exists(_.taskLabels.exists(_.name == "bug")) shouldBe false
      }
    }

    "feed the milestone activity from the log entries" in { _ =>
      val s = new Services
      val activity = s.env.module.taskStats.source.activityOf(
        MilestonesPreviewSeed.BetaId,
        monday.minusDays(14),
        monday
      )
      activity.nonEmpty shouldBe true
      activity.forall(_.logTexts.nonEmpty) shouldBe true
    }

    "list, version and validate timeboxes" in { _ =>
      val s = new Services
      for {
        seeded <- s.env.run(s.timeboxes.findByProjectId(PreviewDemo.ProjectId, firstPage))
        current = seeded.toOption.get.items.head
        versions <- s.env.run(s.timeboxes.listAllVersions(current.id))
        planned <- s.env.run(s.tasks.findAllByTimebox(PreviewDemo.ProjectId, current.id))
        overlapping <- s.env.run(
          s.timeboxes.createTimebox(CreateTimeboxReq(PreviewDemo.ProjectId, monday, monday.plusDays(5)))
        )
        noComment <- s.env.run(
          s.timeboxes.saveNewVersion(SaveTimeboxVersionReq(current.id, monday, monday.plusDays(13)))
        )
        saved <- s.env.run(
          s.timeboxes.saveNewVersion(
            SaveTimeboxVersionReq(current.id, monday, monday.plusDays(20), Some("Longer"), "Extended", Seq.empty)
          )
        )
        created <- s.env.run(
          s.timeboxes.createTimebox(
            CreateTimeboxReq(PreviewDemo.ProjectId, monday.plusDays(60), monday.plusDays(70), None, Seq(PreviewSeedIds.id(PreviewSeedIds.Task, 13)))
          )
        )
        wrongProject <- s.env.run(
          s.timeboxes.createTimebox(
            CreateTimeboxReq(PreviewDemo.SecondProjectId, monday.plusDays(60), monday.plusDays(70), None, Seq(PreviewSeedIds.id(PreviewSeedIds.Task, 13)))
          )
        )
        deleted <- s.env.runEmpty(s.timeboxes.deleteTimebox(current.id))
        gone <- s.env.run(s.timeboxes.findById(current.id))
      } yield {
        seeded.map(_.totalNumberOfItems) shouldBe Right(2L)
        versions.map(_.map(_.changeComment)) shouldBe Right(Seq("Dropped the CSV export, pulled in the beta invite template", ""))
        planned.toOption.get.size shouldBe 6
        isBadRequest(overlapping) shouldBe true
        isBadRequest(noComment) shouldBe true
        saved.map(_.latestVersion.tasks) shouldBe Right(Nil)
        created.map(_.latestVersion.tasks.size) shouldBe Right(1)
        isBadRequest(wrongProject) shouldBe true
        deleted.isRight shouldBe true
        isBadRequest(gone) shouldBe true
      }
    }

    "aggregate the contributions per member and milestone" in { _ =>
      val s = new Services
      for {
        all <- s.env.run(s.contributions.findByProjectId(PreviewDemo.ProjectId, ContributionsFilter.empty))
        ranged <- s.env.run(
          s.contributions.findByProjectId(
            PreviewDemo.ProjectId,
            ContributionsFilter(Some(monday.minusDays(7)), Some(monday))
          )
        )
        missing <- s.env.run(s.contributions.findByProjectId(UUID.randomUUID(), ContributionsFilter.empty))
      } yield {
        val dto = all.toOption.get
        dto.general.size shouldBe 6
        dto.general.exists(_.dailyEntries.nonEmpty) shouldBe true
        dto.general.map(_.tasksAssigned).sum should be > 0
        dto.milestones.head.name shouldBe "No milestone"
        dto.milestones.map(_.name) should contain("Beta release")
        dto.generalTasks.byStatus.map(_.count).sum shouldBe 22
        val week = ranged.toOption.get
        week.general.flatMap(_.dailyEntries).forall(d => !d.date.isBefore(monday.minusDays(7)) && !d.date.isAfter(monday)) shouldBe true
        week.general.map(_.activeDays).sum should be < dto.general.map(_.activeDays).sum
        missing.isLeft shouldBe true
      }
    }
  }

  private implicit class TaskToUpdate(task: FindTaskResp1) {
    def into: UpdateTaskReq1 =
      UpdateTaskReq1(
        task.id,
        task.projectId,
        task.name,
        task.description,
        task.assignedTo,
        task.createdBy,
        task.taskStatusId,
        task.createdAt,
        task.updatedAt,
        task.priority,
        task.milestone,
        Some(task.startDate),
        Some(task.dueDate),
        task.endDate,
        task.taskLabelIds,
        task.archived
      )
  }
}
