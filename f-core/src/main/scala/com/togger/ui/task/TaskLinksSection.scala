package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.togger.ui.components.{TgBadge, TgBadgeColor}

import java.util.UUID

/** The links of a task form as badges, each with a pencil that opens the popup to change it, and the badge that opens
  * the popup to create one in a row of its own. Links are saved with the submit button of the form.
  */
class TaskLinksSection {

  val linksVar: Var[Seq[TaskLinkComponent]] = Var(Seq.empty)

  /** Saved links that were removed, deleted by the backend with the next update of the task. */
  val linksToDelete: Var[Set[UUID]] = Var(Set.empty)

  /** The popup that is open: the one that creates a link, or the one that changes the link of a badge. */
  private val openLinkEditor: Var[Option[Option[TaskLinkComponent]]] = Var(None)

  def clearDeleted(): Unit = linksToDelete.set(Set.empty)

  private def closeLinkEditor(): Unit = openLinkEditor.set(None)

  private def removeLink(link: TaskLinkComponent): Unit = {
    closeLinkEditor()
    link.formState.now().id.foreach(id => linksToDelete.update(_ + id))
    linksVar.update(_.filterNot(_ eq link))
  }

  private def linkEditorFor(link: Option[TaskLinkComponent]): HtmlElement =
    link.fold(
      new TaskLinkEditor(
        TaskLinkFormData(),
        "Create",
        data => {
          linksVar.update(_ :+ new TaskLinkComponent(Some(data)))
          closeLinkEditor()
        },
        () => closeLinkEditor()
      ).element
    ) { existing =>
      new TaskLinkEditor(
        existing.formState.now(),
        "Update",
        data => {
          existing.formState.update(old => data.copy(id = old.id))
          closeLinkEditor()
        },
        () => closeLinkEditor()
      ).element
    }

  private def linkWithEditor(link: Option[TaskLinkComponent], trigger: HtmlElement): HtmlElement =
    div(
      cls("relative inline-flex"),
      trigger,
      child.maybe <-- openLinkEditor.signal.map(_.contains(link)).distinct.map(Option.when(_)(linkEditorFor(link)))
    )

  private lazy val addNewLink: HtmlElement =
    TgBadge(text = "+ Add link", color = TgBadgeColor.Alternative, bordered = true, extraCls = "cursor-pointer").element
      .amend(
        role("button"),
        tabIndex(0),
        onClick --> (_ => openLinkEditor.set(Some(None))),
        onKeyDown.filter(event => event.key == "Enter" || event.key == " ").preventDefault -->
          (_ => openLinkEditor.set(Some(None)))
      )

  lazy val element: HtmlElement =
    div(
      h4(cls("text-sm font-medium text-heading mb-2"), "Links"),
      child <-- linksVar.signal.map { components =>
        div(
          Option.when(components.nonEmpty)(
            div(
              cls("flex flex-wrap items-center gap-1.5 mb-2"),
              components.map { link =>
                linkWithEditor(
                  Some(link),
                  span(
                    child <-- link.formState.signal.map(data =>
                      TaskLinkBadges.badge(data, () => openLinkEditor.set(Some(Some(link))), () => removeLink(link))
                    )
                  )
                )
              }
            )
          ),
          div(cls("flex items-center mb-2"), linkWithEditor(None, addNewLink))
        )
      }
    )
}
