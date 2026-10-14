package com.togger.ui.timebox

import com.raquo.laminar.api.L._
import com.togger.ui.components.{TgProgressColor, TgProgressComponent, TgProgressSize}

/** The capacity bars of the timebox: one for the whole team and one for each person. Rendering only: what a bar holds
  * is worked out by [[TimeboxCapacity]].
  */
class TimeboxCapacityComponent(capacity: Signal[Option[TimeboxCapacity]]) {

  private def color(level: CapacityLevel): TgProgressColor = level match {
    case CapacityLevel.Green  => TgProgressColor.Success
    case CapacityLevel.Yellow => TgProgressColor.Warning
    case CapacityLevel.Red    => TgProgressColor.Danger
  }

  private def bar(line: CapacityLine, size: TgProgressSize): HtmlElement =
    div(
      div(
        cls("mb-1 flex justify-between text-sm text-body"),
        span(cls("font-medium text-heading"), line.name),
        span(s"${line.usedDays} / ${line.capacityDays} days (${line.percent}%)")
      ),
      TgProgressComponent(
        value = line.usedDays,
        max = line.capacityDays,
        color = color(line.level),
        size = size
      ).element
    )

  private def content(current: TimeboxCapacity): HtmlElement =
    div(
      cls("flex flex-col gap-3"),
      bar(current.total, TgProgressSize.Large),
      current.people.map(bar(_, TgProgressSize.Default)),
      div(cls("text-sm text-body"), s"Without assignee: ${current.unassignedDays} days (counted in the total only)"),
      Option.when(current.isOverCapacity)(
        div(cls("text-sm font-medium text-fg-danger-strong"), "Over capacity: unstage tasks to be able to save.")
      )
    )

  val element: HtmlElement =
    div(
      h6(cls("mb-2 text-heading"), "Capacity"),
      child <-- capacity.map {
        case Some(current) => content(current)
        case None          => div(cls("text-sm text-body"), "Set the start and end dates to see the capacity.")
      }
    )
}
