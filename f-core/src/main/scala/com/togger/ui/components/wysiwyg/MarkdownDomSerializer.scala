package com.togger.ui.components.wysiwyg

import org.scalajs.dom

/** Turns an edited DOM tree back into markdown.
  *
  * The counterpart to `TgMarkdownRender`, which the markdown library does not provide — it parses and renders but
  * never serializes. A pure function of a DOM node, so it is testable on its own.
  *
  * It is tag-driven rather than annotation-driven: everything it sees came either from `TgMarkdownRender`, whose
  * tags are unambiguous, or from the browser's own contenteditable handling. Unknown elements are unwrapped, which
  * turns a browser-inserted `<span>`, a `<font>` from a paste, or a stray `<div>` into their text instead of
  * garbage.
  *
  * Output is canonical, not a reproduction of whatever produced the DOM: ATX headings, `-` bullets, `*` emphasis,
  * fenced code. So `serialize(render(md)) != md` in general, but running it again over its own output is stable —
  * see `MarkdownRoundTripTest`.
  */
object MarkdownDomSerializer {

  def serialize(root: dom.Node): String = {
    val body = childBlocks(root).mkString("\n\n").trim

    if (body.isEmpty) "" else body + "\n"
  }

  /** Elements that hold other blocks rather than inline content. Used to decide whether a container should be
    * recursed into as blocks or flattened as a paragraph — the question a browser-inserted `<div>` raises.
    */
  private val blockTags =
    Set(
      "address", "article", "aside", "blockquote", "dd", "details", "div", "dl", "dt", "figure", "footer", "form",
      "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section",
      "summary", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul"
    )

  private val headingLevels = Map("h1" -> 1, "h2" -> 2, "h3" -> 3, "h4" -> 4, "h5" -> 5, "h6" -> 6)

  private def tagOf(node: dom.Node): String = node match {
    case e: dom.Element => e.tagName.toLowerCase
    case _              => ""
  }

  private def children(node: dom.Node): Seq[dom.Node] = {
    val nodes = node.childNodes

    (0 until nodes.length).map(nodes(_))
  }

  private def hasBlockChildren(node: dom.Node): Boolean =
    children(node).exists(child => blockTags.contains(tagOf(child)))

  private def childBlocks(parent: dom.Node): Seq[String] = children(parent).flatMap(block)

  private def block(node: dom.Node): Option[String] = node match {
    case text: dom.Text => Some(escapeBlockStart(escapeText(text.data))).filter(_.trim.nonEmpty)
    case e: dom.Element => elementBlock(e)
    case _              => None
  }

  private def elementBlock(e: dom.Element): Option[String] = tagOf(e) match {
    case tag if headingLevels.contains(tag) =>
      val text = inline(e).trim

      Some(("#" * headingLevels(tag)) + " " + text).filter(_ => text.nonEmpty)

    case "p"          => Some(escapeBlockStart(inline(e))).filter(_.trim.nonEmpty)
    case "ul"         => Some(list(e, ordered = false)).filter(_.nonEmpty)
    case "ol"         => Some(list(e, ordered = true)).filter(_.nonEmpty)
    case "li"         => listItemBlock(e)
    case "blockquote" => Some(prefixLines(childBlocks(e).mkString("\n\n"), "> ")).filter(_.trim.nonEmpty)
    case "pre"        => Some(codeFence(e))
    case "hr"         => Some("---")
    case "table"      => Some(table(e)).filter(_.nonEmpty)
    case "dl"         => Some(definitionList(e)).filter(_.nonEmpty)
    case "br"         => None
    case _            => container(e)
  }

  /** A `<div>` the browser inserted on Enter holds inline content and is a paragraph; one from the markdown
    * renderer (a table wrapper, a raw-HTML block, a callout) holds blocks and is unwrapped.
    */
  private def container(e: dom.Element): Option[String] =
    if (hasBlockChildren(e)) Some(childBlocks(e).mkString("\n\n")).filter(_.trim.nonEmpty)
    else Some(escapeBlockStart(inline(e))).filter(_.trim.nonEmpty)

  /** An `<li>` reached on its own rather than through its list — a paste, or a browser leaving one behind. */
  private def listItemBlock(item: dom.Element): Option[String] =
    Some(listItemBody(item)).filter(_.trim.nonEmpty).map(body => withMarker("- ", taskMarker(item) + body))

  private def list(e: dom.Element, ordered: Boolean): String = {
    val start = if (ordered) Option(e.getAttribute("start")).flatMap(_.toIntOption).getOrElse(1) else 1

    children(e)
      .collect { case item: dom.Element if tagOf(item) == "li" => item }
      .map(item => (taskMarker(item), listItemBody(item)))
      // An item with no content would serialize to a bare marker, which the parser chokes on when the document
      // comes back through it — and an empty bullet carries nothing anyway. The author gets one while typing a
      // new item; dropping it is invisible to them because their own edits never trigger a repaint.
      //
      // The test runs on the body alone: a checkbox is not content, and prefixing `[ ] ` first would keep every
      // empty task item alive and put the bare marker back.
      .filter { case (_, body) => body.trim.nonEmpty }
      .zipWithIndex
      .map { case ((task, body), index) =>
        withMarker(if (ordered) s"${start + index}. " else "- ", task + body)
      }
      .mkString("\n")
  }

  /** `- [x] ` for an item whose first child is a checkbox, so a task list survives the editor's round trip. The
    * *attribute* is what the renderer set; the property is whatever anyone has since clicked, and this is a
    * function of the rendered document rather than of that.
    */
  private def taskMarker(item: dom.Element): String =
    children(item).headOption
      .collect { case e: dom.Element if tagOf(e) == "input" && e.getAttribute("type") == "checkbox" => e }
      .map(e => if (e.hasAttribute("checked")) "[x] " else "[ ] ")
      .getOrElse("")

  /** A nested list joins its parent item with a single newline — a blank line there would make the whole list
    * loose, which changes how every item renders.
    */
  private def listItemBody(item: dom.Element): String =
    if (!hasBlockChildren(item)) inline(item).trim
    else
      children(item)
        .flatMap(child => block(child).map(text => (tagOf(child) == "ul" || tagOf(child) == "ol", text)))
        .foldLeft("") {
          case ("", (_, text))          => text
          case (acc, (true, text))      => acc + "\n" + text
          case (acc, (false, text))     => acc + "\n\n" + text
        }

  private def withMarker(marker: String, body: String): String = {
    val padding = " " * marker.length

    body.linesIterator.zipWithIndex
      .map {
        case (line, 0)                   => marker + line
        case (line, _) if line.isEmpty   => line
        case (line, _)                   => padding + line
      }
      .mkString("\n")
  }

  private def prefixLines(body: String, prefix: String): String =
    body.linesIterator.map(line => if (line.isEmpty) prefix.trim else prefix + line).mkString("\n")

  private def codeFence(pre: dom.Element): String = {
    val codeElement = children(pre).collectFirst { case e: dom.Element if tagOf(e) == "code" => e }
    val language = codeElement.flatMap(languageOf).getOrElse("")
    val body = withPlainSpaces(codeElement.map(_.textContent).getOrElse(pre.textContent)).stripSuffix("\n")
    val backticks = "`" * math.max(3, longestRun(body, '`') + 1)

    s"$backticks$language\n$body\n$backticks"
  }

  private def languageOf(e: dom.Element): Option[String] =
    Option(e.getAttribute("class")).toSeq
      .flatMap(_.split("\\s+"))
      .collectFirst { case name if name.startsWith("language-") => name.drop("language-".length) }

  private def table(e: dom.Element): String = {
    val rows = descendants(e, "tr").map(row => descendants(row, "th") ++ descendants(row, "td"))

    if (rows.isEmpty) ""
    else {
      val header = rows.head
      val alignments = header.map(alignmentOf)
      val separator = alignments.map {
        case "left"   => ":---"
        case "center" => ":---:"
        case "right"  => "---:"
        case _        => "---"
      }

      (Seq(header.map(cell => inline(cell).trim)) ++ Seq(separator) ++ rows.tail.map(_.map(cell =>
        inline(cell).trim
      )))
        .map(cells => cells.mkString("| ", " | ", " |"))
        .mkString("\n")
    }
  }

  private def alignmentOf(cell: dom.Element): String = cell match {
    case html: dom.html.Element => html.style.textAlign
    case _                      => ""
  }

  private def definitionList(e: dom.Element): String =
    children(e)
      .collect { case child: dom.Element => child }
      .flatMap { child =>
        tagOf(child) match {
          case "dt" => Some(inline(child).trim)
          case "dd" => Some(": " + inline(child).trim)
          case _    => None
        }
      }
      .mkString("\n")

  private def descendants(root: dom.Node, tag: String): Seq[dom.Element] = root match {
    case e: dom.Element =>
      val found = e.getElementsByTagName(tag)

      (0 until found.length).map(found(_)).filter(_.parentNode == e || tag == "tr")
    case _ => Nil
  }

  private def inline(node: dom.Node): String = children(node).map(inlineNode).mkString

  private def inlineNode(node: dom.Node): String = node match {
    case text: dom.Text => escapeText(text.data)
    case e: dom.Element =>
      tagOf(e) match {
        case "strong" | "b"          => wrap(inline(e), "**")
        case "em" | "i"              => wrap(inline(e), "*")
        case "del" | "s" | "strike"  => wrap(inline(e), "~~")
        case "code"                  => codeSpan(withPlainSpaces(e.textContent))
        case "a"                     => link(e)
        case "img"                   => image(e)
        case "br"                    => "  \n"
        case "sup"                   => footnoteReference(e).getOrElse(inline(e))
        case _                       => inline(e)
      }
    case _ => ""
  }

  /** `** bold **` is not emphasis, so surrounding whitespace moves outside the markers. */
  private def wrap(content: String, marker: String): String =
    if (content.trim.isEmpty) content
    else {
      val leading = content.takeWhile(_.isWhitespace)
      val trailing = content.reverse.takeWhile(_.isWhitespace).reverse
      val core = content.substring(leading.length, content.length - trailing.length)

      leading + marker + core + marker + trailing
    }

  /** Backticks in the content are escaped by lengthening the fence, the only way markdown has. */
  private def codeSpan(content: String): String = {
    val fence = "`" * (longestRun(content, '`') + 1)
    val padding = if (content.startsWith("`") || content.endsWith("`")) " " else ""

    s"$fence$padding$content$padding$fence"
  }

  private def link(e: dom.Element): String = {
    val text = inline(e)
    val destination = Option(e.getAttribute("href")).getOrElse("")
    val title = Option(e.getAttribute("title")).filter(_.nonEmpty).map(t => s""" "$t"""").getOrElse("")

    s"[$text](${destinationOf(destination)}$title)"
  }

  private def image(e: dom.Element): String = {
    val altText = Option(e.getAttribute("alt")).getOrElse("")
    val destination = Option(e.getAttribute("src")).getOrElse("")
    val title = Option(e.getAttribute("title")).filter(_.nonEmpty).map(t => s""" "$t"""").getOrElse("")

    s"![$altText](${destinationOf(destination)}$title)"
  }

  /** A destination with spaces or unbalanced parens only parses inside angle brackets. */
  private def destinationOf(destination: String): String =
    if (destination.isEmpty) destination
    else if (destination.exists(_.isWhitespace) || destination.contains("(") || destination.contains(")"))
      s"<$destination>"
    else destination

  private def footnoteReference(e: dom.Element): Option[String] =
    children(e)
      .collectFirst { case anchor: dom.Element if tagOf(anchor) == "a" => anchor }
      .flatMap(anchor => Option(anchor.getAttribute("href")))
      .filter(_.startsWith("#fn-"))
      .map(href => s"[^${href.drop("#fn-".length)}]")

  /** contenteditable produces U+00A0 mechanically wherever a typed space would collapse, so one in the DOM says
    * nothing about intent. Markdown has no use for the distinction either — `-\u00a0item` is not a list — so the
    * two are flattened on the way out.
    */
  private def withPlainSpaces(text: String): String = text.replace('\u00a0', ' ')

  private def longestRun(text: String, char: Char): Int =
    text
      .foldLeft((0, 0)) {
        case ((longest, current), c) if c == char => (math.max(longest, current + 1), current + 1)
        case ((longest, _), _)                    => (longest, 0)
      }
      ._1

  /** Characters that would otherwise be read as syntax. Escaping too little is how a round trip quietly rewrites
    * someone's text, so this errs toward escaping — `&` and `<` only where they could start an entity or a tag,
    * which keeps ordinary prose readable.
    */
  private def escapeText(source: String): String = {
    val text = withPlainSpaces(source)
    val escaped = new StringBuilder

    text.zipWithIndex.foreach { case (char, index) =>
      val next = if (index + 1 < text.length) text.charAt(index + 1) else ' '

      char match {
        case '\\' | '`' | '*' | '_' | '[' | ']'          => escaped ++= s"\\$char"
        case '&' if next.isLetter || next == '#'         => escaped ++= "\\&"
        case '<' if next.isLetter || next == '/' || next == '!' || next == '?' => escaped ++= "\\<"
        case other                                       => escaped += other
      }
    }

    escaped.toString
  }

  private val blockStartMarkers = """^(\s*)([#>|]|[-+*] |\d+[.)] |={3,}$|-{3,}$)""".r

  /** A paragraph beginning with something that starts a block gets that one character escaped, so `# not a
    * heading` survives the trip.
    */
  private def escapeBlockStart(text: String): String =
    blockStartMarkers.findFirstMatchIn(text) match {
      case Some(matched) =>
        val indent = matched.group(1)
        val marker = matched.group(2)

        indent + "\\" + marker + text.drop(indent.length + marker.length)
      case None => text
    }
}
