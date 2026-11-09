package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.MilestoneStepVersionDto
import com.togger.dto.UpdateMilestoneStepReq
import com.togger.markdown.TgMarkdown
import com.togger.milestone.MilestoneStepStatus
import com.togger.routes.FrontendPage
import com.togger.routes.Pages.ShowMilestoneStepsPage
import com.togger.routes.TgRouter
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.auth.TgInputType
import com.togger.ui.components.ChartColorBadgeColor
import com.togger.ui.components.QuickOption
import com.togger.ui.components.QuickOptionsPopup
import com.togger.ui.components.TgBadge
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.modal.ModalStyles
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TgDateFormater
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

case class EditMilestoneStepForm(
  id: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  status: String = MilestoneStepStatus.Planned.value.toString,
  startDate: String = "",
  dueDate: String = "",
  endDate: String = "",
  changeComment: String = ""
) {
  def stepStatus: MilestoneStepStatus = MilestoneStepStatus.withValue(status.toInt)
}

/** Edits a step; every save is a new version and needs a change comment. Done and Abandoned are chosen in a dialog. */
class EditMilestoneStepComponent(
  milestoneStepService: MilestoneStepFeService,
  milestoneService: MilestoneFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgMarkdown: TgMarkdown,
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  projectId: UUID,
  milestoneStepId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditMilestoneStepForm(id = milestoneStepId))

  private val milestoneIdVar = Var(Option.empty[UUID])
  private val milestoneRangeVar = Var("" -> "")
  private val workDaysVar = Var(Set.empty[DayOfWeek])
  private val versionsVar = Var(Seq.empty[MilestoneStepVersionDto])

  private def applyStep(step: FindMilestoneStepResp): Unit =
    formState.set(
      EditMilestoneStepForm(
        id = step.id,
        name = step.name,
        description = step.description,
        status = step.status.value.toString,
        startDate = step.startDate.toString,
        dueDate = step.dueDate.toString,
        endDate = step.endDate.map(_.toString).getOrElse("")
      )
    )

  private def loadVersions(): IO[Unit] =
    milestoneStepService.listAllVersions(milestoneStepId).map {
      case Left(failure)  => toast.error(failure)
      case Right(success) => versionsVar.set(success.body.getOrElse(Seq.empty).sortBy(_.createdAt).reverse)
    }

  private def load(): IO[Unit] =
    milestoneStepService.findMilestoneStepById(milestoneStepId).flatMap {
      case Left(failure) => IO(toast.error(failure))
      case Right(success) =>
        val step = success.body.get
        applyStep(step)
        milestoneIdVar.set(Some(step.milestoneId))
        for {
          milestoneResult <- milestoneService.findMilestoneById(step.milestoneId)
          projectResult <- projectService.findById(projectId)
          _ <- loadVersions()
        } yield {
          milestoneResult
            .foreach(m => milestoneRangeVar.set(m.body.get.startDate.toString -> m.body.get.dueDate.toString))
          projectResult.foreach(p => workDaysVar.set(p.body.get.workDays.map(DayOfWeek.of)))
        }
    }

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  /** Why the form cannot be saved; nothing when it can. */
  private def errorsOf(form: EditMilestoneStepForm, range: (String, String), workDays: Set[DayOfWeek]): List[String] = {
    val draft = MilestoneStepDraft(form.name, form.description, form.startDate, form.dueDate)
    val endDate = MilestoneStepsDraft.parseDate(form.endDate)
    val endErrors =
      if (form.stepStatus.isClosing)
        MilestoneStepClose.endDateErrors(
          endDate,
          MilestoneStepsDraft.parseDate(form.startDate).getOrElse(LocalDate.MIN),
          workDays
        )
      else Nil
    MilestoneStepsDraft.errors(draft, range._1, range._2, workDays) ++ endErrors ++
      MilestoneStepClose.commentErrors(form.changeComment)
  }

  private val errors: Signal[List[String]] =
    formState.signal.combineWith(milestoneRangeVar.signal, workDaysVar.signal).map { case (form, range, workDays) =>
      errorsOf(form, range, workDays)
    }

  private def save(form: EditMilestoneStepForm, successMessage: String, afterSave: => Unit): Unit =
    (MilestoneStepsDraft.parseDate(form.startDate), MilestoneStepsDraft.parseDate(form.dueDate)) match {
      case (Some(start), Some(due)) =>
        val dto = UpdateMilestoneStepReq(
          id = form.id,
          name = form.name.trim,
          description = form.description,
          status = form.stepStatus,
          startDate = start,
          dueDate = due,
          endDate = MilestoneStepsDraft.parseDate(form.endDate),
          changeComment = form.changeComment.trim
        )
        spinnerService
          .runWithSpinner(milestoneStepService.updateMilestoneStep(dto))
          .flatMap(
            _.fold(
              failure => IO(toast.error(failure)),
              success =>
                IO {
                  toast.success(successMessage)
                  applyStep(success.body.get)
                } >> loadVersions() >> IO(afterSave)
            )
          )
          .unsafeRunAndForget()
      case _ => ()
    }

  private val submitter = Observer[EditMilestoneStepForm](save(_, "Milestone step saved as a new version", ()))

  // ---- close

  private val closeDialogOpen = Var(false)
  private val closeStatus = Var[MilestoneStepStatus](MilestoneStepStatus.Done)
  private val closeEndDate = Var(tgClock.currentDate().toString)
  private val closeComment = Var("")

  private def closeErrors: Signal[List[String]] =
    closeEndDate.signal.combineWith(closeComment.signal, formState.signal, workDaysVar.signal).map {
      case (endDate, comment, form, workDays) =>
        MilestoneStepClose.endDateErrors(
          MilestoneStepsDraft.parseDate(endDate),
          MilestoneStepsDraft.parseDate(form.startDate).getOrElse(LocalDate.MIN),
          workDays
        ) ++ MilestoneStepClose.commentErrors(comment)
    }

  private def openCloseDialog(status: MilestoneStepStatus): Unit = {
    closeStatus.set(status)
    closeComment.set("")
    closeDialogOpen.set(true)
  }

  private def closeStep(): Unit = {
    val closed = formState
      .now()
      .copy(
        status = closeStatus.now().value.toString,
        endDate = closeEndDate.now(),
        changeComment = closeComment.now()
      )
    closeDialogOpen.set(false)
    save(
      closed,
      "Milestone step closed",
      milestoneIdVar.now().foreach(milestoneId => router.route(ShowMilestoneStepsPage(projectId, milestoneId)))
    )
  }

  private val closeDialog: HtmlElement =
    div(
      cls(ModalStyles.wrapperCls),
      ModalStyles.closeOnBackdropOrEscape(Observer(_ => closeDialogOpen.set(false))),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-md"),
        h3(cls(ModalStyles.titleCls), cls("mb-4"), "Close step"),
        div(
          cls("mb-4 flex gap-2"),
          MilestoneStepClose.closingStatuses.map { status =>
            button(
              typ("button"),
              cls(ModalStyles.secondaryButtonCls),
              cls("ring-2 ring-blue-500") <-- closeStatus.signal.map(_ == status),
              status.toStringWithEmoji,
              onClick.mapToUnit --> (_ => closeStatus.set(status))
            )
          }
        ),
        label(cls("block mb-2.5 text-sm font-medium text-heading"), "End date"),
        input(
          typ("date"),
          cls(tgInputStyle),
          controlled(value <-- closeEndDate.signal, onInput.mapToValue --> closeEndDate)
        ),
        label(cls("block mt-4 mb-2.5 text-sm font-medium text-heading"), "Change comment"),
        textArea(
          cls(
            "w-full p-2.5 text-sm text-heading bg-neutral-secondary-medium border border-default-medium rounded-base"
          ),
          rows(3),
          placeholder("Why is the step closed?"),
          controlled(value <-- closeComment.signal, onInput.mapToValue --> closeComment)
        ),
        children <-- closeErrors.map(_.map(error => p(cls("mt-2 text-sm text-red-600 dark:text-red-500"), error))),
        div(
          cls(ModalStyles.footerCls),
          cls("justify-end"),
          button(
            typ("button"),
            cls(ModalStyles.secondaryButtonCls),
            "Cancel",
            onClick.mapToUnit --> (_ => closeDialogOpen.set(false))
          ),
          button(
            typ("button"),
            cls(ModalStyles.primaryButtonCls),
            "Close step",
            disabled <-- closeErrors.map(_.nonEmpty),
            onClick.mapToUnit --> (_ => closeStep())
          )
        )
      )
    )

  // ---- form

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Step name",
    tgInputType = TgInputType.Text
  )

  private val startDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.startDate),
    errors = EventStream.empty,
    labelValue = "Start date",
    tgInputType = TgInputType.Date
  )

  private val dueDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.dueDate),
    errors = EventStream.empty,
    labelValue = "Due date",
    tgInputType = TgInputType.Date
  )

  private val endDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.endDate),
    errors = EventStream.empty,
    labelValue = "End date",
    tgInputType = TgInputType.Date,
    isRequired = false
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Step description",
    minHeightRem = 10,
    minWidthRem = 0
  )

  private val changeCommentEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.changeComment),
    labelValue = "Change comment",
    placeholderValue = "What changed and why?",
    minHeightRem = 8,
    minWidthRem = 0
  )

  private val statusMenuOpen = Var(false)

  private val statusBadge: HtmlElement =
    div(
      cls("relative"),
      child <-- formState.signal.map(_.status).distinct.map { value =>
        val status = MilestoneStepStatus.withValue(value.toInt)
        TgBadge(text = status.toStringWithEmoji, color = ChartColorBadgeColor.of(status), bordered = true).element
          .amend(
            cls("cursor-pointer"),
            cls("ring-2 ring-blue-500") <-- statusMenuOpen.signal,
            // stopPropagation keeps this click from reaching the menu's own outside-click listener
            onClick.stopPropagation.mapToUnit --> (_ => statusMenuOpen.update(!_))
          )
      },
      child.maybe <-- statusMenuOpen.signal.map(Option.when(_)(statusMenu))
    )

  private def statusMenu: HtmlElement =
    QuickOptionsPopup(
      options = MilestoneStepStatus.values.map(status =>
        QuickOption(
          status.value.toString,
          status.toStringWithEmoji,
          status.description,
          formState.now().status == status.value.toString,
          Some(ChartColorBadgeColor.of(status))
        )
      ),
      onChoose = option => {
        val status = MilestoneStepStatus.withValue(option.id.toInt)
        if (status.isClosing) openCloseDialog(status)
        else formState.update(_.copy(status = option.id))
        statusMenuOpen.set(false)
      },
      widthCls = "w-64"
    ).element.amend(
      onMountBind { ctx =>
        val rootNode = ctx.thisNode.ref
        documentEvents(_.onClick)
          .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
          .mapToUnit --> (_ => statusMenuOpen.set(false))
      },
      onMountBind(_ =>
        documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> (_ => statusMenuOpen.set(false))
      )
    )

  /** Read-only: every version with its state and the comment that explains it. */
  private def historyPanel: HtmlElement =
    div(
      cls("mt-6"),
      h4(cls("mb-2 text-sm font-medium text-heading"), "Version history"),
      children <-- versionsVar.signal.map(_.map { version =>
        div(
          cls("mb-3 p-3 border border-default rounded-base bg-neutral-primary-soft shadow-md"),
          div(
            cls("mb-2 text-xs text-body-subtle"),
            s"${version.name} - ${version.status.toStringWithEmoji} - position ${version.position + 1} - " +
              s"${tgDateFormater.asAmericanDate(version.startDate)} to ${tgDateFormater.asAmericanDate(version.dueDate)}" +
              version.endDate.fold("")(end => s", ended ${tgDateFormater.asAmericanDate(end)}") +
              s" - ${tgDateFormater.yearMonthDayTimeFormat(version.createdAt)}"
          ),
          Option.when(version.changeComment.nonEmpty)(tgMarkdown.toLaminarDiv(version.changeComment))
        )
      })
    )

  val element: HtmlElement =
    div(
      display.flex,
      flexDirection.column,
      alignItems.center,
      child.maybe <-- closeDialogOpen.signal.map(Option.when(_)(closeDialog)),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Edit milestone step", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                nameInput.htmlElement.amend(cls("mb-4 max-w-[60ch]")),
                div(cls("mb-4 flex flex-wrap items-center gap-4"), statusBadge),
                div(
                  cls("mb-4 flex flex-wrap gap-4"),
                  startDateInput.htmlElement,
                  dueDateInput.htmlElement,
                  endDateInput.htmlElement
                ),
                descriptionEditor.htmlElement.amend(cls("mb-4")),
                changeCommentEditor.htmlElement.amend(cls("mb-4")),
                children <-- errors.map(_.map(error => p(cls("mb-2 text-sm text-red-600 dark:text-red-500"), error))),
                div(
                  cls("flex justify-end gap-2"),
                  button(
                    typ("button"),
                    cls(TgHtmlButton.tailwindExtraSmallButton),
                    "Back to steps",
                    onClick.mapToUnit --> (_ =>
                      milestoneIdVar.now().foreach(id => router.route(ShowMilestoneStepsPage(projectId, id)))
                    )
                  ),
                  TgHtmlButton.updateSmall(errors.map(_.nonEmpty).changes)
                )
              ),
              historyPanel
            )
          )
        )
        .element
        .amend(cls("w-full mb-4"))
    )
}
