package com.togger.tasktemplate

import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.task.TaskLabelRepo
import com.togger.tasktemplate.TaskTemplateError._
import com.togger.util.SplunkMacro.sp
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.time.Instant
import java.util.UUID

class TaskTemplateService(
  taskTemplateRepo: TaskTemplateRepo,
  taskTemplateVersionRepo: TaskTemplateVersionRepo,
  taskLabelRepo: TaskLabelRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  type Result[A] = Either[TaskTemplateError, A]

  def create(template: TaskTemplate, firstVersion: TaskTemplateVersion): IO[Result[TaskTemplateFull]] = {
    logger.info(s"Creating ${sp(template)} with ${sp(firstVersion)}")
    (for {
      labelCheck <- validateLabel(template.projectId, template.taskLabelId, None)
      created <- labelCheck.traverse { _ =>
        for {
          savedTemplate <- taskTemplateRepo.insert(template)
          _ <- taskTemplateVersionRepo.insert(firstVersion.copy(taskTemplateId = savedTemplate.id))
          full <- taskTemplateRepo.findFullById(savedTemplate.id)
        } yield full
      }
    } yield created).transact(transactor)
  }

  def update(
    id: UUID,
    name: String,
    body: String,
    taskLabelId: Option[UUID],
    now: Instant
  ): IO[Result[TaskTemplateFull]] = {
    logger.info(s"Updating task template ${sp(id)}")
    (for {
      template <- taskTemplateRepo.findById(id)
      labelCheck <- validateLabel(template.projectId, taskLabelId, id.some)
      updated <- labelCheck.traverse { _ =>
        for {
          _ <- Option
            .when(template.taskLabelId != taskLabelId)(())
            .traverse(_ => taskTemplateRepo.updateLabel(id, taskLabelId))
          currentVersion <- taskTemplateVersionRepo.findLatestByTemplateId(id)
          newVersion = TaskTemplateVersion(UUID.randomUUID(), id, name, body, now)
          _ <-
            if (currentVersion.hasSameContentAs(newVersion)) currentVersion.pure[ConnectionIO]
            else taskTemplateVersionRepo.insert(newVersion)
          full <- taskTemplateRepo.findFullById(id)
        } yield full
      }
    } yield updated).transact(transactor).recover(notFound)
  }

  def delete(id: UUID): IO[Result[Int]] = {
    logger.info(s"Deleting task template ${sp(id)}")
    taskTemplateRepo
      .deleteById(id)
      .transact(transactor)
      .map(deleted => Either.cond(deleted > 0, deleted, TaskTemplateNotFound))
  }

  def findById(id: UUID): IO[Result[TaskTemplateFull]] =
    taskTemplateRepo.findFullById(id).transact(transactor).map(_.asRight[TaskTemplateError]).recover(notFound)

  def findByLabelId(taskLabelId: UUID): IO[Option[TaskTemplateFull]] =
    taskTemplateRepo.findFullByLabelId(taskLabelId).transact(transactor)

  def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[TaskTemplateFull]] =
    taskTemplateRepo.findPaginatedFullByProjectId(projectId, page).transact(transactor)

  def listVersions(
    taskTemplateId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[TaskTemplateVersion]] =
    taskTemplateVersionRepo.findPaginatedByTemplateId(taskTemplateId, page).transact(transactor)

  private def notFound[A]: PartialFunction[Throwable, Result[A]] = { case UnexpectedEnd =>
    TaskTemplateNotFound.asLeft[A]
  }

  private def validateLabel(
    projectId: UUID,
    taskLabelId: Option[UUID],
    templateId: Option[UUID]
  ): ConnectionIO[Result[Unit]] =
    taskLabelId.fold(().asRight[TaskTemplateError].pure[ConnectionIO]) { labelId =>
      taskLabelRepo
        .findById(labelId)
        .map(_.some)
        .recover { case UnexpectedEnd => None }
        .flatMap {
          case Some(label) if label.projectId == projectId =>
            taskTemplateRepo
              .findByLabelId(labelId)
              .map {
                case Some(linked) if !templateId.contains(linked.id) => LabelAlreadyHasTemplate.asLeft[Unit]
                case _                                               => ().asRight[TaskTemplateError]
              }
          case _ => (LabelNotInProject: TaskTemplateError).asLeft[Unit].pure[ConnectionIO]
        }
    }
}
