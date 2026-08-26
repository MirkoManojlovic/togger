package com.togger.ui.taskloadconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.components.button.TgHtmlButton
import com.togger.dto.taskloadconfig.TaskLoadConfigResp
import com.togger.dto.{TaskLoadResp, TaskLoadStatusResp, TaskLoadTaskResultResp}
import com.togger.routes.Pages.{CreateTaskLoadConfigPage, EditTaskLoadConfigPage, ShowAllTaskConfigsPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskDumpFeService
import com.togger.ui.toast.Toast
import org.scalajs.dom.HTMLTableCellElement

import java.util.UUID

class ShowAllTaskLoadConfigsComponent(
  taskLoadConfigService: TaskLoadConfigFeService,
  taskDumpService: TaskDumpFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val stateFormVar = Var(Seq.empty[TaskLoadConfigResp])
  private val resultsVar = Var(Seq.empty[TaskLoadTaskResultResp])

  spinnerService
    .runWithSpinner(
      taskLoadConfigService
        .findByProjectId(projectId)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))
    )
    .unsafeRunAndForget()

  def createTh(content: String) =
    th(cls("px-6 py-3 font-medium"), content)

  def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-6 py-4"), content)

  private def summaryMessage(dto: TaskLoadResp): String = {
    val counts = dto.results.groupBy(_.status).view.mapValues(_.size).toMap
    s"Loaded ${dto.taskCount} task(s): " +
      s"${counts.getOrElse(TaskLoadStatusResp.Inserted, 0)} inserted, " +
      s"${counts.getOrElse(TaskLoadStatusResp.Merged, 0)} merged, " +
      s"${counts.getOrElse(TaskLoadStatusResp.Unchanged, 0)} unchanged, " +
      s"${counts.getOrElse(TaskLoadStatusResp.Malformed, 0) + counts.getOrElse(TaskLoadStatusResp.Failed, 0)} failed"
  }

  private def handleLoadResponse(dto: TaskLoadResp): Unit = {
    resultsVar.set(dto.results)
    toast.success(summaryMessage(dto))
  }

  private def load(dumpPath: String): Unit =
    spinnerService
      .runWithSpinner(taskDumpService.loadTasks(projectId, dumpPath))
      .toastJustErrors(())(handleLoadResponse)
      .unsafeRunAndForget()

  private def loadAll(): Unit = {
    val paths = stateFormVar.now().map(_.dumpPath).distinct.toList
    val program: IO[List[Either[com.togger.common.TgJsonFailure, TaskLoadResp]]] =
      paths.traverse(path => taskDumpService.loadTasks(projectId, path).map(_.map(_.body.get)))

    spinnerService
      .runWithSpinner(program)
      .map { outcomes =>
        val successes = outcomes.collect { case Right(dto) => dto }
        resultsVar.set(successes.flatMap(_.results))
        outcomes.collect { case Left(failure) => failure }.foreach(toast.error)
        val totalTasks = successes.map(_.taskCount).sum
        val failedCount = outcomes.count(_.isLeft)
        if (failedCount == 0) toast.success(s"Loaded from ${successes.size} path(s), $totalTasks task(s) total")
        else toast.error(s"Loaded from ${successes.size}/${outcomes.size} path(s); $failedCount failed")
      }
      .unsafeRunAndForget()
  }

  private def statusBadgeCls(status: TaskLoadStatusResp): String = status match {
    case TaskLoadStatusResp.Inserted  => "bg-green-100 text-green-800"
    case TaskLoadStatusResp.Merged    => "bg-blue-100 text-blue-800"
    case TaskLoadStatusResp.Unchanged => "bg-gray-100 text-gray-800"
    case TaskLoadStatusResp.Malformed => "bg-red-100 text-red-800"
    case TaskLoadStatusResp.Failed    => "bg-red-100 text-red-800"
  }

  private def statusBadge(status: TaskLoadStatusResp) =
    span(
      cls(s"px-2 py-1 rounded-base text-xs font-medium ${statusBadgeCls(status)}"),
      status.entryName
    )

  private def resultsTable(results: Seq[TaskLoadTaskResultResp]) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Load results", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
            table(
              cls("w-full text-sm text-left rtl:text-right text-body"),
              thead(
                cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                tr(
                  createTh("task id"),
                  createTh("task name"),
                  createTh("status"),
                  createTh("versions"),
                  createTh("links"),
                  createTh("log entries"),
                  createTh("error")
                )
              ),
              tbody(
                results
                  .sortBy(_.taskId)
                  .map { (r: TaskLoadTaskResultResp) =>
                    tr(
                      cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
                      createTd(r.taskId.toString),
                      createTd(r.taskName.getOrElse("-"): String),
                      createTd(statusBadge(r.status)),
                      createTd(r.versionsAdded.toString),
                      createTd(r.linksAdded.toString),
                      createTd(r.logEntriesAdded.toString),
                      createTd(r.error.getOrElse("-"): String)
                    )
                  }
              )
            )
          )
        )
      )
      .element

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Task load configs"),
              div(
                TgHtmlButton
                  .extraSmall("Load all")
                  .amend(cls("mr-2"), onClick.mapToUnit --> (_ => loadAll())),
                TgHtmlButton
                  .extraSmall("Create")
                  .amend(
                    router.navigateTo(CreateTaskLoadConfigPage(projectId))
                  )
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child <-- stateFormVar.signal.map(dtos =>
                div(
                  cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
                  table(
                    cls("w-full text-sm text-left rtl:text-right text-body"),
                    thead(
                      cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                      tr(
                        createTh("id"),
                        createTh("name"),
                        createTh("dump path"),
                        createTh("updated at"),
                        createTh("Action")
                      )
                    ),
                    tbody(
                      dtos
                        .sortBy(_.id)
                        .zipWithIndex
                        .map { case (dto, index) =>
                          val rowCls = if (index % 2 == 0) {
                            "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"
                          } else {
                            "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"
                          }

                          tr(
                            cls(rowCls),
                            createTd(dto.id.toString),
                            createTd(dto.name),
                            createTd(dto.dumpPath),
                            createTd(dto.updatedAt.toString),
                            createTd(
                              div(
                                button(
                                  `type`("button"),
                                  TgIcons.editIcon(className = "w-4 h-4 me-1.5 -ms-0.5"),
                                  "Edit",
                                  cls(
                                    "inline-flex items-center text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none"
                                  ),
                                  cls("mr-2"),
                                  onClick.mapToUnit --> (_ => router.route(EditTaskLoadConfigPage(dto.id)))
                                ),
                                button(
                                  `type`("button"),
                                  TgIcons.trashIcon(className = "w-4 h-4 me-1.5 -ms-0.5"),
                                  "Delete",
                                  cls(
                                    "inline-flex items-center text-white bg-red-600 hover:bg-red-700 box-border border border-transparent focus:ring-4 focus:ring-red-300 shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none"
                                  ),
                                  cls("mr-2"),
                                  onClick.mapToUnit --> (_ =>
                                    spinnerService
                                      .runWithSpinner(taskLoadConfigService.deleteById(dto.id))
                                      .toastBackendMessageCodeBlock {
                                        router.route(ShowAllTaskConfigsPage(projectId))
                                      }
                                      .unsafeRunAndForget()
                                  )
                                ),
                                button(
                                  `type`("button"),
                                  TgIcons.downloadIcon(className = "w-4 h-4 me-1.5 -ms-0.5"),
                                  "Load",
                                  cls(
                                    "inline-flex items-center text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none"
                                  ),
                                  onClick.mapToUnit --> (_ => load(dto.dumpPath))
                                )
                              )
                            )
                          )
                        }
                    )
                  )
                )
              )
            )
          )
        )
        .element,
      div(cls("mt-4"), child.maybe <-- resultsVar.signal.map(results => if (results.isEmpty) None else Some(resultsTable(results))))
    )
  }

}
