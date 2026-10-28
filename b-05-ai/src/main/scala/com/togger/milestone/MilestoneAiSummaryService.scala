package com.togger.milestone

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either._
import com.togger.ai._
import com.togger.common.TgError
import com.togger.logentry.{LogEntryRepo, LogEntryWithOneVersion}
import com.togger.logging.TgLogging
import com.togger.milestone.MilestoneAiSummaryError._
import com.togger.tgprompt.TgPromptService
import doobie.Transactor
import doobie.implicits._
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._

import java.time.{Instant, LocalDate}
import java.util.UUID

case class MilestoneAiSummaryCommand(
  milestoneId: UUID,
  promptId: Option[UUID],
  summarizePrompt: String,
  startDate: LocalDate,
  endDate: LocalDate,
  model: TgAiModel
)

class MilestoneAiSummaryService(
  logEntryRepo: LogEntryRepo,
  tgPromptService: TgPromptService,
  tgAiService: TgAiService,
  transactor: Transactor[IO]
) extends TgLogging {
  import MilestoneAiSummaryService._

  type Result[A] = Either[TgError, A]

  /** Everything that can be checked up front fails here; the stream carries the pieces of the summary as written. */
  def summariseStream(accountId: UUID, command: MilestoneAiSummaryCommand): IO[Result[fs2.Stream[IO, TgAiChunk]]] =
    (for {
      entries <- EitherT(findLogEntries(command))
      storedPrompt <- EitherT(findStoredPrompt(accountId, command.promptId))
      stream <- EitherT(chatStream(accountId, command, buildPrompt(storedPrompt, command.summarizePrompt, entries)))
    } yield stream).value

  private def findLogEntries(command: MilestoneAiSummaryCommand): IO[Result[List[LogEntryAiDto]]] =
    logEntryRepo
      .findLatestByMilestoneIdBetween(command.milestoneId, command.startDate, command.endDate, MAX_LOG_ENTRIES + 1)
      .transact(transactor)
      .map {
        case Nil                                  => (NoLogEntriesInRange: TgError).asLeft
        case entries if entries.size > MAX_LOG_ENTRIES => (TooManyLogEntries(MAX_LOG_ENTRIES): TgError).asLeft
        case entries                              => entries.map(toAiDto).asRight
      }

  private def findStoredPrompt(accountId: UUID, promptId: Option[UUID]): IO[Result[Option[String]]] =
    promptId match {
      case None => IO.pure(None.asRight)
      case Some(id) =>
        tgPromptService.findById(accountId, id).map(_.map(full => Option(full.latestVersion.prompt)).leftMap(e => e: TgError))
    }

  private def chatStream(
    accountId: UUID,
    command: MilestoneAiSummaryCommand,
    prompt: String
  ): IO[Result[fs2.Stream[IO, TgAiChunk]]] =
    tgAiService
      .chatStream(accountId, TgAiChatRequest(command.model, List(TgAiMessage.User(prompt))))
      .map(_.leftMap(e => e: TgError))

  private def buildPrompt(storedPrompt: Option[String], summarizePrompt: String, entries: List[LogEntryAiDto]): String =
    (storedPrompt.toList :+ summarizePrompt :+ entries.asJson.printWith(Printer.noSpaces)).mkString("\n\n")
}

object MilestoneAiSummaryService {
  val MAX_LOG_ENTRIES: Int = 500

  case class LogEntryAiDto(
    logEntryId: UUID,
    taskId: UUID,
    status: String,
    textInternal: String,
    textExternal: String,
    createdAt: Instant,
    updatedAt: Instant
  )

  def toAiDto(entry: LogEntryWithOneVersion): LogEntryAiDto =
    LogEntryAiDto(
      logEntryId = entry.logEntry.id,
      taskId = entry.logEntry.taskId,
      status = entry.version.status.statusName,
      textInternal = entry.version.textInternal,
      textExternal = entry.version.textExternal,
      createdAt = entry.logEntry.createdAt,
      updatedAt = entry.version.updatedAt
    )
}
