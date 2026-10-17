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
import scala.scalajs.js

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

/** Reported to `onTriggerChange` while a trigger char is active (`None` once it no longer is). `consume` deletes the
  * token text — the caller has already written its pick into its own form state — and refocuses the editor. The
  * editor renders no popup of its own; the caller decides where and how to show one (e.g. anchored to a badge) and
  * calls `consume` from its pick handler.
  */
case class WYSIWYGTriggerEvent(trigger: Char, query: String, consume: () => Unit)

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
  triggerChars: Set[Char] = Set.empty,
  onTriggerChange: Observer[Option[WYSIWYGTriggerEvent]] = Observer.empty,
  onEditorKeyDown: dom.KeyboardEvent => Unit = _ => ()
) {

  /** Laminar has no prop for a textarea's own selection event, which is the one that fires on every caret move. */
  private val onSelectionChange = eventProp[dom.Event]("select")

  /** The preview's checkboxes are live: the source pane is right there to write the change back into. The live
    * editor passes no observer, because a click inside its contenteditable places a caret rather than toggling.
    */
  private val markdown =
    new TgMarkdown(config, allowRawHtml = false, styles, onTaskToggle = Some(Observer[TaskToggle](applyTaskToggle)))

  private val sourceVar = Var("")
  /** True once the editor is given less than `minWidthRem`; the panes then stack behind the tab bar. */
  private val stackedVar = Var(false)
  private val activePaneVar = Var[SplitPane](SplitPane.Source)
  private val activeMarksVar = Var(Set.empty[String])
  private val caretVar = Var(0)
  private val dismissedTriggerStartVar = Var(Option.empty[Int])

  private val activeTriggerTokenSignal: Signal[Option[WYSIWYGTriggerToken]] =
    sourceVar.signal.combineWith(caretVar.signal, dismissedTriggerStartVar.signal).map {
      case (text, caret, dismissed) =>
        WYSIWYGTriggerToken.activeAt(text, caret, triggerChars).filterNot(token => dismissed.contains(token.start))
    }

  /** `Signal#now` is protected outside Airstream internals, so the one synchronous read (Escape's key handler) goes
    * straight to the two `Var`s instead of the derived signal above.
    */
  private def currentActiveTriggerToken(): Option[WYSIWYGTriggerToken] =
    WYSIWYGTriggerToken
      .activeAt(sourceVar.now(), caretVar.now(), triggerChars)
      .filterNot(token => dismissedTriggerStartVar.now().contains(token.start))

  /** The last markdown the editor and its caller agree on. An incoming value equal to this is the echo of our own
    * emit; acting on it would re-assign the textarea's value and drop the caret to the end of the text.
    */
  private var lastKnown: Option[String] = None

  private var sourceRef: Option[dom.html.TextArea] = None

  /** Pulls DOM focus back onto the textarea — the caller's job after something else (clicking a badge, say) opens a
    * popup that should still read arrow keys from here, the way `CreateTaskQuick` refocuses `nameInput` after a
    * badge click so the same keydown handler keeps driving the popup it just opened.
    */
  def focus(): Unit = sourceRef.foreach(_.focus())

  val htmlElement: HtmlDiv =
    div(
      onMountCallback(context => observeWidth(context.thisNode.ref)),
      div(cls(WYSIWYGTextEditorStyles.widthReserve), width.rem(minWidthRem)),
      label(cls(WYSIWYGTextEditorStyles.label), labelValue),
      div(
        cls(WYSIWYGTextEditorStyles.wrapper),
        if (showToolbar) toolbarElement else emptyNode,
        child <-- stackedVar.signal.map(stacked => if (stacked) tabBarElement else emptyNode),
        div(
          cls <-- stackedVar.signal.map(stacked =>
            if (stacked) WYSIWYGTextEditorStyles.splitPanesStacked
            else WYSIWYGTextEditorStyles.splitPanesSideBySide
          ),
          minHeight.rem(minHeightRem),
          sourcePane,
          previewPane
        )
      ),
      activeTriggerTokenSignal.distinct.map(toTriggerEvent) --> onTriggerChange,
      inputSignal --> Observer[String](onExternalValue),
      sourceVar.signal.changes.debounce(debounceTimeMillis) --> Observer[String](emit)
    )

  /** Watches the editor's own box, which `widthReserve` keeps from being measured at a width the editor itself
    * asked for. jsdom has no `ResizeObserver`, so there the editor just stays side by side.
    */
  private def observeWidth(element: dom.html.Div): Unit =
    if (js.typeOf(js.Dynamic.global.ResizeObserver) != "undefined") {
      val observer = new dom.ResizeObserver((_, _) => stackedVar.set(element.clientWidth < minWidthPx))
      observer.observe(element)
    }

  private def minWidthPx: Double = {
    val rootFontSize = dom.window.getComputedStyle(dom.document.documentElement).fontSize
    minWidthRem * rootFontSize.stripSuffix("px").toDoubleOption.getOrElse(16.0)
  }

  private def toTriggerEvent(token: Option[WYSIWYGTriggerToken]): Option[WYSIWYGTriggerEvent] =
    token.map(activeToken =>
      WYSIWYGTriggerEvent(activeToken.trigger, activeToken.query, () => consumeTriggerToken(activeToken))
    )

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
      cls <-- layout(SplitPane.Source, WYSIWYGTextEditorStyles.splitPaneSourceSideBySide),
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
      cls <-- layout(SplitPane.Preview, WYSIWYGTextEditorStyles.splitPanePreviewSideBySide),
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

  /** Both panes stay in the DOM; only their classes change, so the pane a viewer is not looking at keeps its scroll
    * position and its caret.
    */
  private def layout(pane: SplitPane, sideBySideClasses: String): Signal[String] =
    stackedVar.signal.combineWith(activePaneVar.signal).map { case (stacked, active) =>
      if (!stacked) sideBySideClasses
      else if (active == pane) WYSIWYGTextEditorStyles.splitPaneShown
      else WYSIWYGTextEditorStyles.splitPaneHidden
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
    *
    * `onEditorKeyDown` runs first and gets to claim the key (via `preventDefault`) before any of this editor's own
    * handling — the caller's popup (e.g. a status dropdown open above this field) needs ArrowUp/ArrowDown/Enter
    * before they'd otherwise move the caret or insert a newline.
    */
  private def handleKeyDown(event: dom.KeyboardEvent): Unit = {
    onEditorKeyDown(event)
    if (!event.defaultPrevented) {
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
    }
  }

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
      triggerChars: Set[Char] = Set.empty,
      onTriggerChange: Observer[Option[WYSIWYGTriggerEvent]] = Observer.empty,
      onEditorKeyDown: dom.KeyboardEvent => Unit = _ => ()
    ): WYSIWYGTextEditorSplit =
      WYSIWYGTextEditorSplit(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        labelValue = labelValue,
        placeholderValue = placeholderValue,
        minHeightRem = minHeightRem,
        minWidthRem = minWidthRem,
        showToolbar = showToolbar,
        triggerChars = triggerChars,
        onTriggerChange = onTriggerChange,
        onEditorKeyDown = onEditorKeyDown
      )
  }
}
