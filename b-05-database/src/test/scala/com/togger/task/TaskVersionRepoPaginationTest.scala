package com.togger.task

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, PaginatedResponse, TgIds, TimeHelpers}
import com.togger.dto.TaskFilter
import com.togger.milestone.{Milestone, MilestoneRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

class TaskVersionRepoPaginationTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgProject
    with TgTaskStatus
    with TimeHelpers {

  private val taskRepo = new TaskRepoImpl
  private val taskVersionRepo = new TaskVersionRepoImpl
  private val taskLabelRepo = new TaskLabelRepoImpl
  private val taskLabelAssignmentRepo = new TaskLabelAssignmentRepoImpl
  private val milestoneRepo = new MilestoneRepoImpl

  /** Eleven tasks, task i being the i-th oldest. Even ones are in `inProgress`, odd ones in `done`; priority Critical
    * for 0, 3, 6, 9 and Medium for the rest; assigned to `first` for 0-3, to `second` for 4-6, to nobody for 7-10; in
    * the milestone for 0-2; labelled with both labels for 0-5, with `feature` only for 6-7, unlabelled for 8-10. Task 0
    * also has an older version (priority Low, in `done`) that no filter may see.
    */
  private case class Seeded(
    projectId: UUID,
    tasks: Vector[Task],
    inProgress: TaskStatus,
    done: TaskStatus,
    first: Account,
    second: Account,
    milestone: Milestone,
    feature: TaskLabel,
    bug: TaskLabel
  ) {
    def idsOf(indexes: Seq[Int]): List[UUID] = indexes.map(tasks(_).id).toList
    def newestFirst(indexes: Seq[Int]): List[UUID] = idsOf(indexes.sorted.reverse)
  }

  private def seed(): ConnectionIO[Seeded] =
    for {
      first <- createAccount
      second <- new AccountRepoImpl().insert(first.copy(id = TgIds.UNASSIGNED_UUID, email = "second@email.com"))
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      inProgress <- createTaskStatus(projectId)
      done <- taskStatusRepo.insert(taskStatus.copy(id = UUID.randomUUID(), projectId = projectId, name = "Done"))
      milestone <- milestoneRepo.insert(Milestone(UUID.randomUUID(), projectId, nowAsInstant))
      feature <- taskLabelRepo.insert(label("feature", projectId))
      bug <- taskLabelRepo.insert(label("bug", projectId))
      base = nowAsInstant
      tasks <- (0 to 10).toList.traverse { index =>
        for {
          saved <- taskRepo.insert(
            Task(UUID.randomUUID(), projectId, first.id, base.plusSeconds(index.toLong))
          )
          _ <- Option.when(index == 0)(oldVersion(saved.id, done.id, first.id, base)).sequence
          _ <- taskVersionRepo.insert(
            TaskVersion(
              version = UUID.randomUUID(),
              taskId = saved.id,
              name = s"Task $index",
              description = s"Description $index",
              assignedTo = if (index <= 3) Some(first.id) else if (index <= 6) Some(second.id) else None,
              taskStatusId = if (index % 2 == 0) inProgress.id else done.id,
              priority = if (index % 3 == 0) TaskPriority.Critical else TaskPriority.Medium,
              milestone = Option.when(index <= 2)(milestone.id),
              startDate = LocalDate.of(2026, 10, 12),
              dueDate = LocalDate.of(2026, 10, 19),
              createdAt = base.plusSeconds(index.toLong),
              updatedAt = base.plusSeconds(index.toLong)
            )
          )
          labels = if (index <= 5) List(feature, bug) else if (index <= 7) List(feature) else List.empty
          _ <- labels.traverse(l => taskLabelAssignmentRepo.insert(TaskLabelAssignment(saved.id, l.id, base)))
        } yield saved
      }
    } yield Seeded(projectId, tasks.toVector, inProgress, done, first, second, milestone, feature, bug)

  private def oldVersion(taskId: UUID, statusId: UUID, accountId: UUID, base: java.time.Instant) =
    taskVersionRepo.insert(
      TaskVersion(
        version = UUID.randomUUID(),
        taskId = taskId,
        name = "Old task 0",
        description = "Old description",
        assignedTo = Some(accountId),
        taskStatusId = statusId,
        priority = TaskPriority.Low,
        startDate = LocalDate.of(2026, 10, 12),
        dueDate = LocalDate.of(2026, 10, 19),
        createdAt = base,
        updatedAt = base
      )
    )

  private def label(name: String, projectId: UUID) =
    TaskLabel(UUID.randomUUID(), projectId, name, s"$name description", nowAsInstant, nowAsInstant)

  private def page(
    seeded: Seeded,
    filter: TaskFilter = TaskFilter.empty,
    pageNumber: Int = 1,
    size: Int = 100
  ): ConnectionIO[PaginatedResponse[TaskFull]] =
    taskVersionRepo.findPaginatedByProjectId(
      seeded.projectId,
      filter,
      DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get
    )

  private def idsOf(response: PaginatedResponse[TaskFull]): List[UUID] = response.items.map(_.id)

  private def filterTest(name: String)(filter: Seeded => TaskFilter, expected: Seq[Int])(implicit
    pos: org.scalactic.source.Position
  ): Unit =
    name in { transactor =>
      (for {
        seeded <- seed()
        found <- page(seeded, filter(seeded))
      } yield {
        idsOf(found) shouldBe seeded.newestFirst(expected)
        found.totalNumberOfItems shouldBe expected.size.toLong
      }).transact(transactor)
    }

  testNameFromClassName should {

    "split 11 tasks over 3 pages of 5, newest update first, without a duplicate" in { transactor =>
      (for {
        seeded <- seed()
        pages <- (1 to 3).toList.traverse(number => page(seeded, pageNumber = number, size = 5))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
        cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
        cp(pages.map(_.page) shouldBe List(1, 2, 3))
        cp(pages.flatMap(idsOf) shouldBe seeded.newestFirst(0 to 10))
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "return an empty page with the totals past the last page" in { transactor =>
      (for {
        seeded <- seed()
        past <- page(seeded, pageNumber = 4, size = 5)
      } yield {
        past.items shouldBe empty
        past.totalNumberOfItems shouldBe 11L
        past.totalNumberOfPages shouldBe 3L
      }).transact(transactor)
    }

    "resolve the latest version, status, accounts and labels of each task of the page" in { transactor =>
      (for {
        seeded <- seed()
        found <- page(seeded)
      } yield {
        val newest = found.items.head
        val oldest = found.items.last
        val cp = new Checkpoint
        cp(newest.name shouldBe "Task 10")
        cp(newest.assignedTo shouldBe None)
        cp(newest.taskStatus shouldBe seeded.inProgress)
        cp(newest.createdBy shouldBe seeded.first)
        cp(oldest.name shouldBe "Task 0")
        cp(oldest.priority shouldBe TaskPriority.Critical)
        cp(oldest.assignedTo shouldBe Some(seeded.first))
        cp(oldest.milestone shouldBe Some(seeded.milestone.id))
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "only see tasks of the project" in { transactor =>
      (for {
        seeded <- seed()
        other <- page(seeded.copy(projectId = UUID.randomUUID()))
      } yield other.items shouldBe empty).transact(transactor)
    }

    filterTest("filter by status")(s => TaskFilter(statusIds = List(s.done.id)), Seq(1, 3, 5, 7, 9))
    filterTest("filter by several statuses (or)")(
      s => TaskFilter(statusIds = List(s.done.id, s.inProgress.id)),
      0 to 10
    )
    filterTest("filter by priority")(_ => TaskFilter(priorities = List(TaskPriority.Critical)), Seq(0, 3, 6, 9))
    filterTest("filter by label")(s => TaskFilter(labelIds = List(s.feature.id)), 0 to 7)
    filterTest("filter by label, a task with several selected labels counts once")(
      s => TaskFilter(labelIds = List(s.feature.id, s.bug.id)),
      0 to 7
    )
    filterTest("filter by the label only some tasks have")(s => TaskFilter(labelIds = List(s.bug.id)), 0 to 5)
    filterTest("filter by assignee")(s => TaskFilter(assigneeIds = List(s.second.id)), 4 to 6)
    filterTest("filter by assignee or unassigned")(
      s => TaskFilter(assigneeIds = List(s.second.id, TgIds.UNASSIGNED_UUID)),
      4 to 10
    )
    filterTest("filter by unassigned only")(_ => TaskFilter(assigneeIds = List(TgIds.UNASSIGNED_UUID)), 7 to 10)
    filterTest("filter by milestone")(s => TaskFilter(milestoneIds = List(s.milestone.id)), 0 to 2)
    filterTest("filter by no milestone")(_ => TaskFilter(milestoneIds = List(TgIds.NO_MILESTONE_UUID)), 3 to 10)
    filterTest("filter by search, ignoring case, in name")(_ => TaskFilter(search = Some("  TASK 10 ")), 10 to 10)
    filterTest("filter by search in description")(_ => TaskFilter(search = Some("description 1")), Seq(1, 10))
    filterTest("filter by search that matches the older version only finds nothing")(
      _ => TaskFilter(search = Some("old")),
      0 until 0
    )
    filterTest("treat percent in the search literally")(_ => TaskFilter(search = Some("%")), 0 until 0)
    filterTest("treat underscore in the search literally")(_ => TaskFilter(search = Some("Task_1")), 0 until 0)
    filterTest("ignore a blank search")(_ => TaskFilter(search = Some("   ")), 0 to 10)
    filterTest("see only the latest version, not the older low priority one")(
      _ => TaskFilter(priorities = List(TaskPriority.Low)),
      0 until 0
    )

    "combine the dimensions with and" in { transactor =>
      (for {
        seeded <- seed()
        found <- page(
          seeded,
          TaskFilter(
            statusIds = List(seeded.inProgress.id),
            labelIds = List(seeded.feature.id),
            assigneeIds = List(seeded.first.id, seeded.second.id),
            priorities = List(TaskPriority.Medium, TaskPriority.Critical)
          )
        )
      } yield idsOf(found) shouldBe seeded.newestFirst(Seq(0, 2, 4, 6))).transact(transactor)
    }

    "paginate a label filter without duplicates over its pages" in { transactor =>
      val filterFor = (s: Seeded) => TaskFilter(labelIds = List(s.feature.id, s.bug.id))
      (for {
        seeded <- seed()
        pages <- (1 to 3).toList.traverse(number => page(seeded, filterFor(seeded), number, 3))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(3, 3, 2))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(8L))
        cp(pages.flatMap(idsOf) shouldBe seeded.newestFirst(0 to 7))
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "paginate an assignee filter over its pages" in { transactor =>
      (for {
        seeded <- seed()
        pages <- (1 to 2).toList.traverse(number =>
          page(seeded, TaskFilter(assigneeIds = List(seeded.first.id, seeded.second.id)), number, 4)
        )
      } yield {
        pages.map(_.items.size) shouldBe List(4, 3)
        pages.flatMap(idsOf) shouldBe seeded.newestFirst(0 to 6)
      }).transact(transactor)
    }
  }
}
