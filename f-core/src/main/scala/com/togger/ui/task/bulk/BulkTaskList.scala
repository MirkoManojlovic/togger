package com.togger.ui.task.bulk

import java.util.UUID

object BulkTaskList {

  /** Lines the text now has, matched to the tasks already there so a task keeps its key and attributes while the text
    * changes: first by name, then (so renaming a line while typing keeps its attributes) by position. Whatever is left
    * is a new task with the defaults. The result follows the order of the text.
    */
  def reconcile(
    existing: Seq[BulkTask],
    names: Seq[String],
    defaults: BulkTaskDefaults,
    newKey: () => UUID
  ): Seq[BulkTask] = {
    type Pool = Vector[(BulkTask, Int)]
    val indexedNames = names.zipWithIndex

    val (byName, poolAfterNames) =
      indexedNames.foldLeft((Map.empty[Int, BulkTask], existing.zipWithIndex.toVector: Pool)) {
        case ((matched, pool), (name, index)) =>
          pool.indexWhere { case (task, _) => task.name == name } match {
            case -1    => (matched, pool)
            case found => (matched + (index -> pool(found)._1), pool.patch(found, Nil, 1))
          }
      }

    val (all, _) = indexedNames.foldLeft((byName, poolAfterNames)) {
      case ((matched, pool), (_, index)) if matched.contains(index) => (matched, pool)
      case ((matched, pool), (name, index)) =>
        pool.indexWhere { case (_, position) => position == index } match {
          case -1    => (matched + (index -> BulkTask.withDefaults(newKey(), name, defaults)), pool)
          case found => (matched + (index -> pool(found)._1.copy(name = name)), pool.patch(found, Nil, 1))
        }
    }
    names.indices.map(all)
  }

  /** Every task back to the defaults, e.g. after the project changed and its statuses and work days with it. */
  def reapplyDefaults(tasks: Seq[BulkTask], defaults: BulkTaskDefaults): Seq[BulkTask] =
    tasks.map(task => BulkTask.withDefaults(task.key, task.name, defaults))

  /** Drops what a task refers to that the project no longer has, so a restored draft can't send ids the backend would
    * refuse: a missing status becomes the default one, a missing label, milestone or assignee is simply removed.
    */
  def sanitize(tasks: Seq[BulkTask], lookups: BulkLookups, defaults: BulkTaskDefaults): Seq[BulkTask] =
    tasks.map { task =>
      task.copy(
        statusId = task.statusId.filter(id => lookups.statuses.exists(_.id == id)).orElse(defaults.statusId),
        labelIds = task.labelIds.filter(id => lookups.labels.exists(_.id == id)),
        milestoneId = task.milestoneId.filter(id => lookups.milestones.exists(_.id == id)),
        assigneeId = task.assigneeId.filter(id => lookups.members.exists(_.id == id))
      )
    }

  /** Moves a task to a drop position, counted in the gaps before each task: 0 is above the first, `size` below the
    * last.
    */
  def moveToInsertion(tasks: Seq[BulkTask], from: Int, insertion: Int): Seq[BulkTask] = {
    if (!tasks.indices.contains(from) || insertion < 0 || insertion > tasks.size) return tasks
    val target = if (insertion > from) insertion - 1 else insertion
    if (target == from) return tasks
    val without = tasks.patch(from, Nil, 1)
    without.patch(target, Seq(tasks(from)), 0)
  }

  /** One step up (-1) or down (+1); at either end nothing moves. */
  def moveBy(tasks: Seq[BulkTask], index: Int, delta: Int): Seq[BulkTask] =
    if (delta > 0) moveToInsertion(tasks, index, index + delta + 1)
    else moveToInsertion(tasks, index, index + delta)
}
