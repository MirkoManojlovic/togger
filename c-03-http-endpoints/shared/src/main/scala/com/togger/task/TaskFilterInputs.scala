package com.togger.task

import com.togger.dto.TaskFilter
import sttp.tapir._

import java.util.UUID

/** The task list filters as repeatable query params: `statusId`, `priority`, `labelId`, `assigneeId`, `milestoneId`
  * a single `search` and a single `timeboxId`.
  */
object TaskFilterInputs {

  private type FilterParams = (List[UUID], List[Int], List[UUID], List[UUID], List[UUID], Option[String], Option[UUID])

  val taskFilterQuery: EndpointInput[TaskFilter] =
    query[List[UUID]]("statusId")
      .and(query[List[Int]]("priority"))
      .and(query[List[UUID]]("labelId"))
      .and(query[List[UUID]]("assigneeId"))
      .and(query[List[UUID]]("milestoneId"))
      .and(query[Option[String]]("search"))
      .and(query[Option[UUID]]("timeboxId"))
      .mapDecode(toFilter)(toParams)

  private def toFilter(params: FilterParams): DecodeResult[TaskFilter] = {
    val (statusIds, priorityValues, labelIds, assigneeIds, milestoneIds, search, timeboxId) = params
    val priorities = priorityValues.map(TaskPriority.withValueOpt)
    if (priorities.exists(_.isEmpty))
      DecodeResult.Error(priorityValues.mkString(","), new IllegalArgumentException("Unknown task priority"))
    else
      DecodeResult.Value(
        TaskFilter(
          statusIds,
          priorities.flatten,
          labelIds,
          assigneeIds,
          milestoneIds,
          search.filter(_.trim.nonEmpty),
          timeboxId
        )
      )
  }

  private def toParams(filter: TaskFilter): FilterParams =
    (
      filter.statusIds,
      filter.priorities.map(_.value),
      filter.labelIds,
      filter.assigneeIds,
      filter.milestoneIds,
      filter.search,
      filter.timeboxId
    )
}
