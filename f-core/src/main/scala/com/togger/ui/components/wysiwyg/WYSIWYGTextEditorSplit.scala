package com.togger.ui.components.wysiwyg

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement
import com.togger.markdown.{TaskToggle, TgMarkdown, TgMarkdownStyles, TgMarkdownTasks}
import com.togger.ui.auth.TgHtmlTypes.{HtmlButton, HtmlDiv}
import com.togger.ui.organization.TgIcons
import com.togger.util.FrontendMacros.SelectAndUpdate
import io.github.edadma.markdown.MarkdownConfig
import org.scalajs.dom
import org.scalajs.dom.SVGSVGElement

/** Which pane is on screen while the editor is too narrow to show both. */
sealed trait SplitPane

object SplitPane {
  case object Source extends SplitPane
  case object Preview extends SplitPane
}

/** A `trigger` char (e.g. `@`) typed at the start of a word, and the word's text after it. Modeled on
  * [[com.togger.ui.task.quick.TaskQuickInputParser.QuickToken]], kept separate since this one lives inside a
  * textarea rather than a single-line input and has no fixed set of trigger meanings.
  */
case class WYSIWYGTriggerToken(trigger: Char, query: String, start: Int, end: Int)

object WYSIWYGTriggerToken {
  def activeAt(raw: String, caret: Int, triggers: Set[Char]): Option[WYSIWYGTriggerToken] = {
    def isTokenStart(index: Int): Boolean =
      triggers.contains(raw(index)) && (index == 0 || raw(index - 1).isWhitespace)

    def tokenEnd(start: Int): Int = {
      val whitespaceIndex = raw.indexWhere(_.isWhitespace, start)
      if (whitespaceIndex == -1) raw.length else whitespaceIndex
    }

    raw.indices
      .filter(isTokenStart)
      .map(start => WYSIWYGTriggerToken(raw(start), raw.substring(start + 1, tokenEnd(start)), start, tokenEnd(start)))
      .find(token => caret > token.start && caret <= token.end)
  }
}

/** Passed to a trigger's popup builder. `consume` deletes the token text (the caller has already written its pick
  * into the form state) and refocuses the editor; `dismiss` hides the popup until the caret leaves this token,
  * mirroring Escape in [[com.togger.ui.task.quick.CreateTaskQuick]].
  */
case class WYSIWYGTriggerContext(token: WYSIWYGTriggerToken, consume: () => Unit, dismiss: () => Unit)

/** A two-pane markdown editor: the source on one side, a live preview on the other, the way GitHub does it.
  *
  * Shaped like [[com.togger.ui.auth.TgTextArea]] — a `Signal[String]` in, an `Observer[String]` out — so it drops
  * into the same form wiring and can replace one in place.
  *
  * The markdown string is the model, not the DOM. The source pane is a plain `<textarea>`, so the caret, undo and
  * IME are the browser's, and the value reported back is exactly what the author typed — this editor never
  * re-renders the source and so never rewrites it into a canonical form.
  *
  * That is also why the preview can use an ordinary `children <--`. Nothing but Laminar touches those nodes, so
  * there is no hand-mounting, no `replaceChildren` and no mount-callback caveat.
  *
  * Raw HTML stays escaped: a task description is read by people other than its author.
  *
  * @param minHeightRem
  *   the panes' resting height; they grow together with the content
  * @param minWidthRem
  *   the editor's resting width; the enclosing card (sized `fit-content` in the task forms) grows to match
  * @param debounceTimeMillis
  *   how long typing settles before the value is reported to `inputStateUpdater`
  * @param previewDebounceMillis
  *   how long typing settles before the preview is re-parsed; a rendering cost, not part of the form contract
  */
case class WYSIWYGTextEditorSplit(
  inputSignal: Signal[String],
  inputStateUpdater: Observer[String],
  labelValue: String,
  placeholderValue: String = "",
  minHeightRem: Int = 20,
  minWidthRem: Int = 40,
  showToolbar: Boolean = true,
  config: MarkdownConfig = TgMarkdown.defaultConfig,
  styles: TgMarkdownStyles = TgMarkdownStyles.default,
  debounceTimeMillis: Int = 300,
  previewDebounceMillis: Int = 150,
  previewEmptyText: String = "Nothing to preview",
  triggers: Map[Char, WYSIWYGTriggerContext => HtmlElement] = Map.empty
) {

  /** Laminar has no prop for a textarea's own selection event, which is the one that fires on every caret move. */
  private val onSelectionChange = eventProp[dom.Event]("select")

  /** The preview's checkboxes are live: the source pane is right there to write the change back into. The live
    * editor passes no observer, because a click inside its contenteditable places a caret rather than toggling.
    */
  private val markdown =
    new TgMarkdown(config, allowRawHtml = false, styles, onTaskToggle = Some(Observer[TaskToggle](applyTaskToggle)))

  private val sourceVar = Var("")
  private val activePaneVar = Var[SplitPane](SplitPane.Source)
  private val activeMarksVar = Var(Set.empty[String])
  private val caretVar = Var(0)
  private val dismissedTriggerStartVar = Var(Option.empty[Int])

  /** Token plus the caret that made it active: the popup repositions to the caret as the author keeps typing the
    * query, not just once when the trigger char appears.
    */
  private val activeTriggerSignal: Signal[Option[(WYSIWYGTriggerToken, Int)]] =
    sourceVar.signal.combineWith(caretVar.signal, dismissedTriggerStartVar.signal).map {
      case (text, caret, dismissed) =>
        WYSIWYGTriggerToken
          .activeAt(text, caret, triggers.keySet)
          .filterNot(token => dismissed.contains(token.start))
          .map(token => token -> caret)
    }

  /** `Signal#now` is protected outside Airstream internals, so the one synchronous read (Escape's key handler) goes
    * straight to the three `Var`s instead of the derived signal above.
    */
  private def currentActiveTriggerToken(): Option[WYSIWYGTriggerToken] =
    WYSIWYGTriggerToken
      .activeAt(sourceVar.now(), caretVar.now(), triggers.keySet)
      .filterNot(token => dismissedTriggerStartVar.now().contains(token.start))

  /** The last markdown the editor and its caller agree on. An incoming value equal to this is the echo of our own
    * emit; acting on it would re-assign the textarea's value and drop the caret to the end of the text.
    */
  private var lastKnown: Option[String] = None

  private var sourceRef: Option[dom.html.TextArea] = None

  val htmlElement: HtmlDiv =
    div(
      label(cls(WYSIWYGTextEditorStyles.label), labelValue),
      div(
        cls(WYSIWYGTextEditorStyles.wrapper),
        position.relative, // anchors the trigger popup below the panes
        minWidth.rem(minWidthRem),
        if (showToolbar) toolbarElement else emptyNode,
        tabBarElement,
        div(
          cls(WYSIWYGTextEditorStyles.splitPanes),
          minHeight.rem(minHeightRem),
          sourcePane,
          previewPane
        ),
        child.maybe <-- activeTriggerSignal.distinct.map(renderTriggerPopup)
      ),
      inputSignal --> Observer[String](onExternalValue),
      sourceVar.signal.changes.debounce(debounceTimeMillis) --> Observer[String](emit)
    )

  /** Wraps the trigger's own popup content in a container positioned at the caret, via [[caretCoordinates]]. The
    * popup builders stay focused on their own look (background, border, list) and know nothing about layout.
    */
  private def renderTriggerPopup(active: Option[(WYSIWYGTriggerToken, Int)]): Option[HtmlElement] =
    active.flatMap { case (activeToken, caret) =>
      triggers.get(activeToken.trigger).map { builder =>
        val content = builder(
          WYSIWYGTriggerContext(
            token = activeToken,
            consume = () => consumeTriggerToken(activeToken),
            dismiss = () => dismissedTriggerStartVar.set(Some(activeToken.start))
          )
        )
        val (caretTop, caretLeft, lineHeight) =
          sourceRef.map(caretCoordinates(_, caret)).getOrElse((0.0, 0.0, 0.0))
        div(
          cls("absolute z-20"),
          top(s"${caretTop + lineHeight}px"),
          left(s"${caretLeft}px"),
          content
        )
      }
    }

  /** Where the caret sits inside the textarea, in pixels relative to its own border box (`lineHeight` is returned
    * alongside so the popup can sit a full line below it, rather than over the text being typed).
    *
    * Textareas have no API for this, so the standard trick applies: clone every CSS property that affects text
    * layout onto a hidden mirror div, fill it with the text up to the caret, and read the offset of a marker span
    * right after it. `scrollTop`/`scrollLeft` are subtracted because the mirror is never scrolled, but the real
    * textarea might be.
    */
  private def caretCoordinates(element: dom.html.TextArea, position: Int): (Double, Double, Double) = {
    val computed = dom.window.getComputedStyle(element)
    val mirror = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    val mirrorStyle = mirror.style

    mirrorStyle.setProperty("position", "absolute")
    mirrorStyle.setProperty("visibility", "hidden")
    mirrorStyle.setProperty("top", "0")
    mirrorStyle.setProperty("left", "-9999px")
    WYSIWYGTextEditorSplit.caretMirrorProperties.foreach(prop =>
      mirrorStyle.setProperty(prop, computed.getPropertyValue(prop))
    )

    dom.document.body.appendChild(mirror): Unit
    mirror.textContent = element.value.substring(0, position)

    val marker = dom.document.createElement("span").asInstanceOf[dom.html.Element]
    marker.textContent = "​" // zero-width space: keeps the span measurable even right after a newline
    mirror.appendChild(marker): Unit

    val top = marker.offsetTop.toDouble - element.scrollTop
    val left = marker.offsetLeft.toDouble - element.scrollLeft
    val lineHeight = computed.getPropertyValue("line-height").stripSuffix("px").toDoubleOption.getOrElse(20.0)

    dom.document.body.removeChild(mirror): Unit

    (top, left, lineHeight)
  }

  /** Deletes the token's text (the caller already wrote its pick into the form state) and puts the caret back where
    * the token used to start, the same order `applyResult` uses for the same reason: assigning a textarea's value
    * clears its selection.
    */
  private def consumeTriggerToken(token: WYSIWYGTriggerToken): Unit = {
    val updated = sourceVar.now().patch(token.start, "", token.end - token.start)
    sourceVar.set(updated)
    sourceRef.foreach { element =>
      if (element.value != updated) element.value = updated
      element.setSelectionRange(token.start, token.start)
      element.focus()
    }
    caretVar.set(token.start)
    dismissedTriggerStartVar.set(None)
  }

  private def sourcePane: HtmlDiv =
    div(
      cls(WYSIWYGTextEditorStyles.splitPaneSource),
      cls <-- visibility(SplitPane.Source),
      textArea(
        cls(WYSIWYGTextEditorStyles.splitSource),
        placeholder(placeholderValue),
        aria.label(labelValue),
        controlled(
          value <-- sourceVar,
          onInput.mapToValue --> sourceVar
        ),
        onMountCallback(context => sourceRef = Some(context.thisNode.ref)),
        onKeyDown --> Observer[dom.KeyboardEvent](handleKeyDown),
        onKeyUp.mapToUnit --> Observer[Unit](_ => refreshActiveMarks()),
        onClick.mapToUnit --> Observer[Unit](_ => refreshActiveMarks()),
        onFocus.mapToUnit --> Observer[Unit](_ => refreshActiveMarks()),
        onSelectionChange.mapToUnit --> Observer[Unit](_ => refreshActiveMarks())
      )
    )

  private def previewPane: HtmlDiv =
    div(
      cls(WYSIWYGTextEditorStyles.splitPanePreview),
      cls <-- visibility(SplitPane.Preview),
      div(
        cls(WYSIWYGTextEditorStyles.splitPreview),
        aria.label(s"$labelValue preview"),
        children <-- previewSignal.map(renderPreview)
      )
    )

  private def previewSignal: Signal[String] =
    sourceVar.signal.changes.debounce(previewDebounceMillis).startWith(sourceVar.now())

  private def renderPreview(text: String): Seq[HtmlElement] =
    if (text.trim.isEmpty) Seq(div(cls(WYSIWYGTextEditorStyles.splitPreviewEmpty), previewEmptyText))
    else markdown.toLaminar(text)

  /** Both panes stay in the DOM; only their classes change, so the wide layout is pure CSS and the pane a viewer
    * is not looking at keeps its scroll position and its caret.
    */
  private def visibility(pane: SplitPane): Signal[String] =
    activePaneVar.signal.map { active =>
      if (active == pane) WYSIWYGTextEditorStyles.splitPaneShown
      else WYSIWYGTextEditorStyles.splitPaneHiddenNarrow
    }

  private def tabBarElement: HtmlDiv =
    div(
      cls(WYSIWYGTextEditorStyles.splitTabBar),
      role("tablist"),
      tabButton("Write", SplitPane.Source),
      tabButton("Preview", SplitPane.Preview)
    )

  private def tabButton(text: String, pane: SplitPane): HtmlButton = {
    val isActive = activePaneVar.signal.map(_ == pane)

    button(
      `type`("button"),
      role("tab"),
      aria.selected <-- isActive,
      cls(WYSIWYGTextEditorStyles.splitTab),
      cls <-- isActive.map { active =>
        if (active) WYSIWYGTextEditorStyles.splitTabActive else WYSIWYGTextEditorStyles.splitTabInactive
      },
      onClick.preventDefault.mapToUnit --> Observer[Unit](_ => activePaneVar.set(pane)),
      text
    )
  }

  private def toolbarElement: HtmlDiv =
    div(
      cls(WYSIWYGTextEditorStyles.toolbar),
      toolbarButton("Heading", "h2", TgIcons.headingIcon, WYSIWYGMarkdownCommands.togglePrefix(_, "## ")),
      toolbarButton("Bold", "strong", TgIcons.boldIcon, WYSIWYGMarkdownCommands.toggleInline(_, "**")),
      toolbarButton("Italic", "em", TgIcons.italicIcon, WYSIWYGMarkdownCommands.toggleInline(_, "*")),
      toolbarButton("Quote", "blockquote", TgIcons.quoteIcon, WYSIWYGMarkdownCommands.togglePrefix(_, "> ")),
      toolbarButton("Code", "code", TgIcons.codeIcon, WYSIWYGMarkdownCommands.toggleInline(_, "`")),
      div(cls(WYSIWYGTextEditorStyles.toolbarSeparator)),
      toolbarButton("Link", "a", TgIcons.linkIcon, linkCommand),
      div(cls(WYSIWYGTextEditorStyles.toolbarSeparator)),
      toolbarButton(
        "Numbered list",
        "ol",
        TgIcons.orderedListIcon,
        WYSIWYGMarkdownCommands.toggleList(_, ordered = true)
      ),
      toolbarButton("Bullet list", "ul", TgIcons.listIcon, WYSIWYGMarkdownCommands.toggleList(_, ordered = false)),
      toolbarButton("Task list", "task", TgIcons.taskListIcon, WYSIWYGMarkdownCommands.toggleTaskList)
    )

  /** `onMouseDown.preventDefault` keeps the textarea focused: a button taking focus would collapse the selection
    * the command is about to act on.
    */
  private def toolbarButton(
    title: String,
    markTag: String,
    icon: String => ReactiveSvgElement[SVGSVGElement],
    command: TextSelection => TextSelection
  ): HtmlButton =
    button(
      `type`("button"),
      L.title(title),
      aria.label(title),
      cls(WYSIWYGTextEditorStyles.toolbarButton),
      cls(WYSIWYGTextEditorStyles.toolbarButtonActive) <-- activeMarksVar.signal.map(_.contains(markTag)),
      icon(WYSIWYGTextEditorStyles.toolbarIcon),
      onMouseDown.preventDefault --> Observer[dom.MouseEvent](_ => ()),
      onClick.preventDefault.mapToUnit --> Observer[Unit](_ => runCommand(command))
    )

  /** The three formatting shortcuts, plus Enter's effect inside a list. Tab, Backspace and undo stay the
    * browser's — the whole reason this editor is a textarea.
    */
  private def handleKeyDown(event: dom.KeyboardEvent): Unit =
    if (event.key == "Escape")
      currentActiveTriggerToken().foreach { token =>
        event.stopPropagation()
        dismissedTriggerStartVar.set(Some(token.start))
      }
    else if (event.ctrlKey || event.metaKey)
      event.key.toLowerCase match {
        case "b" =>
          event.preventDefault()
          runCommand(WYSIWYGMarkdownCommands.toggleInline(_, "**"))
        case "i" =>
          event.preventDefault()
          runCommand(WYSIWYGMarkdownCommands.toggleInline(_, "*"))
        case "k" =>
          event.preventDefault()
          runCommand(linkCommand)
        case _ => ()
      }
    else if (event.key == "Enter" && !event.shiftKey) handleEnter(event)

  /** Plain Enter is only intercepted when the caret sits in a list item — everywhere else it stays the browser's,
    * so `preventDefault` waits until `continueList` has actually found something to do.
    */
  private def handleEnter(event: dom.KeyboardEvent): Unit =
    sourceRef.foreach { element =>
      WYSIWYGMarkdownCommands
        .continueList(TextSelection(element.value, element.selectionStart, element.selectionEnd))
        .foreach { result =>
          event.preventDefault()
          applyResult(result, element)
        }
    }

  private def linkCommand(selection: TextSelection): TextSelection =
    Option(dom.window.prompt("Link address", "https://")).map(_.trim).filter(_.nonEmpty) match {
      case Some(destination) => WYSIWYGMarkdownCommands.insertLink(selection, destination)
      case None              => selection
    }

  private def runCommand(command: TextSelection => TextSelection): Unit =
    sourceRef.foreach(element =>
      applyResult(command(TextSelection(element.value, element.selectionStart, element.selectionEnd)), element)
    )

  /** The `Var` is written before the DOM on purpose: the controlled binding assigns `value` synchronously while
    * `set` runs, and assigning a textarea's value is what clears its selection. Restoring the selection afterwards
    * is therefore the last step, not the first.
    */
  private def applyResult(result: TextSelection, element: dom.html.TextArea): Unit = {
    sourceVar.set(result.text)
    if (element.value != result.text) element.value = result.text
    element.setSelectionRange(result.start, result.end)
    element.focus()
    refreshActiveMarks()
  }

  private def refreshActiveMarks(): Unit =
    sourceRef.foreach { element =>
      caretVar.set(element.selectionStart)
      activeMarksVar.set(
        WYSIWYGMarkdownCommands.activeMarks(TextSelection(element.value, element.selectionStart, element.selectionEnd))
      )
    }

  /** The click landed in the preview, so the author's place in the source pane is untouched by it and should
    * survive the rewrite — the same reason `runCommand` restores the selection.
    *
    * A rewrite the source declines leaves everything alone; the checkbox undoes itself, being controlled.
    */
  private def applyTaskToggle(toggle: TaskToggle): Unit =
    TgMarkdownTasks.setChecked(sourceVar.now(), toggle.index, toggle.checked).foreach { updated =>
      val selection = sourceRef.map(element => (element.selectionStart, element.selectionEnd))

      sourceVar.set(updated)
      sourceRef.foreach { element =>
        if (element.value != updated) element.value = updated
        selection.foreach { case (start, end) => element.setSelectionRange(start, end) }
      }
    }

  private def emit(value: String): Unit =
    if (!lastKnown.contains(value)) {
      lastKnown = Some(value)
      inputStateUpdater.onNext(value)
    }

  private def onExternalValue(value: String): Unit =
    if (!lastKnown.contains(value)) {
      lastKnown = Some(value)
      sourceVar.set(value)
    }
}

object WYSIWYGTextEditorSplit {

  /** The CSS properties [[WYSIWYGTextEditorSplit.caretCoordinates]] copies onto its mirror div — every property
    * that affects where text wraps or how wide/tall a character renders, kebab-cased for `getPropertyValue`.
    */
  private val caretMirrorProperties: Seq[String] = Seq(
    "box-sizing",
    "width",
    "overflow-x",
    "overflow-y",
    "border-top-width",
    "border-right-width",
    "border-bottom-width",
    "border-left-width",
    "padding-top",
    "padding-right",
    "padding-bottom",
    "padding-left",
    "font-style",
    "font-variant",
    "font-weight",
    "font-stretch",
    "font-size",
    "line-height",
    "font-family",
    "text-align",
    "text-transform",
    "text-indent",
    "text-decoration",
    "letter-spacing",
    "word-spacing",
    "tab-size",
    "white-space",
    "word-wrap",
    "word-break"
  )

  /** Builds an editor from a form `Var` the way `TgTextArea`, `TgInput` and `TgSelect` are built, so a field using
    * it reads like the ones around it.
    */
  implicit class WYSIWYGTextEditorSplitSyntax[A](formState: Var[A]) {

    def wysiwygSplitEditorSig(
      inputAndUpdater: SelectAndUpdate[A, String],
      labelValue: String,
      placeholderValue: String = "",
      minHeightRem: Int = 30,
      minWidthRem: Int = 60,
      showToolbar: Boolean = true,
      triggers: Map[Char, WYSIWYGTriggerContext => HtmlElement] = Map.empty
    ): WYSIWYGTextEditorSplit =
      WYSIWYGTextEditorSplit(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        labelValue = labelValue,
        placeholderValue = placeholderValue,
        minHeightRem = minHeightRem,
        minWidthRem = minWidthRem,
        showToolbar = showToolbar,
        triggers = triggers
      )
  }
}
