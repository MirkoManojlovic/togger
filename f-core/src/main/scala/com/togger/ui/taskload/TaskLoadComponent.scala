package com.togger.ui.taskload

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.components.button.TgHtmlButton
import com.togger.dto.taskloadconfig.TaskLoadConfigResp
import com.togger.dto.{TaskLoadStatusResp, TaskLoadTaskResultResp}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskDumpFeService
import com.togger.ui.taskloadconfig.TaskLoadConfigFeService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLTableCellElement

import java.util.UUID

case class TaskLoadForm(dumpPath: String = "")

class TaskLoadComponent(
  taskDumpService: TaskDumpFeService,
  taskLoadConfigService: TaskLoadConfigFeService,
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  private val formState = Var(TaskLoadForm())
  private val resultsVar = Var(Seq.empty[TaskLoadTaskResultResp])
  private val configsVar = Var(Seq.empty[TaskLoadConfigResp])

  spinnerService
    .runWithSpinner(
      taskLoadConfigService
        .findByProjectId(projectId)
        .map(response => response.map(success => configsVar.set(success.body.get)))
    )
    .unsafeRunAndForget()

  private val dumpPathInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.dumpPath),
    errors = EventStream.empty,
    labelValue = "Dump path to load from",
    tgInputType = TgInputType.Text
  )

  private lazy val loadSubmitter = Observer[TaskLoadForm] { form =>
    spinnerService
      .runWithSpinner(taskDumpService.loadTasks(projectId, form.dumpPath))
      .toastMessageDto { dto =>
        val counts = dto.results.groupBy(_.status).view.mapValues(_.size).toMap
        s"Loaded ${dto.taskCount} task(s): " +
          s"${counts.getOrElse(TaskLoadStatusResp.Inserted, 0)} inserted, " +
          s"${counts.getOrElse(TaskLoadStatusResp.Merged, 0)} merged, " +
          s"${counts.getOrElse(TaskLoadStatusResp.Unchanged, 0)} unchanged, " +
          s"${counts.getOrElse(TaskLoadStatusResp.Malformed, 0) + counts.getOrElse(TaskLoadStatusResp.Failed, 0)} failed"
      } { dto => IO(resultsVar.set(dto.results)) }
      .unsafeRunAndForget()
  }

  private def createTh(content: String) =
    th(cls("px-6 py-3 font-medium"), content)

  private def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-6 py-4"), content)

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

  private def configsTable(configs: Seq[TaskLoadConfigResp]) =
    div(
      cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
      table(
        cls("w-full text-sm text-left rtl:text-right text-body"),
        thead(
          cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
          tr(
            createTh("name"),
            createTh("dump path")
          )
        ),
        tbody(
          configs
            .sortBy(_.name)
            .map { dto =>
              tr(
                cls(
                  "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default cursor-pointer hover:bg-neutral-tertiary-soft"
                ),
                onClick.mapToUnit --> (_ => formState.update(_.copy(dumpPath = dto.dumpPath))),
                createTd(dto.name),
                createTd(dto.dumpPath)
              )
            }
        )
      )
    )

  private def resultsTable(results: Seq[TaskLoadTaskResultResp]) =
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

  val element = div(
    div(
      justifyCenterFlex,
      cls("mb-4"),
      child.maybe <-- configsVar.signal.map { configs =>
        if (configs.isEmpty) None
        else
          Some(
            TgCard
              .headerNoFooter(
                header = TgCard.CardHeader.plainText("Configured paths", TgCard.CardHeaderSize.H3),
                body = TgCard.HtmlCardContent(configsTable(configs))
              )
              .element
              .amend(heightAndWidthFitContent)
          )
      }
    ),
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Load tasks from disk", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now()) --> loadSubmitter,
                dumpPathInput.htmlElement.amend(cls("mb-4")),
                TgHtmlButton.create(EventStream.fromValue(false))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    ),
    div(cls("mt-4 w-full"), child <-- resultsVar.signal.map(resultsTable))
  )

}
