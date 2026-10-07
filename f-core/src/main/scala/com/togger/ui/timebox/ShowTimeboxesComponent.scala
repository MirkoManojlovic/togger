package com.togger.ui.timebox

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.FindTimeboxResp
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable
import com.togger.ui.components.TgHtmlTable.{createTd, createTdOverflow}
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{
  headerIconButtonCls,
  iconButton,
  rowButtonMarginCls,
  rowIconButtonCls,
  rowIconButtonDangerCls
}
import com.togger.ui.task.TgDateFormater
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowTimeboxesComponent(
  timeboxService: TimeboxFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  projectId: UUID,
  val toast: Toast,
  modalFactory: ModalFactory
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val timeboxesVar = Var(Seq.empty[FindTimeboxResp])
  private val projectNameVar = Var("")

  spinnerService
    .runWithSpinner {
      val projectCall = projectService
        .findById(projectId)
        .map(_.fold(e => println(e), success => projectNameVar.set(success.body.get.name)))
      val timeboxCall = timeboxService
        .findByProjectId(projectId)
        .map(_.map(success => timeboxesVar.set(success.body.get)))
      projectCall.flatMap(_ => timeboxCall)
    }
    .unsafeRunAndForget()

  private def deleteTimebox(dto: FindTimeboxResp): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(
        s"Delete the timebox ${tgDateFormater.asEuropeanDate(dto.latestVersion.startDate)} - " +
          s"${tgDateFormater.asEuropeanDate(dto.latestVersion.endDate)} and all of its versions?"
      ),
      title = "Delete timebox",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(timeboxService.deleteTimebox(dto.id))
        .toastBackendMessageCodeBlock {
          timeboxesVar.update(_.filterNot(_.id == dto.id))
        }
        .unsafeRunAndForget()
      close()
    }

  val element =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(
            cls("flex justify-between"),
            div(child.text <-- projectNameVar.signal.map(name => if (name.isEmpty) "Timeboxes" else s"$name timeboxes")),
            div(
              cls("flex"),
              iconButton(
                "Create timebox",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(CreateTimeboxPage(projectId))
              )
            )
          ),
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(
          div(child <-- timeboxesVar.signal.map { timeboxes =>
            TgHtmlTable.tableElement(Seq("Start date", "End date", "Description", "Tasks", "Last saved", "Action"))(
              timeboxes
                .sortBy(_.latestVersion.startDate.toString)
                .reverse
                .map { dto =>
                  val version = dto.latestVersion
                  Seq(
                    createTd(tgDateFormater.asEuropeanDate(version.startDate)),
                    createTd(tgDateFormater.asEuropeanDate(version.endDate)),
                    createTdOverflow(version.description.getOrElse("-")),
                    createTd(version.tasks.size.toString),
                    createTd(version.createdAt.toString),
                    createTd(
                      div(
                        iconButton(
                          "Save new version",
                          TgIcons.editIcon(className = "w-4 h-4"),
                          rowIconButtonCls,
                          rowButtonMarginCls,
                          onClick.mapToUnit --> (_ => router.route(EditTimeboxPage(projectId, dto.id)))
                        ),
                        iconButton(
                          "History",
                          TgIcons.statusIcon(className = "w-4 h-4"),
                          rowIconButtonCls,
                          rowButtonMarginCls,
                          onClick.mapToUnit --> (_ => router.route(TimeboxHistoryPage(projectId, dto.id)))
                        ),
                        iconButton(
                          "Delete",
                          TgIcons.trashIcon(className = "w-4 h-4"),
                          rowIconButtonDangerCls,
                          rowButtonMarginCls,
                          onClick.mapToUnit --> (_ => deleteTimebox(dto))
                        )
                      )
                    )
                  )
                }
            )
          })
        )
      )
      .element

}
