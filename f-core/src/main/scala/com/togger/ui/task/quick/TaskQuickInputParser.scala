package com.togger.ui.task.quick

sealed trait QuickDropdown

object QuickDropdown {
  case object ProjectDropdown extends QuickDropdown
  case object LabelsDropdown extends QuickDropdown
  case object PriorityDropdown extends QuickDropdown
  case object TaskStatusDropdown extends QuickDropdown
  case object AssignedToDropdown extends QuickDropdown

  val triggerChars: Map[Char, QuickDropdown] =
    Map(
      '~' -> ProjectDropdown,
      '#' -> LabelsDropdown,
      '!' -> PriorityDropdown,
      '$' -> TaskStatusDropdown,
      '%' -> AssignedToDropdown
    )
}

/** @param start
  *   index of the trigger char in the raw input
  * @param end
  *   index just after the last char of the token
  */
case class QuickToken(dropdown: QuickDropdown, query: String, start: Int, end: Int)

case class ParsedQuickInput(taskName: String, tokens: Seq[QuickToken]) {
  def dropdowns: Seq[QuickDropdown] = tokens.map(_.dropdown)
}

object TaskQuickInputParser {

  def parse(raw: String): ParsedQuickInput = {
    val found = tokens(raw)
    ParsedQuickInput(taskName = removeTokens(raw, found), tokens = found)
  }

  /** The token the caret is currently typing in, if any; a caret right after the token counts as inside. */
  def activeTokenAt(raw: String, caret: Int): Option[QuickToken] =
    tokens(raw).find(token => caret > token.start && caret <= token.end)

  def removeTokens(raw: String, tokens: Seq[QuickToken]): String = {
    val withoutTokens = tokens.foldRight(raw)((token, acc) => acc.patch(token.start, "", token.end - token.start))
    withoutTokens.trim.replaceAll("\\s+", " ")
  }

  private def tokens(raw: String): Seq[QuickToken] = {
    def isTokenStart(index: Int): Boolean =
      QuickDropdown.triggerChars.contains(raw(index)) && (index == 0 || raw(index - 1).isWhitespace)

    def tokenEnd(start: Int): Int = {
      val whitespaceIndex = raw.indexWhere(_.isWhitespace, start)
      if (whitespaceIndex == -1) raw.length else whitespaceIndex
    }

    raw.indices.filter(isTokenStart).map { start =>
      val end = tokenEnd(start)
      QuickToken(QuickDropdown.triggerChars(raw(start)), raw.substring(start + 1, end), start, end)
    }
  }
}
