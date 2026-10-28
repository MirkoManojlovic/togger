package com.togger.tgaiaction

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.ai.TgAiEndpoints
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.project.ProjectAccountRepo
import com.togger.suites.TestNameHelper
import com.togger.tgaiaction.TgAiActionError._
import com.togger.tgprompttemplate.TgPromptTemplateRepo
import doobie.ConnectionIO
import doobie.util.invariant.UnexpectedEnd
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class TgAiActionServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with TestLoggingJvm {

  private val actionRepo = mock[TgAiActionRepo]
  private val endpointRepo = mock[TgAiActionEndpointRepo]
  private val defaultRepo = mock[TgAiActionDefaultRepo]
  private val promptTemplateRepo = mock[TgPromptTemplateRepo]
  private val projectAccountRepo = mock[ProjectAccountRepo]

  private val service =
    new TgAiActionService(actionRepo, endpointRepo, defaultRepo, promptTemplateRepo, projectAccountRepo, transactorMock)

  private val accountId = UUID.randomUUID()
  private val projectId = UUID.randomUUID()
  private val endpointId = TgAiEndpoints.MilestoneSummary.value

  private def action(createdBy: UUID = accountId) =
    TgAiAction(UUID.randomUUID(), createdBy, "Summarise", "Prompt", None, "gpt-5", nowAsInstant, nowAsInstant)

  testNameFromClassName should {

    "create the action and bind its endpoints" in {
      val created = action()
      (actionRepo.existsByName _).expects(accountId, created.name, None).returns(false.pure[ConnectionIO])
      (actionRepo.insert _).expects(created).returns(created.pure[ConnectionIO])
      (endpointRepo.replaceBindings _).expects(created.id, List(endpointId)).returns(().pure[ConnectionIO])
      (endpointRepo.findByActionIds _)
        .expects(List(created.id))
        .returns(List(TgAiActionEndpoint(created.id, endpointId)).pure[ConnectionIO])

      service
        .create(created, List(endpointId))
        .map(_ shouldBe Right(TgAiActionWithBindings(created, List(endpointId))))
        .unsafeToFuture()
    }

    "reject an unknown endpoint" in {
      service.create(action(), List(12345)).map(_ shouldBe Left(UnknownEndpoint)).unsafeToFuture()
    }

    "reject a duplicate name" in {
      val created = action()
      (actionRepo.existsByName _).expects(accountId, created.name, None).returns(true.pure[ConnectionIO])
      service.create(created, Nil).map(_ shouldBe Left(DuplicateName)).unsafeToFuture()
    }

    "report an action of another account as not found" in {
      val foreign = action(createdBy = UUID.randomUUID())
      (actionRepo.findById _).expects(foreign.id).returns(foreign.pure[ConnectionIO])
      service.findById(accountId, foreign.id).map(_ shouldBe Left(TgAiActionNotFound)).unsafeToFuture()
    }

    "report a missing action as not found" in {
      val id = UUID.randomUUID()
      (actionRepo.findById _).expects(id).returns(doobie.free.connection.raiseError[TgAiAction](UnexpectedEnd))
      service.delete(accountId, id).map(_ shouldBe Left(TgAiActionNotFound)).unsafeToFuture()
    }

    "set the default of a bound action for a project member" in {
      val owned = action()
      val default = TgAiActionDefault(accountId, projectId, endpointId, owned.id)
      (projectAccountRepo.isMember _).expects(projectId, accountId).returns(true.pure[ConnectionIO])
      (actionRepo.findById _).expects(owned.id).returns(owned.pure[ConnectionIO])
      (endpointRepo.isBound _).expects(owned.id, endpointId).returns(true.pure[ConnectionIO])
      (defaultRepo.upsert _).expects(default).returns(default.pure[ConnectionIO])
      service.setDefault(default).map(_ shouldBe Right(default)).unsafeToFuture()
    }

    "reject a default from a non-member" in {
      val default = TgAiActionDefault(accountId, projectId, endpointId, UUID.randomUUID())
      (projectAccountRepo.isMember _).expects(projectId, accountId).returns(false.pure[ConnectionIO])
      service.setDefault(default).map(_ shouldBe Left(NotAProjectMember)).unsafeToFuture()
    }

    "reject a default for an action that is not bound to the endpoint" in {
      val owned = action()
      val default = TgAiActionDefault(accountId, projectId, endpointId, owned.id)
      (projectAccountRepo.isMember _).expects(projectId, accountId).returns(true.pure[ConnectionIO])
      (actionRepo.findById _).expects(owned.id).returns(owned.pure[ConnectionIO])
      (endpointRepo.isBound _).expects(owned.id, endpointId).returns(false.pure[ConnectionIO])
      service.setDefault(default).map(_ shouldBe Left(ActionNotBoundToEndpoint)).unsafeToFuture()
    }
  }
}
