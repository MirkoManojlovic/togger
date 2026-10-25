package com.togger.ui.project

import com.raquo.laminar.api.L._
import com.togger.dto.FindProjectResp
import com.togger.markdown.TgMarkdown
import com.togger.routes.Pages.{EditProjectPage, ProjectHomePage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls}

/** The card every page uses to show a project: name (linking to the project home) and labels in the header, the
  * markdown description and the members in the body.
  */
class ProjectInfoCard(router: TgRouter[FrontendPage], tgMarkdown: TgMarkdown) {

  def element(project: FindProjectResp): HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(header(project), TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(body(project))
      )
      .element
      .amend(cls("mb-4"))

  private def header(project: FindProjectResp): HtmlElement =
    div(
      cls("flex items-center justify-between"),
      div(
        cls("mr-2 flex flex-wrap items-center gap-2"),
        a(
          cls("font-medium text-fg-brand hover:underline"),
          router.navigateTo(ProjectHomePage(project.id)),
          s"${project.icon} ${project.name}".trim
        ),
        Option.when(project.projectLabels.nonEmpty)(
          badges(project.projectLabels.map(_.name).sorted, TgBadgeColor.Indigo)
        )
      ),
      iconButton(
        "Edit Project",
        TgIcons.editIcon(className = "w-4 h-4"),
        rowIconButtonCls,
        rowButtonMarginCls,
        onClick.mapToUnit --> (_ => router.route(EditProjectPage(project.id)))
      )
    )

  private def body(project: FindProjectResp): HtmlElement =
    div(
      tgMarkdown.toLaminarDiv(project.description),
      Option.when(project.members.nonEmpty)(
        div(
          cls("mt-4"),
          div(cls("font-semibold text-heading mb-1"), "Members"),
          badges(project.members.map(_.fullName).sorted, TgBadgeColor.Gray)
        )
      )
    )

  private def badges(texts: Seq[String], color: TgBadgeColor): HtmlElement =
    div(cls("flex flex-wrap gap-1"), texts.map(text => TgBadge(text = text, color = color, bordered = true).element))
}
