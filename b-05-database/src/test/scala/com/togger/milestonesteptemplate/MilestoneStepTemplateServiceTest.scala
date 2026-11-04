package com.togger.milestonesteptemplate

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.milestonesteptemplate.MilestoneStepTemplateError.MilestoneStepTemplateNotFound
import com.togger.milestonesteptemplate.MilestoneStepTemplateError.StepTemplateInUse
import com.togger.milestonetemplate.MilestoneTemplateRepo
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.util.invariant.UnexpectedEnd
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class MilestoneStepTemplateServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with TestLoggingJvm {

  private val templateRepo: MilestoneStepTemplateRepo = mock[MilestoneStepTemplateRepo]
  private val versionRepo: MilestoneStepTemplateVersionRepo = mock[MilestoneStepTemplateVersionRepo]
  private val milestoneTemplateRepo: MilestoneTemplateRepo = mock[MilestoneTemplateRepo]
  private val service =
    new MilestoneStepTemplateService(templateRepo, versionRepo, milestoneTemplateRepo, transactorMock)

  private val template = MilestoneStepTemplate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), nowAsInstant)
  private val current = MilestoneStepTemplateVersion(UUID.randomUUID(), template.id, "Design", "Plan it", nowAsInstant)

  testNameFromClassName should {

    "not save a new version when the content did not change" in {
      (templateRepo.findById _).expects(template.id).returns(template.pure[ConnectionIO])
      (versionRepo.findLatestByTemplateId _).expects(template.id).returns(current.pure[ConnectionIO])
      (templateRepo.findFullById _)
        .expects(template.id)
        .returns(MilestoneStepTemplateFull(template, current).pure[ConnectionIO])

      service.update(template.id, "Design", "Plan it", nowAsInstant).unsafeToFuture().map(_.isRight shouldBe true)
    }

    "save a new version when the content changed" in {
      val changed = current.copy(version = UUID.randomUUID(), name = "Design 2")
      (templateRepo.findById _).expects(template.id).returns(template.pure[ConnectionIO])
      (versionRepo.findLatestByTemplateId _).expects(template.id).returns(current.pure[ConnectionIO])
      (versionRepo.insert _).expects(*).returns(changed.pure[ConnectionIO])
      (templateRepo.findFullById _)
        .expects(template.id)
        .returns(MilestoneStepTemplateFull(template, changed).pure[ConnectionIO])

      service.update(template.id, "Design 2", "Plan it", nowAsInstant).unsafeToFuture().map { result =>
        result.map(_.latestVersion.name) shouldBe Right("Design 2")
      }
    }

    "answer not found when updating a template that does not exist" in {
      (templateRepo.findById _)
        .expects(template.id)
        .returns(doobie.free.connection.raiseError[MilestoneStepTemplate](UnexpectedEnd))

      service
        .update(template.id, "x", "y", nowAsInstant)
        .unsafeToFuture()
        .map(_ shouldBe Left(MilestoneStepTemplateNotFound))
    }

    "refuse to delete a template that milestone templates still use" in {
      (milestoneTemplateRepo.findNamesUsingStepTemplate _)
        .expects(template.id)
        .returns(List("Migration", "New feature").pure[ConnectionIO])

      service.delete(template.id).unsafeToFuture().map { result =>
        result shouldBe Left(StepTemplateInUse(List("Migration", "New feature")))
      }
    }

    "answer not found when deleting a template that does not exist" in {
      (milestoneTemplateRepo.findNamesUsingStepTemplate _)
        .expects(template.id)
        .returns(List.empty[String].pure[ConnectionIO])
      (templateRepo.deleteById _).expects(template.id).returns(0.pure[ConnectionIO])

      service.delete(template.id).unsafeToFuture().map(_ shouldBe Left(MilestoneStepTemplateNotFound))
    }
  }
}
