package com.togger.ui.timebox.gantt

import com.togger.dto.FindTaskRespFull

import java.util.UUID

object GanttRowOrder {

  /** The tasks in the order the rows were arranged in. Tasks that are not in that order yet, all of them at first, come
    * after, grouped by assignee in the order of the members (the order of their colours) with the unassigned last, and
    * in the order they were staged within a group. The ones no longer staged disappear from the arranged order.
    */
  def ordered(tasks: Seq[FindTaskRespFull], order: Seq[UUID], memberIds: Seq[UUID] = Seq.empty): Seq[FindTaskRespFull] = {
    val byId = tasks.map(task => task.id -> task).toMap
    val rest = tasks
      .filterNot(task => order.contains(task.id))
      .sortBy(task => GanttAssigneeColors.rank(task.assignedTo.map(_.id), memberIds).getOrElse(Int.MaxValue))
    order.flatMap(byId.get) ++ rest
  }
}
