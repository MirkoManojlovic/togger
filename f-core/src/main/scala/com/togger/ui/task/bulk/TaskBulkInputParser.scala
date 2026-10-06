package com.togger.ui.task.bulk

sealed trait BulkParseIssueReason

object BulkParseIssueReason {
  case object MissingPrefix extends BulkParseIssueReason
  case object EmptyName extends BulkParseIssueReason
}

/** @param lineNumber
  *   1-based line in the raw input
  */
case class ParsedBulkLine(lineNumber: Int, name: String)

case class BulkParseIssue(lineNumber: Int, reason: BulkParseIssueReason)

case class ParsedBulkInput(lines: Seq[ParsedBulkLine], issues: Seq[BulkParseIssue]) {
  def hasIssues: Boolean = issues.nonEmpty
}

object TaskBulkInputParser {

  private val Prefix = "-"

  def parse(raw: String): ParsedBulkInput = {
    val results = raw.linesIterator.zipWithIndex.flatMap { case (line, index) => parseLine(line.trim, index + 1) }.toSeq
    ParsedBulkInput(
      lines = results.collect { case Right(line) => line },
      issues = results.collect { case Left(issue) => issue }
    )
  }

  /** Writes the task names back over the lines they were parsed from, in the given order. Blank and invalid lines stay
    * where they are, so reordering never eats what the user typed.
    */
  def applyOrder(raw: String, orderedNames: Seq[String]): String = {
    val parsedLines = parse(raw).lines
    if (parsedLines.size != orderedNames.size) return raw
    val rawLines = raw.linesIterator.toVector
    val reordered = parsedLines.zip(orderedNames).foldLeft(rawLines) { case (lines, (parsed, name)) =>
      lines.updated(parsed.lineNumber - 1, s"$Prefix $name")
    }
    reordered.mkString("\n") + (if (raw.endsWith("\n")) "\n" else "")
  }

  private def parseLine(line: String, lineNumber: Int): Option[Either[BulkParseIssue, ParsedBulkLine]] = {
    if (line.isEmpty) return None
    if (!hasPrefix(line)) return Some(Left(BulkParseIssue(lineNumber, BulkParseIssueReason.MissingPrefix)))
    val name = line.stripPrefix(Prefix).trim.replaceAll("\\s+", " ")
    if (name.isEmpty) return Some(Left(BulkParseIssue(lineNumber, BulkParseIssueReason.EmptyName)))
    Some(Right(ParsedBulkLine(lineNumber, name)))
  }

  /** The prefix is `-` followed by whitespace or nothing, so `-v2` is not a task. */
  private def hasPrefix(line: String): Boolean =
    line == Prefix || (line.startsWith(Prefix) && line(Prefix.length).isWhitespace)
}
