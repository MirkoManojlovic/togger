package com.togger.ui.milestonesteptemplate

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.markdown.TgMarkdown
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TgDateFormater
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

class EditMilestoneStepTemplateComponent(
  milestoneStepTemplateService: MilestoneStepTemplateFeService,
  spinnerService: SpinnerService,
  tgMarkdown: TgMarkdown,
  tgDateFormater: TgDateFormater,
  projectId: UUID,
  milestoneStepTemplateId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(MilestoneStepTemplateForm(id = milestoneStepTemplateId, projectId = projectId))

  private val versionsVar = Var(Seq.empty[MilestoneStepTemplateVersionDto])

  private def loadVersions(): IO[Unit] =
    milestoneStepTemplateService.listAllVersions(milestoneStepTemplateId).map {
      case Left(failure) => toast.error(failure)
      case Right(success) =>
        versionsVar.set(success.body.getOrElse(Seq.empty).sortBy(_.createdAt).reverse)
    }

  private def loadForm(): IO[Unit] =
    milestoneStepTemplateService.findMilestoneStepTemplateById(milestoneStepTemplateId).map {
      case Left(failure) => toast.error(failure)
      case Right(success) =>
        val dto = success.body.get
        formState.set(
          MilestoneStepTemplateForm(
            projectId = dto.projectId,
            id = dto.id,
            name = dto.name,
            description = dto.description
          )
        )
    }

  spinnerService.runWithSpinner(loadForm().flatMap(_ => loadVersions())).unsafeRunAndForget()

  lazy val submitter = Observer[MilestoneStepTemplateForm] { form =>
    val dto = UpdateMilestoneStepTemplateReq(id = form.id, name = form.name, description = form.description)
    spinnerService
      .runWithSpinner(milestoneStepTemplateService.updateMilestoneStepTemplate(dto).flatTap {
        case Right(_) => loadVersions()
        case Left(_)  => IO.unit
      })
      .toastMessageJustMessage("Milestone step template saved")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Step template name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Step template description",
    placeholderValue = MilestoneStepTemplateFormSupport.descriptionHint
  )

  /** Read-only: old versions are listed and shown rendered, never edited or deleted. */
  private def historyPanel: HtmlElement =
    div(
      cls("mt-6"),
      h4(cls("mb-2 text-sm font-medium text-heading"), "Version history"),
      children <-- versionsVar.signal.map(_.map { version =>
        div(
          cls("mb-3 p-3 border border-default rounded-base bg-neutral-primary-soft"),
          div(
            cls("mb-2 text-xs text-body-subtle"),
            s"${version.name} - ${tgDateFormater.yearMonthDayTimeFormat(version.createdAt)}"
          ),
          tgMarkdown.toLaminarDiv(version.description)
        )
      })
    )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit milestone step template", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              div(cls("flex justify-end"), TgHtmlButton.updateSmall(EventStream.fromValue(false)))
            ),
            historyPanel
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
