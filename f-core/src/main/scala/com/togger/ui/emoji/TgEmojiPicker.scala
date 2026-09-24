package com.togger.ui.emoji

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.togger.ui.auth.TgHtmlTypes.{HtmlButton, HtmlDiv}
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import org.scalajs.dom
import org.scalajs.dom.KeyboardEvent

import scala.scalajs.js

/** A Laminar re-implementation of missive/emoji-mart's picker panel: search, category nav, scrollable grid, preview
  * bar, skin-tone selector and a LocalStorage-backed "frequently used" row.
  *
  * The panel renders itself only; opening/closing and positioning belong to the caller (see [[TgEmojiField]]).
  */
case class TgEmojiPicker(
  onEmojiSelect: Observer[SelectedEmoji],
  localStorage: LocalStorage,
  config: TgEmojiPickerConfig = TgEmojiPickerConfig(),
  onClickOutside: Observer[Unit] = Observer.empty
) {

  import TgEmojiPicker._

  private val queryVar: Var[String] = Var("")
  private val previewVar: Var[Option[Emoji]] = Var(None)
  private val activeCategoryVar: Var[EmojiCategoryId] =
    Var(config.categories.headOption.getOrElse(EmojiCategoryId.People))
  private val skinPickerOpenVar: Var[Boolean] = Var(false)

  private val skinToneStore = new EmojiSkinToneStore(localStorage)
  private val frequentStore = new EmojiFrequentStore(localStorage)

  /** Snapshotted on open so picked emoji don't jump around under the cursor. */
  private val frequentIdsVar: Var[Vector[String]] = Var(frequentStore.top(config.frequentCount))

  private var gridElement: Option[dom.HTMLElement] = None

  private val debouncedQuery: Signal[String] =
    queryVar.signal.composeChanges(_.debounce(config.searchDebounceMillis))

  private val renderStateSignal: Signal[RenderState] =
    EmojiDataLoader.state
      .combineWith(debouncedQuery, frequentIdsVar.signal, skinToneStore.current)
      .map { case (state, query, frequentIds, tone) =>
        val sections = state match {
          case EmojiDataState.Loaded(dataSet) => buildSections(dataSet, query, frequentIds)
          case _                              => Vector.empty
        }
        RenderState(state, sections, tone, query.trim.nonEmpty)
      }

  val element: HtmlDiv =
    div(
      cls(panelCls),
      styleAttr := s"width: ${config.panelWidthPx}px",
      onMountCallback { _ =>
        EmojiDataLoader.load()
        frequentIdsVar.set(frequentStore.top(config.frequentCount))
        if (config.autoFocus) searchInputElement.ref.focus()
      },
      onKeyDown --> (event => handleKeyDown(event)),
      onMountBind { ctx =>
        val rootNode = ctx.thisNode.ref
        val clickedOutside = documentEvents(_.onClick)
          .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))

        clickedOutside.mapToUnit --> onClickOutside
      },
      navIn(NavPosition.Top),
      if (config.searchPosition == SearchPosition.Sticky) searchRow else emptyNode,
      previewBarIn(PreviewPosition.Top),
      gridArea,
      previewBarIn(PreviewPosition.Bottom),
      navIn(NavPosition.Bottom)
    )

  // -- Sections ------------------------------------------------------------------------------------------------

  private def buildSections(
    dataSet: EmojiDataSet,
    query: String,
    frequentIds: Vector[String]
  ): Vector[EmojiSection] =
    if (query.trim.nonEmpty) {
      val results = searchIndexFor(dataSet).search(query, exceptEmojis = config.exceptEmojis)
      if (results.isEmpty) Vector.empty else Vector(EmojiSection(None, "Search results", results))
    } else
      config.categories.toVector.flatMap {
        case EmojiCategoryId.Frequent =>
          buildSection(EmojiCategoryId.Frequent, dataSet.resolveAll(frequentIds))
        case categoryId =>
          dataSet.category(categoryId).flatMap(found => buildSection(categoryId, dataSet.resolveAll(found.emojiIds)))
      }

  private def buildSection(categoryId: EmojiCategoryId, emojis: Vector[Emoji]): Option[EmojiSection] = {
    val visible = emojis.filterNot(emoji => config.exceptEmojis.contains(emoji.id))
    if (visible.isEmpty) None else Some(EmojiSection(Some(categoryId), categoryId.label, visible))
  }

  // -- Nav -----------------------------------------------------------------------------------------------------

  private def navIn(position: NavPosition): Modifier[HtmlElement] =
    if (config.navPosition == position) navBar else emptyNode

  private lazy val navBar: HtmlDiv =
    div(
      cls(navBarCls),
      cls(if (config.navPosition == NavPosition.Bottom) "border-t" else "border-b"),
      config.categories.map(navButton)
    )

  private def navButton(categoryId: EmojiCategoryId): HtmlButton =
    button(
      `type`("button"),
      cls(navButtonCls),
      cls <-- activeCategoryVar.signal.map(active =>
        if (active == categoryId) "border-brand text-brand" else "border-transparent text-body hover:text-heading"
      ),
      aria.label := categoryId.label,
      title := categoryId.label,
      onClick.stopPropagation.mapToUnit --> (_ => goToCategory(categoryId)),
      EmojiCategoryIcons.iconFor(categoryId, "w-5 h-5")
    )

  private def goToCategory(categoryId: EmojiCategoryId): Unit = {
    activeCategoryVar.set(categoryId)
    if (queryVar.now().nonEmpty) {
      queryVar.set("")
      // the grid only re-renders after the search debounce has elapsed
      val _ = js.timers.setTimeout((config.searchDebounceMillis + 20).toDouble)(scrollToCategory(categoryId))
    } else scrollToCategory(categoryId)
  }

  private def scrollToCategory(categoryId: EmojiCategoryId): Unit =
    for {
      grid <- gridElement
      sectionElement <- Option(grid.querySelector(s"[data-category='${categoryId.jsonId}']"))
    } grid.scrollTop = sectionElement.asInstanceOf[dom.HTMLElement].offsetTop

  private def updateActiveCategory(): Unit =
    gridElement.foreach { grid =>
      val sectionNodes = grid.querySelectorAll("[data-category]")
      (0 until sectionNodes.length).reverseIterator
        .map(index => sectionNodes(index).asInstanceOf[dom.HTMLElement])
        .find(sectionElement => sectionElement.offsetTop <= grid.scrollTop + 1)
        .flatMap(sectionElement => EmojiCategoryId.fromJsonId(sectionElement.getAttribute("data-category")))
        .foreach(activeCategoryVar.set)
    }

  // -- Search --------------------------------------------------------------------------------------------------

  private lazy val searchInputElement =
    input(
      cls(searchInputCls),
      `type`("text"),
      aria.label := "Search emojis",
      placeholder("Search emojis"),
      controlled(
        value <-- queryVar.signal,
        onInput.mapToValue --> queryVar.writer
      )
    )

  /** `Sticky` renders this row above the scroll container, so it stays visible; `Static` renders it as the first thing
    * inside the container, so it scrolls away with the grid.
    */
  private lazy val searchRow: HtmlDiv =
    div(
      cls(searchRowCls),
      div(
        cls("relative flex-1"),
        div(
          cls("absolute inset-y-0 start-0 flex items-center ps-3 pointer-events-none"),
          TgIcons.searchIcon(className = "w-4 h-4 text-body")
        ),
        searchInputElement,
        child <-- queryVar.signal.map(query => if (query.isEmpty) emptyNode else clearSearchButton)
      ),
      skinToneSelectorIn(SkinTonePosition.Search)
    )

  private lazy val clearSearchButton: HtmlButton =
    button(
      `type`("button"),
      cls("absolute inset-y-0 end-0 flex items-center pe-3 text-body hover:text-heading cursor-pointer"),
      aria.label := "Clear search",
      onClick.stopPropagation.mapToUnit --> (_ => {
        queryVar.set("")
        searchInputElement.ref.focus()
      }),
      "×"
    )

  // -- Grid ----------------------------------------------------------------------------------------------------

  private lazy val gridArea: HtmlDiv =
    div(
      cls(gridAreaCls),
      role("grid"),
      styleAttr := s"max-height: ${config.gridMaxHeightPx}px",
      onMountCallback(ctx => gridElement = Some(ctx.thisNode.ref)),
      onUnmountCallback(_ => gridElement = None),
      onScroll.mapToUnit --> (_ => updateActiveCategory()),
      onMouseLeave.mapToUnit --> (_ => previewVar.set(None)),
      if (config.searchPosition == SearchPosition.Static) searchRow else emptyNode,
      child <-- renderStateSignal.map(gridContent)
    )

  private def gridContent(renderState: RenderState): HtmlDiv =
    renderState.state match {
      case EmojiDataState.Loading         => loadingBox
      case EmojiDataState.Failed(message) => errorBox(message)
      case EmojiDataState.Loaded(dataSet) =>
        if (renderState.sections.isEmpty) noResultsBox(dataSet, renderState.tone)
        else div(renderState.sections.map(renderSection(_, renderState.tone)))
    }

  private def renderSection(section: EmojiSection, tone: SkinTone): HtmlDiv =
    div(
      dataAttr("category") := section.categoryId.map(_.jsonId).getOrElse("search"),
      h3(cls(sectionHeaderCls), section.label),
      div(
        cls("grid"),
        role("rowgroup"),
        styleAttr := s"grid-template-columns: repeat(${config.perLine}, minmax(0, 1fr))",
        section.emojis.map(emojiButton(_, tone))
      )
    )

  private def emojiButton(emoji: Emoji, tone: SkinTone): HtmlButton =
    button(
      `type`("button"),
      dataAttr("emoji-id") := emoji.id,
      role("gridcell"),
      aria.label := emoji.name,
      title := emoji.name,
      cls(emojiButtonCls),
      styleAttr := s"width: ${config.emojiButtonSize}px; height: ${config.emojiButtonSize}px; " +
        s"font-size: ${config.emojiSize}px; line-height: 1",
      onMouseEnter.mapToUnit --> (_ => previewVar.set(Some(emoji))),
      onFocus.mapToUnit --> (_ => previewVar.set(Some(emoji))),
      onClick.stopPropagation.mapToUnit --> (_ => select(emoji)),
      emoji.native(tone)
    )

  private def select(emoji: Emoji): Unit = {
    val tone = skinToneStore.now()
    frequentStore.record(emoji.id)
    onEmojiSelect.onNext(SelectedEmoji(emoji, tone, emoji.native(tone)))
  }

  private def loadingBox: HtmlDiv =
    div(
      cls("flex items-center justify-center"),
      styleAttr := s"height: ${config.gridMaxHeightPx}px",
      SpinnerService.spinerElement
    )

  private def errorBox(message: String): HtmlDiv =
    div(
      cls("flex flex-col items-center justify-center gap-2 px-4 text-center"),
      styleAttr := s"height: ${config.gridMaxHeightPx}px",
      p(cls("text-sm font-medium text-heading"), "Couldn't load emojis"),
      p(cls("text-xs text-body"), message),
      button(
        `type`("button"),
        cls(retryButtonCls),
        onClick.stopPropagation.mapToUnit --> (_ => EmojiDataLoader.reload()),
        "Retry"
      )
    )

  private def noResultsBox(dataSet: EmojiDataSet, tone: SkinTone): HtmlDiv = {
    val glyph: String = dataSet.get(config.noResultsEmoji).map(_.native(tone)).getOrElse(FallbackNoResultsGlyph)

    div(
      cls("flex flex-col items-center justify-center gap-2"),
      styleAttr := s"height: ${config.gridMaxHeightPx}px",
      span(styleAttr := "font-size: 32px; line-height: 1", glyph),
      p(cls("text-sm text-body"), "No emoji found")
    )
  }

  // -- Preview -------------------------------------------------------------------------------------------------

  private def previewBarIn(position: PreviewPosition): Modifier[HtmlElement] =
    if (config.previewPosition == position) previewBar else emptyNode

  private lazy val previewBar: HtmlDiv =
    div(
      cls(previewBarCls),
      cls(if (config.previewPosition == PreviewPosition.Top) "border-b" else "border-t"),
      div(cls("flex min-w-0 flex-1 items-center"), child <-- previewContentSignal),
      skinToneSelectorIn(SkinTonePosition.Preview)
    )

  private lazy val previewContentSignal: Signal[HtmlDiv] =
    previewVar.signal.combineWith(renderStateSignal).map { case (hovered, renderState) =>
      hovered match {
        case Some(emoji) =>
          div(
            cls("flex min-w-0 items-center gap-2"),
            span(cls("shrink-0"), styleAttr := "font-size: 28px; line-height: 1", emoji.native(renderState.tone)),
            div(
              cls("min-w-0"),
              p(cls("truncate text-sm font-medium text-heading"), emoji.name),
              p(cls("truncate text-xs text-body"), emoji.shortcode)
            )
          )
        case None =>
          div(
            cls("flex items-center gap-2"),
            span(
              cls("shrink-0"),
              styleAttr := "font-size: 28px; line-height: 1",
              previewPlaceholderGlyph(renderState)
            ),
            p(cls("text-sm text-body"), "Pick an emoji…")
          )
      }
    }

  private def previewPlaceholderGlyph(renderState: RenderState): String =
    renderState.state match {
      case EmojiDataState.Loaded(dataSet) =>
        dataSet.get(config.previewEmoji).map(_.native(renderState.tone)).getOrElse(FallbackPreviewGlyph)
      case _ => FallbackPreviewGlyph
    }

  // -- Skin tones ----------------------------------------------------------------------------------------------

  private def skinToneSelectorIn(position: SkinTonePosition): Modifier[HtmlElement] =
    if (config.skinTonePosition == position) skinToneSelector else emptyNode

  private def skinToneSelector: HtmlDiv =
    div(
      cls("shrink-0"),
      child <-- skinPickerOpenVar.signal.combineWith(skinToneStore.current).map {
        case (true, current) =>
          div(cls("flex items-center gap-0.5"), SkinTone.values.map(tone => toneButton(tone, current)))
        case (false, current) =>
          div(
            cls("flex items-center"),
            button(
              `type`("button"),
              cls(toneButtonCls),
              aria.label := s"Skin tone: ${current.label}",
              title := s"Skin tone: ${current.label}",
              onClick.stopPropagation.mapToUnit --> (_ => skinPickerOpenVar.set(true)),
              current.swatch
            )
          )
      }
    )

  private def toneButton(tone: SkinTone, current: SkinTone): HtmlButton =
    button(
      `type`("button"),
      cls(toneButtonCls),
      cls(if (tone == current) "ring-2 ring-brand" else ""),
      aria.label := s"${tone.label} skin tone",
      title := tone.label,
      onClick.stopPropagation.mapToUnit --> (_ => {
        skinToneStore.set(tone)
        skinPickerOpenVar.set(false)
      }),
      tone.swatch
    )

  // -- Keyboard ------------------------------------------------------------------------------------------------

  private def handleKeyDown(event: KeyboardEvent): Unit =
    event.key match {
      case "Escape"     => onClickOutside.onNext(())
      case "ArrowRight" => moveFocus(1, event)
      case "ArrowLeft"  => moveFocus(-1, event)
      case "ArrowDown"  => moveFocus(config.perLine, event)
      case "ArrowUp"    => moveFocus(-config.perLine, event)
      case key if key.length == 1 && !searchIsFocused =>
        event.preventDefault()
        queryVar.update(_ + key)
        searchInputElement.ref.focus()
      case _ => ()
    }

  private def searchIsFocused: Boolean =
    dom.document.activeElement eq searchInputElement.ref

  private def moveFocus(delta: Int, event: KeyboardEvent): Unit =
    gridElement.foreach { grid =>
      val buttons = grid.querySelectorAll("button[data-emoji-id]")
      if (buttons.length > 0) {
        event.preventDefault()
        val active = dom.document.activeElement
        val currentIndex = (0 until buttons.length).indexWhere(index => buttons(index) eq active)
        val nextIndex =
          if (currentIndex < 0) 0
          else math.max(0, math.min(buttons.length - 1, currentIndex + delta))
        buttons(nextIndex).asInstanceOf[dom.HTMLElement].focus()
      }
    }

}

object TgEmojiPicker {

  private val FallbackPreviewGlyph = "👆"
  private val FallbackNoResultsGlyph = "😢"

  private case class EmojiSection(categoryId: Option[EmojiCategoryId], label: String, emojis: Vector[Emoji])

  private case class RenderState(
    state: EmojiDataState,
    sections: Vector[EmojiSection],
    tone: SkinTone,
    searching: Boolean
  )

  /** Building the index over ~1900 emoji is not free, and it only depends on the dataset. */
  private var cachedIndex: Option[(EmojiDataSet, EmojiSearchIndex)] = None

  private def searchIndexFor(dataSet: EmojiDataSet): EmojiSearchIndex =
    cachedIndex match {
      case Some((cached, index)) if cached eq dataSet => index
      case _ =>
        val index = EmojiSearchIndex(dataSet)
        cachedIndex = Some(dataSet -> index)
        index
    }

  private val panelCls =
    "flex flex-col overflow-hidden bg-neutral-primary border border-default-medium rounded-base shadow-lg"

  private val navBarCls = "flex shrink-0 border-default-medium px-1"

  private val navButtonCls =
    "flex flex-1 cursor-pointer items-center justify-center border-b-2 py-2 focus:outline-none focus:ring-2 focus:ring-brand"

  private val searchRowCls = "flex shrink-0 items-center gap-2 px-2 py-2"

  private val searchInputCls =
    "block w-full rounded-base border border-default-medium bg-neutral-secondary-medium py-2 pe-8 ps-9 text-sm " +
      "text-heading shadow-xs placeholder:text-body focus:border-brand focus:ring-brand"

  private val gridAreaCls = "relative flex-1 overflow-y-auto px-2 pb-2"

  private val sectionHeaderCls =
    "sticky top-0 z-10 bg-neutral-primary py-1 text-xs font-medium uppercase tracking-wide text-body"

  private val emojiButtonCls =
    "flex cursor-pointer items-center justify-center rounded-base hover:bg-neutral-tertiary-medium " +
      "focus:outline-none focus:ring-2 focus:ring-brand"

  private val previewBarCls = "flex shrink-0 items-center gap-2 border-default-medium px-2 py-2"

  private val toneButtonCls =
    "flex h-7 w-7 cursor-pointer items-center justify-center rounded-base text-base " +
      "hover:bg-neutral-tertiary-medium focus:outline-none focus:ring-2 focus:ring-brand"

  private val retryButtonCls =
    "rounded-base border border-default-medium bg-neutral-secondary-medium px-3 py-1.5 text-sm font-medium " +
      "text-heading shadow-xs hover:bg-neutral-tertiary-medium focus:outline-none focus:ring-2 focus:ring-brand"

}
