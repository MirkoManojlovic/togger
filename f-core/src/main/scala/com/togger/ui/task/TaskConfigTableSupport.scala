package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.dto.{TaskDumpPathStatusResp, TaskDumpSyncStatusResp, TaskDumpTaskStatusResp}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import org.scalajs.dom.HTMLButtonElement

object TaskConfigTableSupport {

  val headerIconButtonCls =
    "inline-flex items-center justify-center w-8 h-8 text-fg-brand bg-neutral-primary border border-brand hover:bg-brand hover:text-white focus:ring-4 focus:ring-brand-subtle rounded-base focus:outline-none"

  val rowIconButtonCls =
    "inline-flex items-center justify-center w-8 h-8 text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs rounded-base focus:outline-none"

  val rowIconButtonDangerCls =
    "inline-flex items-center justify-center w-8 h-8 text-white bg-red-600 hover:bg-red-700 box-border border border-transparent focus:ring-4 focus:ring-red-300 shadow-xs rounded-base focus:outline-none"

  val headerButtonMarginCls = "mr-2"
  val rowButtonMarginCls = "mr-2 mt-1 mb-1"
  val lastRowButtonMarginCls = "mt-1 mb-1"

  val greenBadgeCls = "bg-green-100 text-green-800"
  val yellowBadgeCls = "bg-yellow-100 text-yellow-800"
  val blueBadgeCls = "bg-blue-100 text-blue-800"
  val grayBadgeCls = "bg-gray-100 text-gray-800"
  val redBadgeCls = "bg-red-100 text-red-800"

  val tableWrapperCls =
    "relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"
  val tableCls = "w-full text-sm text-left rtl:text-right text-body"
  val theadCls = "text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"
  val rowCls = "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"

  def iconButton(
    label: String,
    icon: Modifier.Base,
    btnCls: String,
    marginCls: String,
    action: Modifier[ReactiveHtmlElement[HTMLButtonElement]]
  ) =
    button(
      `type`("button"),
      title(label),
      icon,
      cls(btnCls),
      cls(marginCls),
      action
    )

  def badge(text: String, colorCls: String) =
    span(cls(s"px-2 py-1 rounded-base text-xs font-medium $colorCls"), text)

  private def dumpSyncStatusBadgeCls(status: TaskDumpSyncStatusResp): String = status match {
    case TaskDumpSyncStatusResp.InSync          => greenBadgeCls
    case TaskDumpSyncStatusResp.OutOfSync       => yellowBadgeCls
    case TaskDumpSyncStatusResp.MissingFromDump => redBadgeCls
    case TaskDumpSyncStatusResp.MissingFromDb   => redBadgeCls
    case TaskDumpSyncStatusResp.Malformed       => redBadgeCls
  }

  def dumpPathStatusCard(pathStatus: TaskDumpPathStatusResp, header: String) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(div(header), TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            cls(tableWrapperCls),
            table(
              cls(tableCls),
              thead(
                cls(theadCls),
                tr(Seq("task id", "task name", "status", "details").map(createTh))
              ),
              tbody(
                pathStatus.taskStatuses
                  .sortBy(_.taskId)
                  .map { (taskStatus: TaskDumpTaskStatusResp) =>
                    tr(
                      cls(rowCls),
                      createTd(taskStatus.taskId.toString),
                      createTd(taskStatus.taskName.getOrElse("-"): String),
                      createTd(dumpSyncStatusBadge(taskStatus.status)),
                      createTd(taskStatus.differences.mkString(", "))
                    )
                  }
              )
            )
          )
        )
      )
      .element

  private def dumpSyncStatusBadge(status: TaskDumpSyncStatusResp) =
    badge(status.entryName, dumpSyncStatusBadgeCls(status))

}
