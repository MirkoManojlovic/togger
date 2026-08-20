package com.togger.ui.taskdumpconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.SearchAccountDto
import com.togger.dto.taskdumpconfig.CreateTaskDumpConfigReq
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID
import scala.collection.immutable.ListMap

case class CreateTaskDumpConfigForm(
  dumpPath: String = ""
)

case class ProjectMembersState(members: Seq[SearchAccountDto] = Seq.empty) {
  lazy val asSelectTuples: Seq[(String, String)] = members.map(m => m.id.toString -> m.fullName)
}

class CreateTaskDumpConfigComponent(
  taskDumpConfigService: TaskDumpConfigFeService,
  projectService: ProjectService,
  spinnerService: SpinnerService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateTaskDumpConfigForm())
  val membersVar = Var(ProjectMembersState())

  spinnerService
    .runWithSpinner(
      projectService
        .findAllMembers(projectId)
        .map(response => response.map(success => membersVar.set(ProjectMembersState(success.body.get))))
    )
    .unsafeRunAndForget()

  lazy val createSubmitter = Observer[CreateTaskDumpConfigForm] { state =>
    val request = CreateTaskDumpConfigReq(
      projectId = projectId,
      dumpPath = state.dumpPath
    )
    spinnerService
      .runWithSpinner(taskDumpConfigService.create(request))
      .toastMessageJustMessage("Task dump config created successfully")
      .unsafeRunAndForget()
  }

  private val dumpPathInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.dumpPath),
    errors = EventStream.empty,
    labelValue = "Dump path (must be an abs path)",
    tgInputType = TgInputType.Text
  )

  def renderElement(selectValues: Seq[(String, String)]) = {
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Create a task dump config", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now()) --> createSubmitter,
                dumpPathInput.htmlElement.amend(cls("mb-4")),
                TgHtmlButton.create(EventStream.fromValue(false))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )
  }

  val element = div(
    child <-- membersVar.signal.map(state => renderElement(state.asSelectTuples))
  )

}
