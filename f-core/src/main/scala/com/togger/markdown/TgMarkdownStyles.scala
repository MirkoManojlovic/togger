package com.togger.markdown

/** Flowbite classes for each markdown node kind, as one overridable bag of class strings.
  *
  * Sizes follow the app rather than Flowbite's typography docs, which are scaled for marketing pages (`h1` is
  * `text-5xl` there). The tokens are Flowbite's — `text-heading`, `text-body`, `border-default` — so every value
  * swaps itself under `.dark` and none of them spells out a `dark:` class.
  *
  * Every string is a literal on purpose: Tailwind scans source text for whole class names, so a fragment built
  * with interpolation (`s"mb-$n"`) would generate no CSS at all — the same rule
  * [[com.togger.ui.components.TgBadgeColor]] documents.
  *
  * Block spacing rides on `mb-*` per block rather than `space-y-*` on a wrapper, because `TgMarkdown.toLaminar`
  * hands back a bare `Seq[HtmlElement]` — a caller splicing those into its own container would get no spacing
  * from a wrapper class that is not there.
  */
case class TgMarkdownStyles(
  heading1: String = "text-2xl font-bold text-heading mb-3",
  heading2: String = "text-xl font-bold text-heading mb-3",
  heading3: String = "text-lg font-semibold text-heading mb-2",
  heading4: String = "text-base font-semibold text-heading mb-2",
  heading5: String = "text-sm font-semibold text-heading mb-2",
  heading6: String = "text-sm font-medium text-heading mb-2",
  paragraph: String = "text-sm text-body mb-3",
  link: String = "font-medium text-fg-brand hover:underline",
  emphasis: String = "italic",
  strong: String = "font-semibold text-heading",
  strikethrough: String = "line-through",
  codeSpan: String = "px-1 py-0.5 text-xs font-mono rounded-xs bg-neutral-secondary-medium text-heading",
  codeBlock: String = "p-3 mb-3 overflow-x-auto rounded-base bg-neutral-secondary-soft border border-default",
  codeBlockCode: String = "text-xs font-mono text-body",
  blockQuote: String = "p-4 mb-3 border-s-4 border-default bg-neutral-secondary-soft text-sm italic text-heading",
  bulletList: String = "mb-3 space-y-1 text-sm text-body list-disc list-inside",
  orderedList: String = "mb-3 space-y-1 text-sm text-body list-decimal list-inside",
  nestedList: String = "ps-5 mt-2 space-y-1",
  listItem: String = "",
  thematicBreak: String = "h-px my-6 bg-neutral-quaternary border-0",
  tableWrapper: String =
    "relative overflow-x-auto mb-3 bg-neutral-primary-soft shadow-xs rounded-base border border-default",
  table: String = "w-full text-sm text-left rtl:text-right text-body",
  tableHead: String = "text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default",
  tableHeaderCell: String = "px-6 py-3 font-medium",
  tableRow: String = "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default",
  tableCell: String = "px-6 py-2",
  image: String = "h-auto max-w-full rounded-base",
  definitionList: String = "mb-3 text-sm text-body",
  definitionTerm: String = "font-semibold text-heading",
  definitionDescription: String = "ps-4 mb-2",
  calloutTitle: String = "font-semibold mb-1",
  collapsible: String = "mb-3 p-3 rounded-base border border-default bg-neutral-secondary-soft",
  collapsibleSummary: String = "text-sm font-medium text-heading cursor-pointer",
  footnote: String = "text-xs text-body",
  footnoteRef: String = "text-fg-brand hover:underline",
  docTag: String = "mb-3 text-sm text-body",
  rawHtmlBlock: String = "mb-3 text-sm text-body",
  fallback: String = "mb-3 text-sm text-body"
) {

  /** Levels past 6 cannot come out of the parser, but `heading6` is the sane clamp if one ever does. */
  def heading(level: Int): String = level match {
    case 1 => heading1
    case 2 => heading2
    case 3 => heading3
    case 4 => heading4
    case 5 => heading5
    case _ => heading6
  }

  /** Callout colors reuse the theme tokens `TgBadgeColor`'s semantic six use, so they flip under `.dark` without a
    * hand-written `dark:` class. An unrecognised kind falls back to the brand tokens rather than rendering unstyled.
    */
  def callout(kind: String): String = kind.toLowerCase match {
    case "warning" | "caution" =>
      "p-4 mb-3 border-s-4 rounded-base bg-warning-soft border-warning-subtle text-fg-warning"
    case "danger" | "error" =>
      "p-4 mb-3 border-s-4 rounded-base bg-danger-soft border-danger-subtle text-fg-danger-strong"
    case "success" | "tip" =>
      "p-4 mb-3 border-s-4 rounded-base bg-success-soft border-success-subtle text-fg-success-strong"
    case _ =>
      "p-4 mb-3 border-s-4 rounded-base bg-brand-softer border-brand-subtle text-fg-brand-strong"
  }
}

object TgMarkdownStyles {
  val default: TgMarkdownStyles = TgMarkdownStyles()
}
