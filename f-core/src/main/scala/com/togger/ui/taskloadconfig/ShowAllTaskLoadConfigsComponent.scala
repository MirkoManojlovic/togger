package com.togger.ui.taskloadconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.dto.taskloadconfig.TaskLoadConfigResp
import com.togger.dto.{TaskLoadResp, TaskLoadStatusResp, TaskLoadTaskResultResp}
import com.togger.routes.Pages.{CreateTaskLoadConfigPage, EditTaskLoadConfigPage, ShowAllTaskConfigsPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskConfigTableSupport, TaskDumpFeService, TgDateFormater}
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowAllTaskLoadConfigsComponent(
  taskLoadConfigService: TaskLoadConfigFeService,
  taskDumpService: TaskDumpFeService,
  tgDateFormater: TgDateFormater,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  import TaskConfigTableSupport._

  private val stateFormVar = Var(Seq.empty[TaskLoadConfigResp])
  private val resultsVar = Var(Seq.empty[TaskLoadTaskResultResp])

  spinnerService
    .runWithSpinner(
      taskLoadConfigService
        .findByProjectId(projectId)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))
    )
    .unsafeRunAndForget()

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

  private def configRow(dto: TaskLoadConfigResp) =
    tr(
      cls(rowCls),
      createTd(dto.name),
      createTd(dto.dumpPath),
      createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
      createTd(
        div(
          iconButton(
            "Edit",
            TgIcons.editIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => router.route(EditTaskLoadConfigPage(dto.id)))
          ),
          iconButton(
            "Delete",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ =>
              spinnerService
                .runWithSpinner(taskLoadConfigService.deleteById(dto.id))
                .toastBackendMessageCodeBlock {
                  router.route(ShowAllTaskConfigsPage(projectId))
                }
                .unsafeRunAndForget()
            )
          ),
          iconButton(
            "Load",
            TgIcons.uploadIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            lastRowButtonMarginCls,
            onClick.mapToUnit --> (_ => load(dto.dumpPath))
          )
        )
      )
    )

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Task load configs"),
              div(
                iconButton(
                  "Load all",
                  TgIcons.uploadIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  headerButtonMarginCls,
                  onClick.mapToUnit --> (_ => loadAll())
                ),
                iconButton(
                  "Create",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "",
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
                  cls(tableWrapperCls),
                  table(
                    cls(tableCls),
                    thead(
                      cls(theadCls),
                      tr(Seq("name", "dump path", "loaded at", "Action").map(createTh))
                    ),
                    tbody(dtos.sortBy(_.id).map(configRow))
                  )
                )
              )
            )
          )
        )
        .element,
      div(
        cls("mt-4"),
        child.maybe <-- resultsVar.signal.map(results => if (results.isEmpty) None else Some(loadResultsTable(results)))
      )
    )
  }

  private def loadResultsTable(results: Seq[TaskLoadTaskResultResp]) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Load results", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            cls(tableWrapperCls),
            table(
              cls(tableCls),
              thead(
                cls(theadCls),
                tr(Seq("task id", "task name", "status", "versions", "links", "log entries", "error").map(createTh))
              ),
              tbody(
                results
                  .sortBy(_.taskId)
                  .map { (r: TaskLoadTaskResultResp) =>
                    tr(
                      cls(rowCls),
                      createTd(r.taskId.toString),
                      createTd(r.taskName.getOrElse("-"): String),
                      createTd(loadStatusBadge(r.status)),
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

  private def loadStatusBadgeCls(status: TaskLoadStatusResp): String = status match {
    case TaskLoadStatusResp.Inserted  => greenBadgeCls
    case TaskLoadStatusResp.Merged    => blueBadgeCls
    case TaskLoadStatusResp.Unchanged => grayBadgeCls
    case TaskLoadStatusResp.Malformed => redBadgeCls
    case TaskLoadStatusResp.Failed    => redBadgeCls
  }

  private def loadStatusBadge(status: TaskLoadStatusResp) =
    badge(status.entryName, loadStatusBadgeCls(status))

}
