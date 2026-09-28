package com.togger.ui.components.wysiwyg

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement
import com.togger.markdown.{TgMarkdown, TgMarkdownStyles}
import com.togger.ui.auth.TgHtmlTypes.{HtmlButton, HtmlDiv}
import com.togger.ui.components.wysiwyg.WYSIWYGDom._
import com.togger.ui.organization.TgIcons
import com.togger.util.FrontendMacros.SelectAndUpdate
import io.github.edadma.markdown.MarkdownConfig
import org.scalajs.dom
import org.scalajs.dom.SVGSVGElement

/** A contenteditable markdown editor: the author edits the rendered elements, the component reports markdown.
  *
  * Shaped like [[com.togger.ui.auth.TgTextArea]] — a `Signal[String]` in, an `Observer[String]` out — so it drops
  * into the same form wiring and can replace one in place.
  *
  * The DOM is the model while the author types, and [[MarkdownDomSerializer]] reads it back. That means the surface
  * is repainted only when a value arrives that the editor did not itself produce; repainting on its own output
  * would destroy the caret on every keystroke. `lastKnown` is what makes that distinction.
  *
  * Raw HTML is always escaped. Besides being the right default for text other people will read, it is what keeps
  * the render output free of `onMountCallback` — a callback never fires on an element Laminar did not mount, and
  * these elements are mounted by hand into the editable surface.
  *
  * @param minHeightRem
  *   the surface's resting height; it grows with the content
  * @param debounceTimeMillis
  *   how long typing settles before the document is serialized and reported
  */
case class WYSIWYGTextEditor(
  inputSignal: Signal[String],
  inputStateUpdater: Observer[String],
  labelValue: String,
  placeholderValue: String = "",
  minHeightRem: Int = 12,
  showToolbar: Boolean = true,
  config: MarkdownConfig = MarkdownConfig.default,
  styles: TgMarkdownStyles = TgMarkdownStyles.default,
  debounceTimeMillis: Int = 300
) {

  // Laminar ships no composition event props; IME input needs them, so they are declared here.
  private val onCompositionStart = eventProp[dom.CompositionEvent]("compositionstart")
  private val onCompositionEnd = eventProp[dom.CompositionEvent]("compositionend")

  private val markdown = new TgMarkdown(config, allowRawHtml = false, styles)
  private val commands = new WYSIWYGCommands(styles)
  private val inputRules = new WYSIWYGInputRules(commands)
  private val keys = new WYSIWYGKeys(commands)
  private val history = new WYSIWYGHistory()

  private val editedBus = new EventBus[Unit]
  private val activeMarksVar = Var(Set.empty[String])
  private val isEmptyVar = Var(true)

  /** The last markdown the editor and its caller agree on. An incoming value equal to this is the echo of our own
    * emit and must not repaint.
    */
  private var lastKnown: Option[String] = None

  /** Composition events bracket IME input (pinyin, kana). Serializing or running input rules mid-composition eats
    * characters, so both wait for it to finish.
    */
  private var composing = false

  private var surfaceRef: Option[dom.html.Element] = None

  /** The signal can reach the editor before the surface is mounted, so the newest value is held until there is
    * something to paint it into.
    */
  private var latestValue: String = ""

  val htmlElement: HtmlDiv =
    div(
      label(cls(WYSIWYGTextEditorStyles.label), labelValue),
      div(
        cls(WYSIWYGTextEditorStyles.wrapper),
        if (showToolbar) toolbarElement else emptyNode,
        surfaceElement
      ),
      inputSignal --> Observer[String](onExternalValue),
      editedBus.events.debounce(debounceTimeMillis) --> Observer[Unit](_ => if (!composing) emit())
    )

  private def surfaceElement: HtmlDiv =
    div(
      cls(WYSIWYGTextEditorStyles.surfaceContainer),
      div(
        cls(WYSIWYGTextEditorStyles.surface),
        minHeight.rem(minHeightRem),
        contentEditable(true),
        role("textbox"),
        aria.multiLine(true),
        aria.label(labelValue),
        onMountCallback { context =>
          surfaceRef = Some(context.thisNode.ref)
          repaint(latestValue)
          history.reset(latestValue)
        },
        onInput.mapToUnit --> Observer[Unit](_ => handleInput()),
        onKeyDown --> Observer[dom.KeyboardEvent](handleKeyDown),
        onPaste --> Observer[dom.ClipboardEvent](handlePaste),
        onBlur.mapToUnit --> Observer[Unit](_ => if (!composing) emit()),
        onFocus.mapToUnit --> Observer[Unit](_ => refreshActiveMarks()),
        onCompositionStart.mapToUnit --> Observer[Unit](_ => composing = true),
        onCompositionEnd.mapToUnit --> Observer[Unit] { _ =>
          composing = false
          handleInput()
        }
      ),
      child.maybe <-- isEmptyVar.signal.map(placeholderElement)
    )

  private def placeholderElement(isEmpty: Boolean): Option[HtmlDiv] =
    Option.when(isEmpty && placeholderValue.nonEmpty)(
      div(cls(WYSIWYGTextEditorStyles.placeholder), placeholderValue)
    )

  private def toolbarElement: HtmlDiv =
    div(
      cls(WYSIWYGTextEditorStyles.toolbar),
      toolbarButton("Bold", "strong", TgIcons.boldIcon, () => commands.toggleInline(surface, "strong")),
      toolbarButton("Italic", "em", TgIcons.italicIcon, () => commands.toggleInline(surface, "em")),
      toolbarButton("Code", "code", TgIcons.codeIcon, () => commands.toggleInline(surface, "code")),
      div(cls(WYSIWYGTextEditorStyles.toolbarSeparator)),
      toolbarButton("Heading", "h2", TgIcons.headingIcon, () => toggleHeading()),
      toolbarButton("Link", "a", TgIcons.linkIcon, () => promptForLink()),
      div(cls(WYSIWYGTextEditorStyles.toolbarSeparator)),
      toolbarButton("Bullet list", "ul", TgIcons.listIcon, () => commands.toggleList(surface, ordered = false)),
      toolbarButton("Numbered list", "ol", TgIcons.orderedListIcon, () => commands.toggleList(surface, ordered = true))
    )

  /** `onMouseDown.preventDefault` keeps the surface focused: a button stealing focus would drop the selection the
    * command is about to act on.
    */
  private def toolbarButton(
    title: String,
    markTag: String,
    icon: String => ReactiveSvgElement[SVGSVGElement],
    run: () => Boolean
  ): HtmlButton =
    button(
      `type`("button"),
      L.title(title),
      aria.label(title),
      cls(WYSIWYGTextEditorStyles.toolbarButton),
      cls(WYSIWYGTextEditorStyles.toolbarButtonActive) <-- activeMarksVar.signal.map(_.contains(markTag)),
      icon(WYSIWYGTextEditorStyles.toolbarIcon),
      onMouseDown.preventDefault --> Observer[dom.MouseEvent](_ => ()),
      onClick.preventDefault.mapToUnit --> Observer[Unit] { _ =>
        run()
        afterEdit()
      }
    )

  private def toggleHeading(): Boolean =
    if (commands.activeMarks(surface).contains("h2")) commands.setBlock(surface, "p")
    else commands.setBlock(surface, "h2")

  private def promptForLink(): Boolean =
    Option(dom.window.prompt("Link address", "https://")).map(_.trim).filter(_.nonEmpty) match {
      case Some(destination) => commands.insertLink(surface, destination)
      case None              => false
    }

  private def surface: dom.html.Element = surfaceRef.orNull

  private def handleInput(): Unit = {
    if (!composing) {
      ensureBlock()
      inputRules(surface)
    }
    afterEdit()
  }

  private def handleKeyDown(event: dom.KeyboardEvent): Unit = {
    val accel = event.ctrlKey || event.metaKey

    if (accel && event.key.toLowerCase == "z") {
      event.preventDefault()
      if (event.shiftKey) restore(history.redo()) else restore(history.undo())
    } else if (accel && event.key.toLowerCase == "y") {
      event.preventDefault()
      restore(history.redo())
    } else if (accel && event.key.toLowerCase == "b") {
      event.preventDefault()
      commands.toggleInline(surface, "strong")
      afterEdit()
    } else if (accel && event.key.toLowerCase == "i") {
      event.preventDefault()
      commands.toggleInline(surface, "em")
      afterEdit()
    } else if (accel && event.key.toLowerCase == "k") {
      event.preventDefault()
      promptForLink()
      afterEdit()
    } else if (!accel && keys.onKeyDown(surface, event)) {
      event.preventDefault()
      afterEdit()
    }
  }

  /** Pasted HTML goes through the serializer and back through the renderer, so it arrives as markdown this editor
    * can represent rather than as whatever markup the source page used.
    */
  private def handlePaste(event: dom.ClipboardEvent): Unit = {
    event.preventDefault()

    val clipboard = event.clipboardData
    val html = Option(clipboard.getData("text/html")).getOrElse("")
    val plain = Option(clipboard.getData("text/plain")).getOrElse("")
    val pasted = if (html.nonEmpty) MarkdownDomSerializer.serialize(parseHtml(html)) else plain

    if (pasted.nonEmpty) insert(pasted)
    afterEdit()
  }

  private def parseHtml(html: String): dom.Element = {
    val holder = dom.document.createElement("div")

    holder.innerHTML = html
    holder
  }

  /** Single-line content is spliced into the current block; anything with structure is added as whole blocks after
    * it, which is the only placement that stays valid for every block kind.
    */
  private def insert(text: String): Unit = {
    val blocks = markdown.toLaminar(text).map(_.ref)

    rangeWithin(surface).foreach { range =>
      range.deleteContents()

      if (blocks.length == 1 && !text.trim.contains("\n")) {
        val fragment = dom.document.createDocumentFragment()

        childNodes(blocks.head).foreach(fragment.appendChild)
        Option(fragment.lastChild).foreach { last =>
          range.insertNode(fragment)
          caretAtEndOf(last)
        }
      } else {
        blockOf(surface, range.startContainer).flatMap(block => topBlockOf(surface, block)) match {
          case Some(anchor) =>
            blocks.foldLeft[dom.Node](anchor) { (previous, node) =>
              surface.insertBefore(node, previous.nextSibling)
              node
            }
            blocks.lastOption.foreach(caretAtEndOf)
          case None =>
            blocks.foreach(surface.appendChild)
            blocks.lastOption.foreach(caretAtEndOf)
        }
      }
    }
  }

  /** contenteditable needs something to put the caret in; an empty surface has nothing. */
  private def ensureBlock(): Unit =
    if (surfaceRef.exists(!_.hasChildNodes())) {
      val paragraph = commands.block("p")

      paragraph.appendChild(filler())
      surface.appendChild(paragraph)
      caretAtStartOf(paragraph)
    }

  private def afterEdit(): Unit = {
    refreshActiveMarks()
    isEmptyVar.set(surfaceRef.forall(_.textContent.trim.isEmpty))
    editedBus.writer.onNext(())
  }

  private def refreshActiveMarks(): Unit =
    surfaceRef.foreach(element => activeMarksVar.set(commands.activeMarks(element)))

  private def emit(): Unit =
    surfaceRef.foreach { element =>
      val serialized = MarkdownDomSerializer.serialize(element)

      if (!lastKnown.contains(serialized)) {
        lastKnown = Some(serialized)
        history.record(serialized)
        inputStateUpdater.onNext(serialized)
      }
    }

  private def onExternalValue(value: String): Unit = {
    latestValue = value

    if (!lastKnown.contains(value)) {
      lastKnown = Some(value)
      if (surfaceRef.isDefined) {
        repaint(value)
        history.record(value)
      }
    }
  }

  private def restore(snapshot: Option[String]): Unit =
    snapshot.foreach { value =>
      lastKnown = Some(value)
      repaint(value)
      inputStateUpdater.onNext(value)
    }

  private def repaint(value: String): Unit =
    surfaceRef.foreach { element =>
      replaceChildren(element, markdown.toLaminar(value).map(_.ref))
      if (!element.hasChildNodes()) {
        val paragraph = commands.block("p")

        paragraph.appendChild(filler())
        element.appendChild(paragraph)
      }
      isEmptyVar.set(element.textContent.trim.isEmpty)
    }
}

object WYSIWYGTextEditor {

  /** Builds an editor from a form `Var` the way `TgTextArea`, `TgInput` and `TgSelect` are built, so a field using
    * it reads like the ones around it and swapping a textarea for the editor is a one-line change.
    */
  implicit class WYSIWYGTextEditorSyntax[A](formState: Var[A]) {

    def wysiwygEditorSig(
      inputAndUpdater: SelectAndUpdate[A, String],
      labelValue: String,
      placeholderValue: String = "",
      minHeightRem: Int = 12,
      showToolbar: Boolean = true
    ): WYSIWYGTextEditor =
      WYSIWYGTextEditor(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        labelValue = labelValue,
        placeholderValue = placeholderValue,
        minHeightRem = minHeightRem,
        showToolbar = showToolbar
      )
  }
}
