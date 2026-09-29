package com.togger.ui.components.wysiwyg

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/** A textarea's value and selection — the only state a toolbar command over markdown source needs.
  *
  * A collapsed selection (`start == end`) is a plain caret.
  */
case class TextSelection(text: String, start: Int, end: Int) {

  def collapsed: Boolean = start == end

  def selected: String = text.substring(start, end)
}

/** Toolbar operations for [[WYSIWYGTextEditorSplit]], as pure transforms of text plus selection.
  *
  * The markdown string is the model there, so a command is string editing rather than the DOM surgery
  * [[WYSIWYGCommands]] performs for [[WYSIWYGTextEditorLive]]. Keeping the DOM out of the signatures is what lets
  * the whole set be tested without a fixture.
  *
  * Two rules shape every command: the returned selection covers the same *text* it did before, never the markers
  * around it, so clicking a button twice undoes it; and a line command works on whole lines, expanding the
  * selection to line boundaries first.
  */
object WYSIWYGMarkdownCommands {

  private val listMarker: Regex = """^(?:[-*+] |\d+\. )""".r
  private val bulletMarker: Regex = """^[-*+] """.r
  private val orderedMarker: Regex = """^\d+\. """.r
  private val headingMarker: Regex = """^#{1,6} """.r
  private val quoteMarker: Regex = """^> """.r

  /** `**x**`, `*x*`, `` `x` ``, `~~x~~`.
    *
    * The selection is unwrapped when it already carries the markers — whether they fall just inside it or just
    * outside — and wrapped otherwise. A caret with nothing selected gets the pair inserted around it, so clicking
    * the button and typing produces a marked-up word.
    */
  def toggleInline(selection: TextSelection, marker: String): TextSelection = {
    val current = normalised(selection)

    if (markersInside(current, marker)) removeMarkers(current, marker, current.start, current.end - marker.length)
    else
      enclosingSpan(current, marker) match {
        case Some((open, close))       => removeMarkers(current, marker, open, close)
        case None if current.collapsed => insertPair(current, marker)
        case None                      => wrap(current, marker)
      }
  }

  /** The author selected the marked-up run itself — `{**bold**}` rather than `**{bold}**`. */
  private def markersInside(selection: TextSelection, marker: String): Boolean = {
    val selected = selection.selected

    selected.length >= 2 * marker.length && selected.startsWith(marker) && selected.endsWith(marker)
  }

  /** `## `, `> ` — set on every line the selection touches, removed when every one already carries exactly it.
    *
    * The prefix replaces others of its own kind rather than stacking: `## ` on a line starting `### ` swaps the
    * level instead of producing `## ### `.
    */
  def togglePrefix(selection: TextSelection, prefix: String): TextSelection =
    rewriteLines(normalised(selection)) { lines =>
      val stripped = lines.map(line => family(prefix).replaceFirstIn(line, ""))

      if (lines.forall(_.startsWith(prefix))) stripped else stripped.map(prefix + _)
    }

  /** `- ` or `1. ` per line, renumbering an ordered list from one as it goes. */
  def toggleList(selection: TextSelection, ordered: Boolean): TextSelection =
    rewriteLines(normalised(selection)) { lines =>
      val marker = if (ordered) orderedMarker else bulletMarker
      val stripped = lines.map(line => listMarker.replaceFirstIn(line, ""))

      if (lines.forall(line => marker.findFirstIn(line).isDefined)) stripped
      else stripped.zipWithIndex.map { case (line, index) => listPrefix(ordered, index) + line }
    }

  /** `[text](destination)`, leaving the label selected — or the caret inside empty brackets when nothing was. */
  def insertLink(selection: TextSelection, destination: String): TextSelection = {
    val current = normalised(selection)
    val label = current.selected
    val link = s"[$label]($destination)"

    TextSelection(
      current.text.take(current.start) + link + current.text.drop(current.end),
      current.start + 1,
      current.start + 1 + label.length
    )
  }

  /** What the selection currently sits inside, keyed the way the toolbar's buttons are.
    *
    * Headings report `h2` at every level, because the toolbar has one heading button — the same key
    * [[WYSIWYGTextEditorLive]]'s toolbar uses.
    */
  def activeMarks(selection: TextSelection): Set[String] = {
    val current = normalised(selection)
    val line = lineAt(current)

    val inline = Seq("strong" -> "**", "em" -> "*", "code" -> "`", "del" -> "~~").collect {
      case (name, marker) if enclosingSpan(current, marker).isDefined => name
    }

    val block = Seq("h2" -> headingMarker, "ul" -> bulletMarker, "ol" -> orderedMarker, "blockquote" -> quoteMarker)
      .collect { case (name, marker) if marker.findFirstIn(line).isDefined => name }

    (inline ++ block).toSet
  }

  private def listPrefix(ordered: Boolean, index: Int): String = if (ordered) s"${index + 1}. " else "- "

  /** A prefix replaces others of its own kind, so `## ` has to recognise `# ` through `###### `. */
  private def family(prefix: String): Regex =
    if (prefix.startsWith("#")) headingMarker
    else if (prefix.startsWith(">")) quoteMarker
    else ("^" + Regex.quote(prefix)).r

  /** A selection out of step with the text would throw in `substring`; a textarea never sends one, but a command
    * is a pure function and should not depend on that.
    */
  private def normalised(selection: TextSelection): TextSelection = {
    val start = clamp(selection.start, 0, selection.text.length)

    TextSelection(selection.text, start, clamp(selection.end, start, selection.text.length))
  }

  private def clamp(value: Int, low: Int, high: Int): Int = math.max(low, math.min(value, high))

  private def lineStart(text: String, at: Int): Int = text.lastIndexOf('\n', at - 1) + 1

  private def lineEnd(text: String, at: Int): Int = {
    val break = text.indexOf('\n', at)

    if (break < 0) text.length else break
  }

  private def lineAt(selection: TextSelection): String =
    selection.text.substring(lineStart(selection.text, selection.start), lineEnd(selection.text, selection.start))

  /** The marker pair enclosing the selection, as absolute positions of the two delimiters.
    *
    * Only a selection inside one line can be enclosed — an inline mark does not span a blank line, and pairing
    * delimiters across the whole document would match markers in unrelated paragraphs.
    */
  private def enclosingSpan(selection: TextSelection, marker: String): Option[(Int, Int)] = {
    val from = lineStart(selection.text, selection.start)

    if (from != lineStart(selection.text, selection.end)) None
    else
      spansOf(selection.text.substring(from, lineEnd(selection.text, selection.start)), marker)
        .map { case (open, close) => (from + open, from + close) }
        .find { case (open, close) => selection.start >= open + marker.length && selection.end <= close }
  }

  private def spansOf(line: String, marker: String): Seq[(Int, Int)] =
    delimiters(line, marker).grouped(2).collect { case Seq(open, close) => (open, close) }.toSeq

  /** `**` is skipped while looking for `*`, so a caret inside `**bold**` reports strong and not emphasis. */
  private def delimiters(line: String, marker: String): Seq[Int] = {
    val found = ListBuffer.empty[Int]
    var index = 0

    while (index < line.length)
      if (marker == "*" && line.startsWith("**", index)) index += 2
      else if (line.startsWith(marker, index)) {
        found += index
        index += marker.length
      } else index += 1

    found.toSeq
  }

  private def removeMarkers(selection: TextSelection, marker: String, open: Int, close: Int): TextSelection = {
    val width = marker.length
    val text =
      selection.text.take(open) + selection.text.slice(open + width, close) + selection.text.drop(close + width)

    TextSelection(
      text,
      clamp(selection.start - width, open, close - width),
      clamp(selection.end - width, open, close - width)
    )
  }

  private def insertPair(selection: TextSelection, marker: String): TextSelection =
    TextSelection(
      selection.text.patch(selection.start, marker + marker, 0),
      selection.start + marker.length,
      selection.start + marker.length
    )

  private def wrap(selection: TextSelection, marker: String): TextSelection =
    TextSelection(
      selection.text.take(selection.start) + marker + selection.selected + marker +
        selection.text.drop(selection.end),
      selection.start + marker.length,
      selection.end + marker.length
    )

  /** Runs a line rewrite over the block the selection touches and puts the selection back where it was, shifted by
    * what the rewrite added or removed ahead of it — replacing it with the whole block would make a heading button
    * swallow the caret every time it is pressed.
    *
    * A caret moves past a marker the rewrite just added, so that clicking *Heading* on an empty line and typing
    * puts the text in the heading. A selection that already started at the line does not, so that it keeps
    * covering the lines it covered and a second click toggles them back.
    */
  private def rewriteLines(selection: TextSelection)(rewrite: Seq[String] => Seq[String]): TextSelection = {
    val from = lineStart(selection.text, selection.start)
    val to = lineEnd(selection.text, selection.end)
    val lines = selection.text.substring(from, to).split("\n", -1).toSeq
    val rewritten = rewrite(lines)
    val block = rewritten.mkString("\n")
    val text = selection.text.take(from) + block + selection.text.drop(to)
    val firstDelta = rewritten.headOption.map(_.length).getOrElse(0) - lines.headOption.map(_.length).getOrElse(0)
    val blockDelta = block.length - (to - from)
    val blockEnd = from + block.length
    val end = clamp(selection.end + blockDelta, from, blockEnd)
    val start =
      if (selection.collapsed || selection.start > from) clamp(selection.start + firstDelta, from, end) else from

    TextSelection(text, start, end)
  }
}
