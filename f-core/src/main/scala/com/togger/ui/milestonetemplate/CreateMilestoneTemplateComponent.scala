package com.togger.ui.milestonetemplate

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.milestonesteptemplate.MilestoneStepTemplateFeService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

class CreateMilestoneTemplateComponent(
  milestoneTemplateService: MilestoneTemplateFeService,
  milestoneStepTemplateService: MilestoneStepTemplateFeService,
  spinnerService: SpinnerService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(MilestoneTemplateForm(projectId = projectId))

  private val stepTemplatesVar = Var(Seq.empty[FindMilestoneStepTemplateResp])

  spinnerService
    .runWithSpinner(milestoneStepTemplateService.findAllByProjectId(projectId))
    .map(_.foreach(success => stepTemplatesVar.set(success.body.getOrElse(Seq.empty))))
    .unsafeRunAndForget()

  lazy val submitter = Observer[MilestoneTemplateForm] { form =>
    val dto = CreateMilestoneTemplateReq(
      projectId = form.projectId,
      name = form.name,
      description = form.description,
      stepTemplateIds = form.stepTemplateIds
    )
    spinnerService
      .runWithSpinner(milestoneTemplateService.createMilestoneTemplate(dto))
      .toastMessageJustMessage("Milestone template created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Template name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Template description",
    placeholderValue = MilestoneTemplateFormSupport.descriptionHint
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a milestone template", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              MilestoneTemplateFormSupport.stepsEditor(formState, stepTemplatesVar.signal).amend(cls("mb-4")),
              div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
