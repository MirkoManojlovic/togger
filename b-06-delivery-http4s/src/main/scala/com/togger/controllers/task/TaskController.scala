package com.togger.controllers.task

import cats.data.NonEmptyChain
import cats.effect.IO
import cats.implicits._
import com.togger.account.Account
import com.togger.account.AccountNameLookup
import com.togger.common.DateRangeValidation
import com.togger.common.TgClock
import com.togger.common.TgError
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgResponseTypes.TgResp
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.logentry.LogEntryDtoMapper.toDto
import com.togger.controllers.project.ProjectService
import com.togger.controllers.task.LinkDtoMapper.{toDto => toLinkDto}
import com.togger.controllers.task.TaskDtoMapper.toVersionDto
import com.togger.controllers.task.TaskDtoMapper.{toDto => toTaskDto}
import com.togger.dto._
import com.togger.logentry.LogEntryWithVersions
import com.togger.milestone.MilestoneStepError.StepNotInProject
import com.togger.milestone.MilestoneStepService
import com.togger.task._

import java.time.Instant
import java.util.UUID

class TaskController(
  taskService: TaskService,
  taskVersionService: TaskVersionService,
  taskEndpoints: TaskEndpoints,
  tgClock: TgClock,
  dateRangeValidation: DateRangeValidation,
  projectService: ProjectService,
  taskStatusService: TaskStatusService,
  accountNameLookup: AccountNameLookup,
  milestoneStepService: MilestoneStepService
) {

  import taskEndpoints._

  /** The milestone of a step, for the tasks that are in one. */
  private def milestonesOf(stepIds: Iterable[Option[UUID]]): IO[UUID => Option[UUID]] =
    milestoneStepService.findMilestoneIdsByStepIds(stepIds.flatten).map(milestones => (id: UUID) => milestones.get(id))

  /** Runs `ok` when every step belongs to the project, else answers which steps do not. */
  private def withStepsOfProject[A](projectId: UUID, stepIds: Iterable[Option[UUID]])(
    ok: => IO[TgResp[A]]
  ): IO[TgResp[A]] = {
    val ids = stepIds.flatten.toList.distinct
    milestoneStepService.findProjectIdsByStepIds(ids).flatMap { projects =>
      NonEmptyChain
        .fromSeq(ids.filterNot(id => projects.get(id).contains(projectId)).map(StepNotInProject(_): TgError)) match {
        case Some(errors) => IO.pure(errors.toBadRequestLeft[A])
        case None         => ok
      }
    }
  }

  val findById =
    findByIdEndpoint.serverLogic { _ => taskId =>
      taskService
        .findById(taskId)
        .flatMap { case (taskWithVersions, links, logEntries, taskLabels) =>
          (namesOf(logEntries), milestonesOf(List(taskWithVersions.latestVersion.milestoneStepId))).mapN {
            (names, milestoneOf) =>
              TgJsonSuccess.justBodyRight(
                FindTaskResp(
                  task =
                    toTaskDto(taskWithVersions.task, taskWithVersions.latestVersion, taskLabels.map(_.id), milestoneOf),
                  links = links.map(link => toLinkDto(link.link, link.latestVersion)),
                  logEntries = logEntries.map(entry => toDto(entry.logEntry, entry.latestVersion, names))
                )
              )
          }
        }
    }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { _ => request =>
      val (projectId, filter, pageRequest) = request
      PageRequestValidation.withPage(pageRequest) { page =>
        taskService
          .findPaginatedByProjectId(projectId, filter, page)
          .flatMap(tasksPage =>
            (
              namesOf(tasksPage.items.flatMap(_._3)),
              milestonesOf(tasksPage.items.map(_._1.milestoneStepId))
            ).mapN((names, milestoneOf) =>
              TgJsonSuccess.justBodyRight(tasksPage.mapItem(toFullDto(_, names, milestoneOf)))
            )
          )
      }
    }

  private def namesOf(logEntries: List[LogEntryWithVersions]): IO[Map[UUID, String]] =
    accountNameLookup.namesByIds(logEntries.map(_.logEntry.createdBy).toSet)

  private def toFullDto(
    item: (TaskFull, List[LinkWithVersions], List[LogEntryWithVersions]),
    names: Map[UUID, String],
    milestoneOf: UUID => Option[UUID]
  ): FindTaskRespFull = {
    val (taskFull, links, logEntries) = item
    FindTaskRespFull(
      id = taskFull.id,
      projectId = taskFull.projectId,
      name = taskFull.name,
      description = taskFull.description,
      assignedTo = taskFull.assignedTo.map(fromAccount),
      createdBy = fromAccount(taskFull.createdBy),
      closedBy = taskFull.closedBy.map(fromAccount),
      taskStatus = FindTaskStatusResp(
        id = taskFull.taskStatus.id,
        projectId = taskFull.projectId,
        name = taskFull.taskStatus.name,
        description = taskFull.taskStatus.description,
        emoji = taskFull.taskStatus.emoji
      ),
      priority = taskFull.priority,
      createdAt = taskFull.createdAt,
      updatedAt = taskFull.updatedAt,
      latestVersion = taskFull.latestVersion,
      logEntries = logEntries.map(entry => toDto(entry.logEntry, entry.versions.head, names)),
      links = links.map(link => toLinkDto(link.link, link.latestVersion)),
      milestone = taskFull.milestoneStepId.flatMap(milestoneOf),
      milestoneStepId = taskFull.milestoneStepId,
      startDate = taskFull.startDate,
      dueDate = taskFull.dueDate,
      endDate = taskFull.endDate,
      taskLabels = taskFull.taskLabels.map(l =>
        FindTaskLabelResp(
          id = l.id,
          projectId = l.projectId,
          name = l.name,
          description = l.description,
          emoji = l.emoji
        )
      ),
      archived = taskFull.archived
    )
  }

  def fromAccount(account: Account): SearchAccountDto =
    SearchAccountDto(
      id = account.id,
      firstName = account.firstName,
      lastName = account.lastName,
      email = account.email,
      accountTypeId = account.accountTypeId
    )

  private def newTaskOf(taskDto: CreateTaskReq, createdBy: UUID, createdAt: Instant): (Task, TaskVersion) = {
    val task = Task(id = UUID.randomUUID(), projectId = taskDto.projectId, createdBy = createdBy, createdAt = createdAt)
    val firstVersion = TaskVersion(
      version = UUID.randomUUID(),
      taskId = task.id,
      name = taskDto.name,
      description = taskDto.description,
      assignedTo = taskDto.assignedTo,
      closedBy = None,
      taskStatusId = taskDto.taskStatusId,
      priority = taskDto.priority,
      milestoneStepId = taskDto.milestoneStepId,
      startDate = taskDto.startDate,
      dueDate = taskDto.dueDate,
      endDate = None,
      createdAt = createdAt,
      updatedAt = createdAt
    )
    (task, firstVersion)
  }

  private def newLinksOf(linksDto: Seq[CreateLinkReq], now: Instant): Seq[LinkWithOneVersion] =
    linksDto.map { linkDto =>
      val linkId = UUID.randomUUID()
      LinkWithOneVersion(
        link = Link(id = linkId, taskId = UUID.randomUUID(), createdAt = now),
        version = LinkVersion(
          version = UUID.randomUUID(),
          linkId = linkId,
          name = linkDto.name,
          url = linkDto.url,
          createdAt = now,
          updatedAt = now
        )
      )
    }

  private def savedDtoOf(
    saved: (TaskWithOneVersion, List[LinkWithOneVersion]),
    labelIds: Seq[UUID],
    milestoneOf: UUID => Option[UUID]
  ): FindTaskResp = {
    val (savedTask, savedLinks) = saved
    FindTaskResp(
      task = toTaskDto(savedTask.task, savedTask.version, labelIds, milestoneOf),
      links = savedLinks.map(link => toLinkDto(link.link, link.version)),
      logEntries = Seq.empty
    )
  }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val taskDto = dto.task

    projectService.findById(taskDto.projectId).flatMap { case (project, _, _) =>
      val validation = dateRangeValidation
        .validate(Some(taskDto.startDate), Some(taskDto.dueDate), None, project.workDays)
        .flatMap(_ => LinkValidation.validateAll(dto.links, Seq.empty))
      validation match {
        case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTaskResp])
        case Right(_) =>
          withStepsOfProject[FindTaskResp](taskDto.projectId, List(taskDto.milestoneStepId)) {
            val (task, firstVersion) = newTaskOf(taskDto, auth.body.get.id, taskDto.createdAt)
            val newLinks = newLinksOf(LinkValidation.withoutBlankLinks(dto.links), tgClock.currentInstant)

            for {
              saved <- taskService.createTask(task, firstVersion, newLinks, taskDto.taskLabelIds)
              milestoneOf <- milestonesOf(List(taskDto.milestoneStepId))
            } yield TgJsonSuccess.justBodyRight(savedDtoOf(saved, taskDto.taskLabelIds, milestoneOf))
          }
      }
    }
  }

  /** Creates the tasks in one transaction, in list order. Each gets a creation time a millisecond after the one before,
    * so the order of the list is the order in which the tasks were created.
    */
  val saveBulk = saveBulkEndpoint.serverLogic { auth => dto =>
    BulkTaskValidation.validate(dto) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[Seq[FindTaskResp]])
      case Right(_) =>
        projectService.findById(dto.tasks.head.task.projectId).flatMap { case (project, _, _) =>
          BulkTaskValidation
            .validateDates(dto, project.workDays, dateRangeValidation)
            .flatMap(_ => BulkTaskValidation.validateLinks(dto)) match {
            case Left(errors) => IO.pure(errors.toBadRequestLeft[Seq[FindTaskResp]])
            case Right(_) =>
              withStepsOfProject[Seq[FindTaskResp]](
                dto.tasks.head.task.projectId,
                dto.tasks.map(_.task.milestoneStepId)
              ) {
                val now = tgClock.currentInstant
                val newTasks = dto.tasks.zipWithIndex.map { case (taskAndLinks, index) =>
                  val (task, firstVersion) =
                    newTaskOf(taskAndLinks.task, auth.body.get.id, now.plusMillis(index.toLong))
                  NewTask(
                    task,
                    firstVersion,
                    newLinksOf(LinkValidation.withoutBlankLinks(taskAndLinks.links), now),
                    taskAndLinks.task.taskLabelIds
                  )
                }

                for {
                  saved <- taskService.createTasks(newTasks)
                  milestoneOf <- milestonesOf(dto.tasks.map(_.task.milestoneStepId))
                } yield TgJsonSuccess.justBodyRight(saved.zip(dto.tasks).map { case (savedTask, taskAndLinks) =>
                  savedDtoOf(savedTask, taskAndLinks.task.taskLabelIds, milestoneOf)
                })
              }
          }
        }
    }
  }

  val update = updateEndpoint.serverLogic { _ => dto =>
    val taskDto = dto.task
    val linksForUpdate = dto.linksForUpdate
    val linksToCreate = LinkValidation.withoutBlankLinks(dto.linksToCreate)

    taskService.findProjectId(taskDto.id).flatMap { projectId =>
      projectService.findById(projectId).flatMap { case (project, _, _) =>
        taskVersionService.findLatest(taskDto.id).flatMap { currentVersion =>
          val effectiveStartDate = taskDto.startDate.getOrElse(currentVersion.startDate)
          val effectiveDueDate = taskDto.dueDate.getOrElse(currentVersion.dueDate)
          val effectiveEndDate = taskDto.endDate

          dateRangeValidation
            .validate(Some(effectiveStartDate), Some(effectiveDueDate), effectiveEndDate, project.workDays)
            .flatMap(_ => LinkValidation.validateAll(linksToCreate, linksForUpdate)) match {
            case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTaskResp])
            case Right(_) =>
              withStepsOfProject[FindTaskResp](projectId, List(taskDto.milestoneStepId)) {
                val newUpdatedAt = tgClock.currentInstant

                val newVersionContent = TaskVersion(
                  version = UUID.randomUUID(),
                  taskId = taskDto.id,
                  name = taskDto.name,
                  description = taskDto.description,
                  assignedTo = taskDto.assignedTo,
                  closedBy = None,
                  taskStatusId = taskDto.taskStatusId,
                  priority = taskDto.priority,
                  milestoneStepId = taskDto.milestoneStepId,
                  startDate = effectiveStartDate,
                  dueDate = effectiveDueDate,
                  endDate = effectiveEndDate,
                  createdAt = newUpdatedAt,
                  updatedAt = newUpdatedAt
                )

                val linkVersionUpdates =
                  linksForUpdate.map(dto =>
                    LinkVersion(
                      version = UUID.randomUUID(),
                      linkId = dto.id,
                      name = dto.name,
                      url = dto.url,
                      createdAt = newUpdatedAt,
                      updatedAt = newUpdatedAt
                    )
                  )

                val newLinks =
                  linksToCreate.map { linkDto =>
                    val linkId = UUID.randomUUID()
                    LinkWithOneVersion(
                      link = Link(id = linkId, taskId = taskDto.id, createdAt = newUpdatedAt),
                      version = LinkVersion(
                        version = UUID.randomUUID(),
                        linkId = linkId,
                        name = linkDto.name,
                        url = linkDto.url,
                        createdAt = newUpdatedAt,
                        updatedAt = newUpdatedAt
                      )
                    )
                  }

                taskService
                  .updateTask(
                    taskDto.id,
                    newVersionContent,
                    linkVersionUpdates,
                    newLinks,
                    taskDto.taskLabelIds,
                    taskDto.archived,
                    dto.linksToDelete
                  )
                  .flatMap { case (savedTask, updatedLinks, savedLinks) =>
                    milestonesOf(List(taskDto.milestoneStepId)).map { milestoneOf =>
                      val savedDto = FindTaskResp(
                        task = toTaskDto(savedTask.task, savedTask.version, taskDto.taskLabelIds, milestoneOf),
                        links = (updatedLinks ++ savedLinks).map(link => toLinkDto(link.link, link.version)),
                        logEntries = Seq.empty
                      )
                      TgJsonSuccess.justBodyRight(savedDto)
                    }
                  }
              }
          }
        }
      }
    }
  }

  val delete = deleteEndpoint.serverLogic { _ => taskId =>
    taskService
      .deleteTask(taskId)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Task deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }

  val listVersions = listVersionsEndpoint.serverLogic { _ => request =>
    val (taskId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      for {
        versionsPage <- taskVersionService.listVersions(taskId, page)
        milestoneOf <- milestonesOf(versionsPage.items.map(_.milestoneStepId))
      } yield TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto(_, milestoneOf)))
    }
  }

  val updateVersion = updateVersionEndpoint.serverLogic { _ => dto =>
    taskService.findProjectIdByVersion(dto.version).flatMap { projectId =>
      (projectService.findById(projectId), taskStatusService.findById(dto.taskStatusId)).flatMapN {
        case ((project, _, _), status) =>
          val validation = for {
            _ <- dateRangeValidation.validate(Some(dto.startDate), Some(dto.dueDate), dto.endDate, project.workDays)
            _ <- dateRangeValidation.validateEndDateWhenDone(
              DefaultTaskStatus.isTerminalName(status.name),
              dto.endDate
            )
          } yield ()
          validation match {
            case Left(errors) => IO.pure(errors.toBadRequestLeft[TaskVersionDto])
            case Right(_) =>
              withStepsOfProject[TaskVersionDto](projectId, List(dto.milestoneStepId)) {
                taskVersionService
                  .updateVersionContent(
                    dto.version,
                    dto.name,
                    dto.description,
                    dto.assignedTo,
                    dto.closedBy,
                    dto.taskStatusId,
                    dto.priority,
                    dto.milestoneStepId,
                    dto.startDate,
                    dto.dueDate,
                    dto.endDate
                  )
                  .flatMap {
                    case Right(version) =>
                      milestonesOf(List(version.milestoneStepId))
                        .map(milestoneOf => TgJsonSuccess.justBodyRight(toVersionDto(version, milestoneOf)))
                    case Left(error) => IO.pure(error.toBadRequestLeft[TaskVersionDto])
                  }
              }
          }
      }
    }
  }

  val deleteVersion = deleteVersionEndpoint.serverLogic { _ => version =>
    taskVersionService
      .deleteVersion(version)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Task version deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }

}
