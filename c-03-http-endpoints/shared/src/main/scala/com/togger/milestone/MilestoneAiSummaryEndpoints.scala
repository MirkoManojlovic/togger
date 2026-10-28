package com.togger.milestone

/** The streamed summary is a server-sent events response, so the backend declares its endpoint; this is what the
  * frontend, which reads the stream itself, shares with it.
  */
object MilestoneAiSummaryStreamContract {
  val PathSegments: List[String] = List("milestone-ai-summary", "stream")

  /** The data of a chunk event is a JSON string with the next piece of the summary. */
  val ChunkEvent = "chunk"

  /** The summary is complete; the data is empty. */
  val DoneEvent = "done"

  /** The stream broke off; the data is a message for the user. */
  val ErrorEvent = "error"
}
