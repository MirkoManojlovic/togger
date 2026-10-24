package com.togger.ui.timebox

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.TimeboxVersionDto
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable
import com.togger.ui.components.TgHtmlTable.createTd
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton}
import com.togger.ui.task.TgDateFormater

import java.util.UUID

/** Read only: timebox versions can neither be edited nor deleted. */
class TimeboxHistoryComponent(
  timeboxService: TimeboxFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  projectId: UUID,
  timeboxId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  private val versionsVar = Var(List.empty[TimeboxVersionDto])

  spinnerService
    .runWithSpinner(
      timeboxService.listAllVersions(timeboxId).map(_.map(success => versionsVar.set(success.body.get.toList)))
    )
    .unsafeRunAndForget()

  private def description(dto: TimeboxVersionDto): String = dto.description.getOrElse("No description")

  private def versionCard(dto: TimeboxVersionDto, isLatest: Boolean) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText(
          s"${tgDateFormater.asAmericanDate(dto.startDate)} - ${tgDateFormater.asAmericanDate(dto.endDate)}" +
            s" (saved ${dto.createdAt}${if (isLatest) ", latest" else ""})",
          TgCard.CardHeaderSize.H4
        ),
        body = TgCard.HtmlCardContent(
          div(
            p(cls("mb-4"), description(dto)),
            TgHtmlTable.tableElement(Seq("Task", "Description", "Start date", "Due date"))(
              dto.tasks.map { task =>
                Seq(
                  createTd(task.name),
                  createTd(task.description),
                  createTd(tgDateFormater.asAmericanDate(task.startDate)),
                  createTd(tgDateFormater.asAmericanDate(task.dueDate))
                )
              }
            )
          )
        )
      )
      .element
      .amend(cls("mb-4"))

  val element =
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Timebox history"),
              iconButton(
                "Timeboxes",
                TgIcons.listIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(ShowTimeboxesPage(projectId))
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              children <-- versionsVar.signal.map(_.zipWithIndex.map { case (dto, index) =>
                versionCard(dto, index == 0)
              })
            )
          )
        )
        .element
    )

}
