package com.togger.ui.task

import cats.effect.IO
import com.raquo.laminar.api.L._
import com.togger.dto.{FindMilestoneResp, FindProjectResp}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.project.ProjectService

import java.util.UUID

/** The project, statuses, labels and milestones a task list needs for its filters and badges. */
class TaskListLookups(
  projectId: UUID,
  projectService: ProjectService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  milestoneService: MilestoneFeService
) {

  val statusesVar = Var(TaskStatusesState())
  val taskLabelsVar = Var(TaskLabelsState())
  val milestonesVar = Var(Seq.empty[FindMilestoneResp])

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

    projectCall
      .flatMap(_ => taskStatusCall)
      .flatMap(_ => taskLabelCall)
      .flatMap(_ => milestoneCall)
      .void
  }
}
