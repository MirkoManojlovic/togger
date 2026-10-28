package com.togger.milestone

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.ai._
import com.togger.common.TgError
import com.togger.logentry._
import com.togger.tgprompttemplate.TgPromptTemplateError.TgPromptTemplateNotFound
import com.togger.tgprompttemplate.{TgPromptTemplate, TgPromptTemplateFull, TgPromptTemplateService, TgPromptTemplateVersion}
import doobie.util.transactor.Strategy
import doobie.{ConnectionIO, Transactor}
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class MilestoneAiSummaryServiceTest extends AsyncWordSpec with AsyncMockFactory with Matchers {
  import MilestoneAiSummaryError._

  private val accountId = UUID.randomUUID()
  private val milestoneId = UUID.randomUUID()
  private val start = LocalDate.of(2026, 10, 10)
  private val end = LocalDate.of(2026, 10, 12)
  private val command =
    MilestoneAiSummaryCommand(milestoneId, None, "Summarise the work.", start, end, TgAiModel("gpt-4o-mini"))

  private val noOp = ().pure[ConnectionIO]
  private val transactor: Transactor[IO] =
    Transactor.fromConnection[IO](null, None).copy(strategy0 = Strategy(noOp, noOp, noOp, noOp))

  private val logEntryId = UUID.fromString("00000000-0000-0000-0000-000000000001")
  private val taskId = UUID.fromString("00000000-0000-0000-0000-000000000002")
  private val at = Instant.parse("2026-10-11T10:00:00Z")

  private val entry = LogEntryWithOneVersion(
    LogEntry(logEntryId, taskId, accountId, at),
    LogEntryVersion(UUID.randomUUID(), logEntryId, "internal", "external", LogEntryStatus.Completed, accountId, at, at)
  )

  private val expectedEntryJson =
    s"""[{"logEntryId":"$logEntryId","taskId":"$taskId","status":"Completed","textInternal":"internal",""" +
      s""""textExternal":"external","createdAt":"$at","updatedAt":"$at"}]"""

  private class CapturingAiService(reply: Either[TgAiError.TgAiError, List[String]])
      extends TgAiService(null, null) {
    val request = new AtomicReference[TgAiChatRequest]()
    override def chatStream(accountId: UUID, request: TgAiChatRequest): IO[Result[fs2.Stream[IO, TgAiChunk]]] =
      IO { this.request.set(request); reply.map(pieces => fs2.Stream.emits(pieces.map(TgAiChunk(_))).covary[IO]) }
  }

  private def summarise(service: MilestoneAiSummaryService, command: MilestoneAiSummaryCommand) =
    service.summariseStream(accountId, command).flatMap {
      case Right(stream) => stream.compile.toList.map(_.map(_.delta).mkString.asRight[TgError])
      case Left(error)   => IO.pure(error.asLeft[String])
    }

  private def promptService(reply: Either[com.togger.tgprompttemplate.TgPromptTemplateError.TgPromptTemplateError, TgPromptTemplateFull]) =
    new TgPromptTemplateService(null, null, transactor) {
      override def findById(accountId: UUID, id: UUID): IO[Result[TgPromptTemplateFull]] = IO.pure(reply)
    }

  private def repoReturning(entries: List[LogEntryWithOneVersion]) = {
    val repo = mock[LogEntryRepo]
    val _ = (repo.findLatestByMilestoneIdBetween _)
      .expects(milestoneId, start, end, MilestoneAiSummaryService.MAX_LOG_ENTRIES + 1)
      .returns(entries.pure[ConnectionIO])
    repo
  }

  private val unusedPromptService = promptService(Left(TgPromptTemplateNotFound))

  "MilestoneAiSummaryService" should {
    "send summarize prompt followed by the entries as compact json, and stream the summary" in {
      val ai = new CapturingAiService(List("the ", "summary").asRight)
      val service = new MilestoneAiSummaryService(repoReturning(List(entry)), unusedPromptService, ai, transactor)
      summarise(service, command).map { result =>
        (result, ai.request.get()) shouldBe (
          "the summary".asRight,
          TgAiChatRequest(
            TgAiModel("gpt-4o-mini"),
            List(TgAiMessage.User(s"Summarise the work.\n\n$expectedEntryJson"))
          )
        )
      }.unsafeToFuture()
    }

    "prepend the stored prompt to the summarize prompt" in {
      val promptId = UUID.randomUUID()
      val full = TgPromptTemplateFull(
        TgPromptTemplate(promptId, accountId, at),
        TgPromptTemplateVersion(UUID.randomUUID(), promptId, "title", "You are a PM.", at)
      )
      val ai = new CapturingAiService(List("ok").asRight)
      val service = new MilestoneAiSummaryService(repoReturning(List(entry)), promptService(full.asRight), ai, transactor)
      summarise(service, command.copy(promptId = Some(promptId))).map { _ =>
        ai.request.get().messages shouldBe
          List(TgAiMessage.User(s"You are a PM.\n\nSummarise the work.\n\n$expectedEntryJson"))
      }.unsafeToFuture()
    }

    "fail when the stored prompt is not found, without calling the ai" in {
      val ai = new CapturingAiService(List("ok").asRight)
      val service = new MilestoneAiSummaryService(repoReturning(List(entry)), unusedPromptService, ai, transactor)
      summarise(service, command.copy(promptId = Some(UUID.randomUUID()))).map { result =>
        (result, Option(ai.request.get())) shouldBe (TgPromptTemplateNotFound.asLeft, None)
      }.unsafeToFuture()
    }

    "fail without calling the ai when there are no log entries" in {
      val ai = new CapturingAiService(List("ok").asRight)
      val service = new MilestoneAiSummaryService(repoReturning(Nil), unusedPromptService, ai, transactor)
      summarise(service, command).map { result =>
        (result, Option(ai.request.get())) shouldBe (NoLogEntriesInRange.asLeft, None)
      }.unsafeToFuture()
    }

    "fail when there are more log entries than the limit" in {
      val ai = new CapturingAiService(List("ok").asRight)
      val tooMany = List.fill(MilestoneAiSummaryService.MAX_LOG_ENTRIES + 1)(entry)
      val service = new MilestoneAiSummaryService(repoReturning(tooMany), unusedPromptService, ai, transactor)
      summarise(service, command).map { result =>
        (result, Option(ai.request.get())) shouldBe (TooManyLogEntries(MilestoneAiSummaryService.MAX_LOG_ENTRIES).asLeft, None)
      }.unsafeToFuture()
    }

    "pass through ai errors" in {
      val ai = new CapturingAiService(TgAiError.TgAiApiKeyMissing.asLeft)
      val service = new MilestoneAiSummaryService(repoReturning(List(entry)), unusedPromptService, ai, transactor)
      summarise(service, command).map(_ shouldBe TgAiError.TgAiApiKeyMissing.asLeft).unsafeToFuture()
    }
  }
}
