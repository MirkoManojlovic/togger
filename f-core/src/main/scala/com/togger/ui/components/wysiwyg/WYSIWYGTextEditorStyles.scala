package com.togger.ui.components.wysiwyg

/** Classes for the editor's own frame only. What the author types *into* keeps coming from `TgMarkdownStyles`, so
  * the editing surface and the rendered entry look the same.
  *
  * Taken from `TgTabs`' button vocabulary and `Sidebar`'s icon buttons. `TgTextArea`'s classes are deliberately not
  * reused — they are raw `gray-*` palette with hand-written `dark:` pairs, predating the Flowbite 4 tokens.
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

  /** `relative` anchors the placeholder overlay; `break-words` stops a long unbroken paste widening the card. */
  val surfaceContainer = "relative"

  val surface = "p-3 overflow-y-auto break-words focus:outline-none"

  /** Not a DOM node inside the surface — anything in there would be serialized as content. */
  val placeholder = "absolute top-3 left-3 pointer-events-none select-none text-body opacity-60 text-sm"
}
