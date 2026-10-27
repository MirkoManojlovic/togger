package com.togger.ui.project

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.{FindMilestoneResp, FindTaskRespFull}
import com.togger.routes.Pages.EditTaskPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.{TaskProgressComponent, TgDateFormater}
import com.togger.ui.task.bulk.BulkTaskCard

import java.time.DayOfWeek

/** A task as the active tasks of the project home list it: its name linked to its page with its labels and progress, a
  * button to preview it, and its badges under them.
  *
  * @param milestones
  *   to name the milestone of the task in its badges; empty where the milestone is already known
  * @param showAssignee
  *   the assignee of the task in its badges, left out where the tasks are of one member
  */
class TaskListItem(
  router: TgRouter[FrontendPage],
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  milestones: Seq[FindMilestoneResp],
  workDays: Set[DayOfWeek],
  showAssignee: Boolean,
  onPreview: FindTaskRespFull => Unit
) {

  def apply(task: FindTaskRespFull): HtmlElement =
    li(
      cls("mb-2 p-2 border border-default rounded-base bg-neutral-primary-soft"),
      div(
        cls("flex items-start gap-2 mb-1"),
        div(
          cls("flex flex-wrap items-center gap-2 min-w-0 flex-1"),
          a(
            cls("text-fg-brand hover:underline cursor-pointer"),
            task.name,
            router.navigateTo(EditTaskPage(task.projectId, task.id))
          ),
          BulkTaskCard.readOnlyRow(ProjectHomeStats.labelBadges(task)),
          TaskProgressComponent
            .fromDescription(Val(task.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
            .element
            .amend(cls("self-center leading-none"))
        ),
        button(
          `type`("button"),
          title("Preview task"),
          cls(
            "inline-flex items-center justify-center w-5 h-5 shrink-0 text-body bg-transparent border border-default hover:bg-neutral-tertiary hover:text-fg-brand focus:ring-2 focus:ring-neutral-tertiary rounded-base focus:outline-none cursor-pointer"
          ),
          TgIcons.eyeIcon("w-3.5 h-3.5"),
          onClick.mapToUnit --> (_ => onPreview(task))
        )
      ),
      BulkTaskCard.readOnlyRow(
        ProjectHomeStats.taskBadges(
          task,
          milestones,
          showAssignee,
          tgClock.currentDate(),
          workDays,
          tgDateFormater.asAmericanDate
        )
      )
    )
}
