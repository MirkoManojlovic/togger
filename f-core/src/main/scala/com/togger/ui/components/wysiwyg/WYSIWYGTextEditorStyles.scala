package com.togger.ui.components.wysiwyg

/** Classes for [[WYSIWYGTextEditorSplit]]'s own frame only. What the author writes *about* keeps its look from
  * `TgMarkdownStyles`, so the preview pane and the rendered entry match.
  *
  * Taken from `TgTabs`' button vocabulary and `Sidebar`'s icon buttons. `TgTextArea`'s classes are deliberately not
  * reused — they are raw `gray-*` palette with hand-written `dark:` pairs, predating the Flowbite 4 tokens.
  *
  * Every value is a literal on purpose: Tailwind scans source text for whole class names, so a string built by
  * interpolation would generate no CSS — the same rule `TgMarkdownStyles` documents.
  */
object WYSIWYGTextEditorStyles {

  val label = "block mb-2 text-sm font-medium text-heading"

  val wrapper =
    "w-full rounded-base border border-default bg-neutral-primary-soft shadow-xs " +
      "focus-within:ring-2 focus-within:ring-brand-subtle"

  val toolbar = "flex flex-wrap items-center gap-1 p-1.5 border-b border-default bg-neutral-secondary-soft"

  val toolbarButton =
    "inline-flex items-center justify-center w-7 h-7 rounded-base text-body bg-transparent " +
      "hover:bg-neutral-tertiary hover:text-fg-brand focus:outline-none focus-visible:ring-2 " +
      "focus-visible:ring-brand-subtle cursor-pointer"

  val toolbarButtonActive = "bg-neutral-tertiary text-fg-brand"

  val toolbarSeparator = "w-px h-5 mx-1 bg-neutral-quaternary"

  val toolbarIcon = "w-4 h-4"

  /** Side by side while the editor has its `minWidthRem`, stacked once it is squeezed below it, where [[splitTabBar]]
    * decides which pane is visible.
    */
  val splitPanesSideBySide = "flex flex-row items-stretch"

  val splitPanesStacked = "flex flex-col items-stretch"

  val splitPaneSourceSideBySide = "flex flex-col w-1/2 border-e border-default"

  val splitPanePreviewSideBySide = "flex flex-col w-1/2"

  /** Takes up the `minWidthRem` the card is meant to grow to, without being the editor's own box — the editor can then
    * be measured at the width it is really given.
    */
  val widthReserve = "h-0 pointer-events-none invisible"

  /** `flex-1` rather than `h-full`: the pane has no definite height to take a percentage of, so the textarea would
    * shrink to its intrinsic rows and scroll on its own.
    *
    * `resize-none` because the pane owns the height; the textarea's own grip would desynchronise the two sides. */
  val splitSource =
    "w-full flex-1 p-3 text-sm font-mono text-body bg-transparent resize-none " +
      "border-0 focus:outline-none focus:ring-0"

  val splitPreview = "p-3 overflow-y-auto break-words"

  val splitPreviewEmpty = "text-sm text-body opacity-60 select-none"

  /** Only while stacked — side by side both panes are on screen and the tabs have nothing to switch. */
  val splitTabBar = "flex border-b border-default text-sm font-medium text-center text-body"

  val splitTab =
    "flex-1 p-2 border-b cursor-pointer bg-transparent focus:outline-none focus-visible:ring-2 " +
      "focus-visible:ring-brand-subtle"

  val splitTabActive = "text-fg-brand border-brand"

  val splitTabInactive = "border-transparent hover:text-fg-brand"

  /** Hides the pane a stacked viewer is not looking at. */
  val splitPaneHidden = "hidden"

  val splitPaneShown = "flex flex-col flex-1"
}
