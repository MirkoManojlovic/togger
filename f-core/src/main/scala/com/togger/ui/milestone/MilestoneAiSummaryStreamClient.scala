package com.togger.ui.milestone

import cats.effect.IO
import com.togger.backendcalls.{SseEvent, SseParser}
import com.togger.dto.SummariseMilestoneWithAiReq
import com.togger.logging.TgLogging
import com.togger.milestone.MilestoneAiSummaryStreamContract._
import com.togger.ui.auth.LoginRepo
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax._
import org.scalajs.dom
import org.scalajs.dom.{HttpMethod, RequestInit}

import scala.scalajs.js

sealed trait AiSummaryEvent
object AiSummaryEvent {
  case class Chunk(text: String) extends AiSummaryEvent
  case object Done extends AiSummaryEvent
  case class Failed(message: String) extends AiSummaryEvent
}

/** Reads the streamed AI summary with fetch: EventSource cannot send the bearer token, nor a POST body. */
class MilestoneAiSummaryStreamClient(loginRepo: LoginRepo, baseUrl: String) extends TgLogging {
  import AiSummaryEvent._

  private val url = baseUrl.stripSuffix("/") + "/" + PathSegments.mkString("/")

  /** Calls `onEvent` for every piece of the summary, then exactly once with [[Done]] or [[Failed]]. */
  def stream(dto: SummariseMilestoneWithAiReq)(onEvent: AiSummaryEvent => Unit): IO[Unit] =
    loginRepo.account match {
      case None => IO(onEvent(Failed("Please log in again")))
      case Some(account) =>
        IO.fromPromise(IO(dom.fetch(url, requestInit(dto, account.sessionId))))
          .flatMap(response => if (response.ok) read(response, onEvent) else IO(onEvent(Failed(failureMessage(response.status)))))
          .handleErrorWith { error =>
            IO(logger.error(s"The AI summary request failed: $error")) *> IO(onEvent(Failed("Could not reach the server")))
          }
    }

  private def requestInit(dto: SummariseMilestoneWithAiReq, token: String): RequestInit =
    new RequestInit {
      method = HttpMethod.POST
      headers = js.Dictionary("Authorization" -> s"Bearer $token", "Content-Type" -> "application/json")
      body = dto.asJson.noSpaces
    }

  private def failureMessage(status: Int): String =
    status match {
      case 403 => "You are not a member of this project"
      case 401 => "Please log in again"
      case 400 => "The AI could not summarise this period, check the dates and that it has log entries"
      case _   => s"The AI summary request failed ($status)"
    }

  private def read(response: dom.Response, onEvent: AiSummaryEvent => Unit): IO[Unit] = {
    val reader = response.body.getReader()
    // scalajs-dom has no TextDecoder; `stream` keeps a character split between two pieces intact
    val decoder = js.Dynamic.newInstance(js.Dynamic.global.TextDecoder)()
    val streaming = js.Dynamic.literal(stream = true)

    def loop(pending: String, finished: Boolean): IO[Unit] =
      IO.fromPromise(IO(reader.read())).flatMap { chunk =>
        if (chunk.done) IO.unlessA(finished)(IO(onEvent(Failed("The summary stopped before it was finished"))))
        else {
          val (rest, events) = SseParser.feed(pending, decoder.decode(chunk.value, streaming).asInstanceOf[String])
          val terminal = events.flatMap(toEvent).filter(e => e == Done || e.isInstanceOf[Failed])
          IO(events.flatMap(toEvent).foreach(onEvent)) *> loop(rest, finished || terminal.nonEmpty)
        }
      }

    loop("", finished = false)
  }

  private def toEvent(event: SseEvent): Option[AiSummaryEvent] =
    event.event match {
      case ChunkEvent => parse(event.data).toOption.flatMap(_.asString).map(Chunk)
      case DoneEvent  => Some(Done)
      case ErrorEvent => Some(Failed(event.data))
      case _          => None
    }
}
