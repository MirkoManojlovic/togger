package com.togger.milestonetemplate

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.milestonesteptemplate.MilestoneStepTemplate
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepo
import com.togger.milestonetemplate.MilestoneTemplateError.MilestoneTemplateNotFound
import com.togger.milestonetemplate.MilestoneTemplateError.StepTemplateNotInProject
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.util.invariant.UnexpectedEnd
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class MilestoneTemplateServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with TestLoggingJvm {

  private val templateRepo: MilestoneTemplateRepo = mock[MilestoneTemplateRepo]
  private val versionRepo: MilestoneTemplateVersionRepo = mock[MilestoneTemplateVersionRepo]
  private val stepTemplateRepo: MilestoneStepTemplateRepo = mock[MilestoneStepTemplateRepo]
  private val service = new MilestoneTemplateService(templateRepo, versionRepo, stepTemplateRepo, transactorMock)

  private val projectId = UUID.randomUUID()
  private val template = MilestoneTemplate(UUID.randomUUID(), projectId, UUID.randomUUID(), nowAsInstant)
  private val firstVersion = MilestoneTemplateVersion(UUID.randomUUID(), template.id, "New feature", "d", nowAsInstant)
  private val design = UUID.randomUUID()
  private val development = UUID.randomUUID()
  private val staging = UUID.randomUUID()

  private def stepTemplate(id: UUID, inProject: UUID) =
    MilestoneStepTemplate(id, inProject, UUID.randomUUID(), nowAsInstant)

  private def step(id: UUID, name: String, stage: Int = 0) =
    MilestoneTemplateStep(id, name, s"$name description", stage)

  private def full(version: MilestoneTemplateVersion, steps: List[MilestoneTemplateStep]) =
    MilestoneTemplateFull(template, MilestoneTemplateVersionWithSteps(version, steps))

  private def stepTemplatesExist(ids: UUID*): Unit =
    ids.foreach(id => (stepTemplateRepo.findById _).expects(id).returns(stepTemplate(id, projectId).pure[ConnectionIO]))

  testNameFromClassName should {

    "create the template with its steps in the given order" in {
      stepTemplatesExist(development, design)
      (templateRepo.insert _).expects(template).returns(template.pure[ConnectionIO])
      (versionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (versionRepo.insertSteps _)
        .expects(firstVersion.version, List(List(development), List(design)))
        .returns(2.pure[ConnectionIO])
      (templateRepo.findFullById _)
        .expects(template.id)
        .returns(
          full(firstVersion, List(step(development, "Development", 0), step(design, "Design", 1))).pure[ConnectionIO]
        )

      service.create(template, firstVersion, List(List(development), List(design))).unsafeToFuture().map { result =>
        result.map(_.latest.steps.map(_.name)) shouldBe Right(List("Development", "Design"))
      }
    }

    "reject a step template of another project" in {
      val foreign = UUID.randomUUID()
      (stepTemplateRepo.findById _)
        .expects(foreign)
        .returns(stepTemplate(foreign, UUID.randomUUID()).pure[ConnectionIO])

      service.create(template, firstVersion, List(List(foreign))).unsafeToFuture().map { result =>
        result shouldBe Left(StepTemplateNotInProject(foreign))
      }
    }

    "reject a step template that does not exist" in {
      val missing = UUID.randomUUID()
      (stepTemplateRepo.findById _)
        .expects(missing)
        .returns(doobie.free.connection.raiseError[MilestoneStepTemplate](UnexpectedEnd))

      service.create(template, firstVersion, List(List(missing))).unsafeToFuture().map { result =>
        result shouldBe Left(StepTemplateNotInProject(missing))
      }
    }

    "not save a new version when nothing changed" in {
      val current = full(firstVersion, List(step(design, "Design", 0), step(development, "Development", 1)))
      (templateRepo.findById _).expects(template.id).returns(template.pure[ConnectionIO])
      stepTemplatesExist(design, development)
      (templateRepo.findFullById _).expects(template.id).returns(current.pure[ConnectionIO]).twice()

      service
        .update(template.id, "New feature", "d", List(List(design), List(development)), nowAsInstant)
        .unsafeToFuture()
        .map(_ shouldBe Right(current))
    }

    "save a new version when only the order of the steps changed" in {
      val current = full(firstVersion, List(step(design, "Design", 0), step(development, "Development", 1)))
      val newVersion = firstVersion.copy(version = UUID.randomUUID())
      (templateRepo.findById _).expects(template.id).returns(template.pure[ConnectionIO])
      stepTemplatesExist(development, design)
      (templateRepo.findFullById _).expects(template.id).returns(current.pure[ConnectionIO])
      (versionRepo.insert _).expects(*).returns(newVersion.pure[ConnectionIO])
      (versionRepo.insertSteps _)
        .expects(newVersion.version, List(List(development), List(design)))
        .returns(2.pure[ConnectionIO])
      (templateRepo.findFullById _).expects(template.id).returns(current.pure[ConnectionIO])

      service
        .update(template.id, "New feature", "d", List(List(development), List(design)), nowAsInstant)
        .unsafeToFuture()
        .map(_.isRight shouldBe true)
    }

    "not save a new version when only the order inside a parallel stage changed" in {
      val current = full(
        firstVersion,
        List(step(design, "Design", 0), step(development, "Development", 1), step(staging, "Staging", 1))
      )
      (templateRepo.findById _).expects(template.id).returns(template.pure[ConnectionIO])
      stepTemplatesExist(design, staging, development)
      (templateRepo.findFullById _).expects(template.id).returns(current.pure[ConnectionIO]).twice()

      service
        .update(template.id, "New feature", "d", List(List(design), List(staging, development)), nowAsInstant)
        .unsafeToFuture()
        .map(_ shouldBe Right(current))
    }

    "save a new version when steps that ran in parallel run in sequence" in {
      val current = full(
        firstVersion,
        List(step(design, "Design", 0), step(development, "Development", 0))
      )
      val newVersion = firstVersion.copy(version = UUID.randomUUID())
      (templateRepo.findById _).expects(template.id).returns(template.pure[ConnectionIO])
      stepTemplatesExist(design, development)
      (templateRepo.findFullById _).expects(template.id).returns(current.pure[ConnectionIO])
      (versionRepo.insert _).expects(*).returns(newVersion.pure[ConnectionIO])
      (versionRepo.insertSteps _)
        .expects(newVersion.version, List(List(design), List(development)))
        .returns(2.pure[ConnectionIO])
      (templateRepo.findFullById _).expects(template.id).returns(current.pure[ConnectionIO])

      service
        .update(template.id, "New feature", "d", List(List(design), List(development)), nowAsInstant)
        .unsafeToFuture()
        .map(_.isRight shouldBe true)
    }

    "answer not found when updating a template that does not exist" in {
      (templateRepo.findById _)
        .expects(template.id)
        .returns(doobie.free.connection.raiseError[MilestoneTemplate](UnexpectedEnd))

      service
        .update(template.id, "x", "y", Nil, nowAsInstant)
        .unsafeToFuture()
        .map(_ shouldBe Left(MilestoneTemplateNotFound))
    }

    "answer not found when deleting a template that does not exist" in {
      (templateRepo.deleteById _).expects(template.id).returns(0.pure[ConnectionIO])

      service.delete(template.id).unsafeToFuture().map(_ shouldBe Left(MilestoneTemplateNotFound))
    }
  }
}
