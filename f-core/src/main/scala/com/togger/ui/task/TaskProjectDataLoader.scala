package com.togger.ui.task

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgJsonFailure
import com.togger.common.TgJsonSuccess
import com.togger.dto._
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.milestone.MilestoneStatus
import com.togger.ui.auth.AccountService
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.organization.OrganizationProjectsLoader
import com.togger.ui.organization.OrganizationServiceFe
import com.togger.ui.milestone.MilestoneStepFeService
import com.togger.ui.milestone.MilestoneStepLabels
import com.togger.ui.project.ProjectService

import java.util.UUID

case class TaskProjectData(
  statuses: Seq[FindTaskStatusResp] = Seq.empty,
  labels: Seq[FindTaskLabelResp] = Seq.empty,
  members: Seq[SearchAccountDto] = Seq.empty,
  milestones: Seq[FindMilestoneResp] = Seq.empty,
  steps: Seq[FindMilestoneStepResp] = Seq.empty
)

/** Loads what a task is created against: the projects the account can see and one project's statuses, labels, members
  * and milestones. A failed call yields an empty list, as the quick create has always done.
  */
class TaskProjectDataLoader(
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  projectService: ProjectService,
  milestoneFeService: MilestoneFeService,
  milestoneStepFeService: MilestoneStepFeService,
  organizationServiceFe: OrganizationServiceFe,
  accountService: AccountService
) {

  /** Milestones still being worked on: a finished or abandoned one is no place for a new task. */
  private val OpenMilestoneStatuses: Set[MilestoneStatus] = Set(MilestoneStatus.InProgress, MilestoneStatus.Paused)

  private val organizationProjectsLoader = new OrganizationProjectsLoader(organizationServiceFe, projectService)

  def loadProjects(): IO[Seq[OrganizationProjectResp]] =
    organizationProjectsLoader
      .loadForAccount(accountService.get.get.id)
      .map(_.toOption.flatMap(_.body).getOrElse(Seq.empty).flatMap(_.projects))

  def loadProjectData(projectId: UUID): IO[TaskProjectData] = {
    // The pickers offer every status, label and milestone, read page by page (bounded by PaginatedLoader).
    val statusesCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskStatusResp]]]] =
      taskStatusFeService.findAllByProjectId(projectId)
    val labelsCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskLabelResp]]]] =
      taskLabelFeService.findAllByProjectId(projectId)
    // The assignee picker offers every member, read page by page (bounded by PaginatedLoader).
    val membersCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[SearchAccountDto]]]] =
      projectService.findAllMembers(projectId)
    val milestonesCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindMilestoneResp]]]] =
      milestoneFeService.findAllByProjectId(projectId)
    val stepsCall: IO[Either[TgJsonFailure, TgJsonSuccess[List[FindMilestoneStepResp]]]] =
      milestoneStepFeService.findByProjectId(projectId)
    (statusesCall, labelsCall, membersCall, milestonesCall, stepsCall).parMapN {
      (statusesResp, labelsResp, membersResp, milestonesResp, stepsResp) =>
        val openMilestones = milestonesResp.toOption
          .flatMap(_.body)
          .getOrElse(Seq.empty)
          .filter(milestone => OpenMilestoneStatuses.contains(milestone.status))
        TaskProjectData(
          statuses = statusesResp.toOption.flatMap(_.body).getOrElse(Seq.empty),
          labels = labelsResp.toOption.flatMap(_.body).getOrElse(Seq.empty),
          members = membersResp.toOption.flatMap(_.body).getOrElse(Seq.empty),
          milestones = openMilestones,
          // a task is put in a step of a milestone that is still being worked on
          steps = MilestoneStepLabels.ordered(stepsResp.toOption.flatMap(_.body).getOrElse(Nil), openMilestones)
        )
    }
  }
}
