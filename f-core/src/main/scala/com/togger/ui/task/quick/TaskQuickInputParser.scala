package com.togger.ui.task.quick

sealed trait QuickDropdown

object QuickDropdown {
  case object ProjectDropdown extends QuickDropdown
  case object LabelsDropdown extends QuickDropdown
  case object PriorityDropdown extends QuickDropdown
  case object TaskStatusDropdown extends QuickDropdown
  case object AssignedToDropdown extends QuickDropdown
  case object MilestoneDropdown extends QuickDropdown
  case object StartDateDropdown extends QuickDropdown
  case object DueDateDropdown extends QuickDropdown

  val dateDropdowns: Set[QuickDropdown] = Set(StartDateDropdown, DueDateDropdown)

  val triggerChars: Map[Char, QuickDropdown] =
    Map(
      '~' -> ProjectDropdown,
      '#' -> LabelsDropdown,
      '!' -> PriorityDropdown,
      '$' -> TaskStatusDropdown,
      '%' -> AssignedToDropdown,
      '-' -> MilestoneDropdown
    )

  /** Whole-word triggers: unlike [[triggerChars]] they take no query, so `@sprint` is plain text, not a start date. */
  val triggerWords: Map[String, QuickDropdown] =
    Map("@s" -> StartDateDropdown, "@d" -> DueDateDropdown)
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
    def tokenEnd(start: Int): Int = {
      val whitespaceIndex = raw.indexWhere(_.isWhitespace, start)
      if (whitespaceIndex == -1) raw.length else whitespaceIndex
    }

    def isWordStart(index: Int): Boolean = index == 0 || raw(index - 1).isWhitespace

    def tokenAt(start: Int): Option[QuickToken] = {
      val end = tokenEnd(start)
      val word = raw.substring(start, end)
      QuickDropdown.triggerWords
        .get(word)
        .map(QuickToken(_, "", start, end))
        .orElse(QuickDropdown.triggerChars.get(raw(start)).map(QuickToken(_, word.tail, start, end)))
    }

    raw.indices.filter(isWordStart).flatMap(tokenAt)
  }
}
