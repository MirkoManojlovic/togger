package com.togger.ui.task

import cats.effect.IO
import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneResp
import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.FindProjectResp
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.milestone.MilestoneStepFeService
import com.togger.ui.project.ProjectService

import java.util.UUID

/** The project, statuses, labels and milestones a task list needs for its filters and badges. */
class TaskListLookups(
  projectId: UUID,
  projectService: ProjectService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  milestoneService: MilestoneFeService,
  milestoneStepService: Option[MilestoneStepFeService] = None
) {

  val statusesVar = Var(TaskStatusesState())
  val taskLabelsVar = Var(TaskLabelsState())
  val milestonesVar = Var(Seq.empty[FindMilestoneResp])
  val stepsVar = Var(Seq.empty[FindMilestoneStepResp])

  val projectVar: Var[FindProjectResp] = Var(
    FindProjectResp(
      id = UUID.randomUUID(),
      organizationId = UUID.randomUUID(),
      name = "",
      description = "",
      icon = "",
      workDays = Set.empty,
      members = Seq.empty
    )
  )

  def load(): IO[Unit] = {
    val projectCall =
      projectService
        .findById(projectId)
        .map(
          _.fold(
            e => println(e),
            success => projectVar.set(success.body.get)
          )
        )

    // The status filter offers every status, read page by page (bounded by PaginatedLoader).
    val taskStatusCall = taskStatusFeService
      .findAllByProjectId(projectId)
      .map(response => response.map(success => statusesVar.set(TaskStatusesState(success.body.get))))

    // The label filter offers every label, read page by page (bounded by PaginatedLoader).
    val taskLabelCall = taskLabelFeService
      .findAllByProjectId(projectId)
      .map(response => response.map(success => taskLabelsVar.set(TaskLabelsState(success.body.get))))

    // The milestone filter offers every milestone, read page by page (bounded by PaginatedLoader).
    val milestoneCall = milestoneService
      .findAllByProjectId(projectId)
      .map(response => response.map(success => milestonesVar.set(success.body.get)))

    // The step badge of a task shows the name of its step.
    val stepCall = milestoneStepService.fold(IO.unit)(
      _.findByProjectId(projectId).map(response => response.foreach(success => stepsVar.set(success.body.get)))
    )

    projectCall
      .flatMap(_ => taskStatusCall)
      .flatMap(_ => taskLabelCall)
      .flatMap(_ => milestoneCall)
      .flatMap(_ => stepCall)
      .void
  }
}
