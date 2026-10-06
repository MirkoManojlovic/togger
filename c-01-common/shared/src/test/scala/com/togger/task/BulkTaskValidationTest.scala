package com.togger.task

import com.togger.common.{DateRangeValidation, NonEmptyChainHelpers, WorkDayCalendar}
import com.togger.dto.{CreateTaskAndLinksReq, CreateTaskReq, CreateTasksBulkReq}
import com.togger.task.BulkTaskValidation.{InvalidTask, NoTasks, TasksInDifferentProjects, TooManyTasks}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class BulkTaskValidationTest extends AnyWordSpec with Matchers with EitherValues with NonEmptyChainHelpers {

  private val projectId = UUID.randomUUID()

  private val monday = LocalDate.of(2026, 10, 12)
  private val dateRangeValidation = new DateRangeValidation

  private def task(
    project: UUID = projectId,
    name: String = "A",
    start: LocalDate = monday,
    due: LocalDate = monday.plusDays(4)
  ): CreateTaskAndLinksReq =
    CreateTaskAndLinksReq(
      CreateTaskReq(
        projectId = project,
        name = name,
        description = "",
        assignedTo = None,
        createdBy = UUID.randomUUID(),
        taskStatusId = UUID.randomUUID(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        startDate = start,
        dueDate = due
      ),
      links = Seq.empty
    )

  "BulkTaskValidation.validate" should {
    "accept tasks of one project" in {
      BulkTaskValidation.validate(CreateTasksBulkReq(Seq(task(), task()))) shouldBe Right(())
    }

    "reject no tasks" in {
      BulkTaskValidation.validate(CreateTasksBulkReq(Seq.empty)).left.value.asList shouldBe List(NoTasks)
    }

    "accept exactly the maximum number of tasks" in {
      val tasks = Seq.fill(BulkTaskValidation.MaxTasks)(task())
      BulkTaskValidation.validate(CreateTasksBulkReq(tasks)) shouldBe Right(())
    }

    "reject more than the maximum number of tasks" in {
      val tasks = Seq.fill(BulkTaskValidation.MaxTasks + 1)(task())
      BulkTaskValidation.validate(CreateTasksBulkReq(tasks)).left.value.asList shouldBe
        List(TooManyTasks(BulkTaskValidation.MaxTasks + 1, BulkTaskValidation.MaxTasks))
    }

    "reject tasks of different projects" in {
      BulkTaskValidation
        .validate(CreateTasksBulkReq(Seq(task(), task(UUID.randomUUID()))))
        .left
        .value
        .asList shouldBe List(TasksInDifferentProjects)
    }
  }

  "BulkTaskValidation.validateDates" should {
    def validate(tasks: CreateTaskAndLinksReq*) =
      BulkTaskValidation.validateDates(
        CreateTasksBulkReq(tasks),
        WorkDayCalendar.DefaultWorkDays,
        dateRangeValidation
      )

    "accept tasks whose dates are work days in order" in {
      validate(task(), task()) shouldBe Right(())
    }

    "say which task has a start date on a weekend" in {
      val errors = validate(task(), task(name = "Weekend", start = monday.plusDays(5), due = monday.plusDays(7)))
      errors.left.value.asList.collect { case InvalidTask(position, name, _) => (position, name) } shouldBe
        List((2, "Weekend"))
    }

    "report the problems of every task" in {
      val errors = validate(
        task(name = "First", start = monday.plusDays(5), due = monday.plusDays(7)),
        task(name = "Second", start = monday.plusDays(2), due = monday)
      )
      errors.left.value.asList.collect { case InvalidTask(position, _, _) => position }.distinct shouldBe List(1, 2)
    }
  }
}
