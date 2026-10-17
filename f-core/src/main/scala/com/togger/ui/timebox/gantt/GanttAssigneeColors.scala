package com.togger.ui.timebox.gantt

import java.util.UUID

/** The colour of a task's bar, by who it is assigned to. Members are ordered by id, which the project's members keep
  * as they come and go, so the first member is always blue, the second green and so on, past twenty it starts over.
  */
object GanttAssigneeColors {

  /** Written out in full so Tailwind finds the classes. White text reads on all of them. */
  val Palette: Vector[String] = Vector(
    "bg-blue-600",
    "bg-green-600",
    "bg-red-600",
    "bg-amber-600",
    "bg-purple-600",
    "bg-cyan-600",
    "bg-pink-500",
    "bg-lime-600",
    "bg-orange-600",
    "bg-indigo-700",
    "bg-teal-600",
    "bg-rose-800",
    "bg-yellow-600",
    "bg-violet-400",
    "bg-sky-500",
    "bg-emerald-800",
    "bg-fuchsia-600",
    "bg-slate-600",
    "bg-stone-500",
    "bg-zinc-800"
  )

  /** For a task nobody is assigned to, or whose assignee is no longer a member. */
  val Unassigned = "bg-gray-400"

  /** The place of the assignee among the project's members, which is also the place of their colour in the palette;
    * none for a task nobody is assigned to or whose assignee is no longer a member.
    */
  def rank(assignee: Option[UUID], memberIds: Seq[UUID]): Option[Int] =
    assignee.map(id => memberIds.sortBy(_.toString).indexOf(id)).filter(_ >= 0)

  def classFor(assignee: Option[UUID], memberIds: Seq[UUID]): String =
    rank(assignee, memberIds).fold(Unassigned)(index => Palette(index % Palette.size))
}
