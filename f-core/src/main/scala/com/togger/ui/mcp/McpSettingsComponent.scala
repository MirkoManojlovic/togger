package com.togger.ui.mcp

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.ui.auth.TgCard
import com.togger.ui.organization.TgIcons
import org.scalajs.dom

import scala.scalajs.js

class McpSettingsComponent {

  private val addCommand = "claude mcp add --transport http togger http://localhost:8090/mcp"

  private val CopiedMessageMillis = 2000.0

  private val jsonConfig =
    """{
      |  "mcpServers": {
      |    "togger": {
      |      "type": "http",
      |      "url": "http://localhost:8090/mcp"
      |    }
      |  }
      |}""".stripMargin

  private def copyButton(text: String, what: String) = {
    val copiedVar = Var(false)
    div(
      cls("relative"),
      button(
        typ("button"),
        cls("text-body hover:text-fg-brand focus:outline-none focus:ring-2 focus:ring-brand rounded-base"),
        title(s"Copy the $what to the clipboard"),
        aria.label(s"Copy the $what to the clipboard"),
        TgIcons.copyIcon("w-6 h-6"),
        onClick --> { _ =>
          dom.window.navigator.clipboard.writeText(text)
          copiedVar.set(true)
          js.timers.setTimeout(CopiedMessageMillis)(copiedVar.set(false))
        }
      ),
      child <-- copiedVar.signal.map { copied =>
        if (copied)
          span(
            cls("absolute right-0 top-full mt-1 text-xs font-normal text-body whitespace-nowrap"),
            role("status"),
            "Copied to clipboard"
          )
        else emptyNode
      }
    )
  }

  private def field(labelText: String, text: String, what: String, hint: String) =
    div(
      label(cls("block mb-2 text-sm font-medium text-heading"), labelText),
      div(
        cls("flex items-start gap-3"),
        pre(
          cls("flex-1 p-2 rounded-base bg-neutral-secondary-medium text-sm text-heading whitespace-pre-wrap break-all"),
          code(text)
        ),
        copyButton(text, what)
      ),
      p(cls("mt-2 text-sm text-body"), hint)
    )

  private val commandField =
    field(
      "Add the Togger MCP server to Claude Code",
      addCommand,
      "command",
      "Run this command in a terminal while the Togger MCP server is running. It gives Claude Code read-only " +
        "access to your organizations, projects and tasks."
    )

  private val jsonField =
    field(
      "Other MCP clients (JSON configuration)",
      jsonConfig,
      "JSON configuration",
      "Add this to the MCP configuration file of your client. The exact file and the name of the \"type\" value " +
        "can differ between clients."
    )

  def element: ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("MCP", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(div(commandField.amend(cls("mb-6")), jsonField))
        )
        .element
        .amend(heightAndWidthFitContent)
    )

}
