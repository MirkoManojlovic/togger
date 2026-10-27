package com.togger.task

import cats.data.EitherNec
import cats.implicits._
import com.togger.common.{DateRangeValidation, TgError}
import com.togger.dto.CreateTasksBulkReq

import java.time.DayOfWeek

object BulkTaskValidation {

  val MaxTasks = 200

  sealed trait BulkTaskError extends TgError

  case object NoTasks extends BulkTaskError {
    override def message: String = "There are no tasks to create"
  }

  case class TooManyTasks(count: Int, max: Int) extends BulkTaskError {
    override def message: String = s"At most $max tasks can be created at once, but there were $count"
  }

  case object TasksInDifferentProjects extends BulkTaskError {
    override def message: String = "All tasks created together must belong to the same project"
  }

  /** @param position
    *   1-based place of the task in the batch, since a batch is written as a list
    */
  case class InvalidTask(position: Int, taskName: String, reason: String) extends BulkTaskError {
    override def message: String = s"Task $position (\"$taskName\"): $reason"
  }

  /** What can be said about a batch without looking anything up: it is not empty, not too big and for one project. */
  def validate(req: CreateTasksBulkReq): EitherNec[TgError, Unit] =
    if (req.tasks.isEmpty) NoTasks.leftNec
    else if (req.tasks.size > MaxTasks) TooManyTasks(req.tasks.size, MaxTasks).leftNec
    else if (req.tasks.map(_.task.projectId).distinct.size > 1) TasksInDifferentProjects.leftNec
    else ().rightNec

  /** The start and due dates of every task against the project's work days, with all the problems of all the tasks
    * reported at once and each saying which task it is about.
    */
  def validateDates(
    req: CreateTasksBulkReq,
    workDays: Set[DayOfWeek],
    dateRangeValidation: DateRangeValidation
  ): EitherNec[TgError, Unit] =
    req.tasks.toList.zipWithIndex.traverse_ { case (newTask, index) =>
      dateRangeValidation
        .validate(Some(newTask.task.startDate), Some(newTask.task.dueDate), None, workDays)
        .leftMap(_.map[TgError](error => InvalidTask(index + 1, newTask.task.name, error.message)))
        .toValidated
    }.toEither

  /** Every link of every task, with each problem saying which task it is about. */
  def validateLinks(req: CreateTasksBulkReq): EitherNec[TgError, Unit] =
    req.tasks.toList.zipWithIndex.traverse_ { case (newTask, index) =>
      LinkValidation
        .validateAll(newTask.links, Seq.empty)
        .leftMap(_.map[TgError](error => InvalidTask(index + 1, newTask.task.name, error.message)))
        .toValidated
    }.toEither
}
