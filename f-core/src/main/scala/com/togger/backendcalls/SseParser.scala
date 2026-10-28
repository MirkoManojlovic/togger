package com.togger.backendcalls

case class SseEvent(event: String, data: String)

/** Splits a server-sent events stream into events; the text arrives in arbitrary pieces. */
object SseParser {
  private val DefaultEvent = "message"

  /** Returns the text of the unfinished event, to be passed back in with the next piece, and the events completed. */
  def feed(pending: String, piece: String): (String, List[SseEvent]) = {
    val blocks = (pending + piece).replace("\r\n", "\n").split("\n\n", -1).toList
    (blocks.last, blocks.init.flatMap(parseBlock))
  }

  private def parseBlock(block: String): Option[SseEvent] = {
    val fields = block.split("\n").toList.filterNot(_.startsWith(":")).flatMap(parseField)
    val data = fields.collect { case ("data", value) => value }
    Option.when(data.nonEmpty || fields.exists(_._1 == "event")) {
      SseEvent(fields.collect { case ("event", value) => value }.lastOption.getOrElse(DefaultEvent), data.mkString("\n"))
    }
  }

  private def parseField(line: String): Option[(String, String)] =
    line.indexOf(':') match {
      case -1 => Option.when(line.nonEmpty)(line -> "")
      case i  => Some(line.take(i) -> line.drop(i + 1).stripPrefix(" "))
    }
}
