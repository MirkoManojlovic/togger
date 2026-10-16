package com.togger.ui.timebox.gantt

import com.togger.dto.FindTaskRespFull

import java.util.UUID

object GanttRowOrder {

  /** The tasks in the order the rows were arranged in. Tasks staged since then go last, and the ones no longer staged
    * disappear from the order.
    */
  def ordered(tasks: Seq[FindTaskRespFull], order: Seq[UUID]): Seq[FindTaskRespFull] = {
    val byId = tasks.map(task => task.id -> task).toMap
    order.flatMap(byId.get) ++ tasks.filterNot(task => order.contains(task.id))
  }
}
