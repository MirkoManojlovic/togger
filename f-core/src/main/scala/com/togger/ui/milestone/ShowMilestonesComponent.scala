package com.togger.ui.milestone

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.{FindMilestoneResp, FindProjectResp}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTdOverflow}
import com.togger.ui.components.{TableSearchComponent, TgHtmlTable}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

import java.util.UUID

class ShowMilestonesComponent(
  milestoneService: MilestoneFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[FindMilestoneResp])

  val searchTermVar = Var("")
  private val searchBox = TableSearchComponent(
    inputSignal = searchTermVar.signal,
    inputStateUpdater = searchTermVar.updater[String]((_, newSearchTerm) => newSearchTerm),
    placeholderValue = "Search by name, description",
    debounceMs = 0
  )

  private val filteredMilestonesSignal: Signal[Seq[FindMilestoneResp]] =
    stateFormVar.signal.combineWith(searchBox.inputSignal).map { case (milestones, term) =>
      val needle = term.trim.toLowerCase
      if (needle.isEmpty) milestones
      else milestones.filter(dto => dto.name.toLowerCase.contains(needle) || dto.description.toLowerCase.contains(needle))
    }

  private val projectVar: Var[FindProjectResp] = Var(
    FindProjectResp(
      id = UUID.randomUUID(),
      organizationId = UUID.randomUUID(),
      name = "",
      description = "",
      members = Seq.empty
    )
  )

  spinnerService
    .runWithSpinner {

      val projectCall =
        projectService
          .findById(projectId)
          .map(
            _.fold(
              e => println(e),
              success => projectVar.set(success.body.get)
            )
          )

      val milestoneCall = milestoneService
        .findByProjectId(projectId)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))

      projectCall.flatMap(_ => milestoneCall)
    }
    .unsafeRunAndForget()

  val element = {
    div(
      child <-- projectVar.signal.map(projectResp =>
        TgCard
          .headerNoFooter(
            header = TgCard.CardHeader.html(
              div(
                div(
                  cls("flex items-center justify-between"),
                  div(cls("mr-2"), projectResp.name),
                  div(
                    iconButton(
                      "Edit Project",
                      TgIcons.editIcon(className = "w-4 h-4"),
                      rowIconButtonCls,
                      rowButtonMarginCls,
                      onClick.mapToUnit --> (_ => router.route(EditProjectPage(projectResp.id)))
                    )
                  )
                )
              )
            ),
            body = TgCard.HtmlCardContent(div(projectResp.description))
          )
          .element
          .amend(cls("mb-4"))
      ),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Milestones"),
              div(
                cls("flex"),
                iconButton(
                  "Create Milestone",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(CreateMilestonePage(projectId))
                )
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard
            .HtmlCardContent(
              div(
                div(cls("flex justify-between mb-4"), searchBox.element),
                child <-- filteredMilestonesSignal.map { milestones =>
                  TgHtmlTable.tableElement(
                    Seq("Id", "Name", "Description", "Action")
                  )(
                    milestones
                      .sortBy(_.name)
                      .map { dto =>
                        Seq(
                          createTd(dto.id.toString),
                          createTd(dto.name),
                          createTdOverflow(dto.description),
                          createTd(
                            div(
                              iconButton(
                                "Edit",
                                TgIcons.editIcon(className = "w-4 h-4"),
                                rowIconButtonCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => router.route(EditMilestonePage(dto.id)))
                              )
                            )
                          )
                        )
                      }
                  )
                }
              )
            )
        )
        .element
    )
  }

}
