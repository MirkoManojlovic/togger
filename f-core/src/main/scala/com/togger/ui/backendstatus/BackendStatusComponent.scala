package com.togger.ui.backendstatus

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.ui.auth.TgCard
import com.togger.ui.modal.ModalFactory
import com.togger.ui.modal.PlainModalVariant
import com.togger.ui.modal.PlainTextModalMessage

/** Public page: it must work without a session and while the backend is down. */
class BackendStatusComponent(store: BackendStatusStore, modalFactory: ModalFactory) {
  import BackendStatusComponent._

  private def actionButton(text: String)(onPress: => Unit) =
    button(
      typ("button"),
      cls(
        "text-white bg-brand hover:bg-brand-strong focus:ring-4 focus:ring-brand-medium font-medium rounded-base " +
          "text-sm px-3 py-2 focus:outline-none disabled:opacity-50"
      ),
      disabled <-- store.busy,
      text,
      onClick --> { _ => onPress }
    )

  private def confirmed(title: String, message: String, confirm: String)(action: => Unit): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(message),
      title = title,
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = confirm
    ) { close =>
      action
      close()
    }

  private def target(app: Option[StatusApp]): String = app.fold("the backend and the MCP server")(_.label)

  private def stopWithConfirmation(app: Option[StatusApp]): Unit =
    confirmed("Stop", s"Stop ${target(app)}? It stays stopped until you start it again.", "Yes, stop")(store.stop(app))

  private def restartWithConfirmation(app: Option[StatusApp]): Unit =
    confirmed("Restart", s"Restart ${target(app)}?", "Yes, restart")(store.restart(app))

  private def buttons(app: Option[StatusApp], view: Option[AppStatusView]) =
    div(
      cls("flex flex-wrap gap-2 mt-3"),
      actionButton("Start")(store.start(app)),
      actionButton("Stop")(stopWithConfirmation(app)),
      actionButton("Restart")(restartWithConfirmation(app)),
      view.filter(_.versionMismatch).toList.map(_ => span(cls("text-sm text-danger self-center"), "Restart to update"))
    )

  private def row(label: String, value: String): HtmlElement =
    div(cls("flex justify-between gap-6 text-sm"), span(cls("text-body"), label), span(cls("text-heading"), value))

  private def appCard(app: StatusApp) =
    div(
      cls("p-4 mb-4 rounded-base border border-default"),
      h4(cls("mb-2 font-semibold text-heading"), app.label),
      child <-- store.views.map(_.find(_.app == app)).map {
        case None => emptyNode
        case Some(view) =>
          div(
            dataAttr("app") := app.id,
            row("Status", BackendStatusModel.statusText(view)),
            row("Version", BackendStatusModel.versionText(view)),
            view.pid.toList.map(pid => row("PID", pid.toString)),
            view.expectedVersion.toList.map(expected => row("Tauri app version", expected)),
            Option
              .when(view.versionMismatch)(p(cls("mt-2 text-sm text-danger"), role("alert"), mismatchText(view)))
              .toList,
            Option.when(view.restarts > 0)(row("Restarted by the app", view.restarts.toString)).toList,
            view.lastError.toList.map(error => p(cls("mt-2 text-sm text-danger"), error)),
            if (store.isControllable) buttons(Some(app), Some(view)) else emptyNode
          )
      }
    )

  private val webNote =
    p(cls("mt-2 text-sm text-body"), "Start, stop and restart are only available in the Togger desktop app.")

  def element: ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      justifyCenterFlex,
      onMountBind { _ =>
        store.refresh()
        EventStream.periodic(RefreshEveryMillis).mapToUnit --> Observer[Unit](_ => store.refresh())
      },
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Backend status", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              StatusApp.all.map(appCard),
              child.maybe <-- store.busy.map(busy =>
                Option.when(busy)(
                  p(cls("mb-3 text-sm text-body"), role("status"), "Working, this can take up to a minute...")
                )
              ),
              child.maybe <-- store.error.map(_.map(error => p(cls("mb-3 text-sm text-danger"), role("alert"), error))),
              if (store.isControllable) div(cls("mt-2"), buttons(None, None)) else webNote
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )
}

object BackendStatusComponent {
  val RefreshEveryMillis: Int = 5000

  def mismatchText(view: AppStatusView): String =
    s"${view.app.label} runs ${BackendStatusModel.versionText(view)}, but the Tauri app is " +
      s"${view.expectedVersion.getOrElse("-")}."
}
