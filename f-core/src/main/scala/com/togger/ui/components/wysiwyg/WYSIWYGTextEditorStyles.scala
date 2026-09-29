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

  /** Side by side from `md` up, stacked under it, where [[splitTabBar]] decides which pane is visible. */
  val splitPanes = "flex flex-col md:flex-row items-stretch"

  val splitPaneSource = "md:w-1/2 md:border-e border-default"

  val splitPanePreview = "md:w-1/2"

  /** `resize-none` because the pane owns the height; the textarea's own grip would desynchronise the two sides. */
  val splitSource =
    "w-full h-full p-3 text-sm font-mono text-body bg-transparent resize-none " +
      "border-0 focus:outline-none focus:ring-0"

  val splitPreview = "p-3 overflow-y-auto break-words"

  val splitPreviewEmpty = "text-sm text-body opacity-60 select-none"

  /** Only below `md` — above it both panes are on screen and the tabs have nothing to switch. */
  val splitTabBar = "flex md:hidden border-b border-default text-sm font-medium text-center text-body"

  val splitTab =
    "flex-1 p-2 border-b cursor-pointer bg-transparent focus:outline-none focus-visible:ring-2 " +
      "focus-visible:ring-brand-subtle"

  val splitTabActive = "text-fg-brand border-brand"

  val splitTabInactive = "border-transparent hover:text-fg-brand"

  /** Hides a pane on narrow screens only; the pane's own `md:*` width class wins the layout back above `md`. */
  val splitPaneHiddenNarrow = "hidden md:block"

  val splitPaneShown = "block"
}
