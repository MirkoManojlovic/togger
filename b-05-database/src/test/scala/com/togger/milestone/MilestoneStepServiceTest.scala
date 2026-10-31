package com.togger.milestone

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.milestone.MilestoneStepError.{MilestoneStepNotFound, StepsDoNotMatchMilestone}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.util.invariant.UnexpectedEnd
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.LocalDate
import java.util.UUID
import scala.collection.mutable.ListBuffer

class MilestoneStepServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with TestLoggingJvm {

  private val stepRepo: MilestoneStepRepo = mock[MilestoneStepRepo]
  private val versionRepo: MilestoneStepVersionRepo = mock[MilestoneStepVersionRepo]
  private val service = new MilestoneStepService(stepRepo, versionRepo, transactorMock)

  private val milestoneId = UUID.randomUUID()

  private def newStep() = MilestoneStep(UUID.randomUUID(), milestoneId, nowAsInstant)

  private def newVersion(stepId: UUID, name: String, position: Int) = MilestoneStepVersion(
    version = UUID.randomUUID(),
    milestoneStepId = stepId,
    name = name,
    description = s"Description $name",
    status = MilestoneStepStatus.Planned,
    position = position,
    startDate = LocalDate.of(2024, 1, 1),
    dueDate = LocalDate.of(2024, 1, 31),
    endDate = None,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant,
    changeComment = ""
  )

  /** Records every inserted version and answers it unchanged. */
  private def recordInserts(): ListBuffer[MilestoneStepVersion] = {
    val inserted = ListBuffer.empty[MilestoneStepVersion]
    (versionRepo.insert _).expects(*).onCall { (version: MilestoneStepVersion) =>
      inserted += version
      version.pure[ConnectionIO]
    }.anyNumberOfTimes()
    inserted
  }

  testNameFromClassName should {

    "append a new step after the last position" in {
      val existing = newVersion(UUID.randomUUID(), "Design", 0)
      val step = newStep()
      val first = newVersion(step.id, "Prototype", 99)
      (versionRepo.findLatestByMilestoneId _).expects(milestoneId).returns(List(existing).pure[ConnectionIO])
      (stepRepo.insert _).expects(step).returns(step.pure[ConnectionIO])
      val inserted = recordInserts()

      service.createMilestoneStep(step, first, None, nowAsInstant).unsafeToFuture().map { result =>
        result.version.position shouldBe 1
        inserted.map(_.name) shouldBe List("Prototype")
      }
    }

    "insert a step in the middle and move the later steps down with a new version" in {
      val design = newVersion(UUID.randomUUID(), "Design", 0)
      val development = newVersion(UUID.randomUUID(), "Development", 1)
      val production = newVersion(UUID.randomUUID(), "Production", 2)
      val step = newStep()
      val first = newVersion(step.id, "Prototype", 99)
      (versionRepo.findLatestByMilestoneId _)
        .expects(milestoneId)
        .returns(List(production, design, development).pure[ConnectionIO])
      (stepRepo.insert _).expects(step).returns(step.pure[ConnectionIO])
      val inserted = recordInserts()

      service.createMilestoneStep(step, first, Some(1), nowAsInstant).unsafeToFuture().map { result =>
        result.version.position shouldBe 1
        val moved = inserted.filterNot(_.milestoneStepId == step.id).map(v => v.name -> v.position).toList
        moved shouldBe List("Development" -> 2, "Production" -> 3)
        inserted.filterNot(_.milestoneStepId == step.id).map(_.changeComment).distinct should have size 1
      }
    }

    "keep the position when saving a new version of a step" in {
      val step = newStep()
      val current = newVersion(step.id, "Design", 3)
      val update = newVersion(step.id, "Design 2", 0).copy(changeComment = "Renamed")
      (stepRepo.findById _).expects(step.id).returns(step.pure[ConnectionIO])
      (versionRepo.findLatestByMilestoneStepId _).expects(step.id).returns(current.pure[ConnectionIO])
      val inserted = recordInserts()

      service.saveNewVersion(step.id, update).unsafeToFuture().map { result =>
        result.map(_.version.position) shouldBe Right(3)
        inserted.map(_.changeComment).toList shouldBe List("Renamed")
      }
    }

    "answer not found when the step to version does not exist" in {
      val id = UUID.randomUUID()
      (stepRepo.findById _).expects(id).returns(doobie.free.connection.raiseError[com.togger.milestone.MilestoneStep](UnexpectedEnd))

      service.saveNewVersion(id, newVersion(id, "x", 0)).unsafeToFuture().map(_ shouldBe Left(MilestoneStepNotFound))
    }

    "version only the steps that move when reordering, with the shared comment" in {
      val design = newVersion(UUID.randomUUID(), "Design", 0)
      val development = newVersion(UUID.randomUUID(), "Development", 1)
      val production = newVersion(UUID.randomUUID(), "Production", 2)
      val steps = List(design, development, production).map(v => MilestoneStep(v.milestoneStepId, milestoneId, nowAsInstant))
      (versionRepo.findLatestByMilestoneId _)
        .expects(milestoneId)
        .returning(List(design, development, production).pure[ConnectionIO])
        .twice()
      (stepRepo.findAllByMilestoneId _).expects(milestoneId).returns(steps.pure[ConnectionIO])
      val inserted = recordInserts()
      val order = List(design, production, development).map(_.milestoneStepId)

      service.reorder(milestoneId, order, "Swap the last two", nowAsInstant).unsafeToFuture().map { result =>
        result.isRight shouldBe true
        inserted.map(v => v.name -> v.position).toList shouldBe List("Production" -> 1, "Development" -> 2)
        inserted.map(_.changeComment).distinct.toList shouldBe List("Swap the last two")
      }
    }

    "count the closed steps per milestone and default to zero for milestones without steps" in {
      val withSteps = UUID.randomUUID()
      val without = UUID.randomUUID()
      (versionRepo.countByMilestoneIds _)
        .expects(List(withSteps, without))
        .returns(Map(withSteps -> MilestoneStepCounts(3, 2)).pure[ConnectionIO])

      service.countsByMilestoneIds(List(withSteps, without)).unsafeToFuture().map { counts =>
        counts shouldBe Map(withSteps -> MilestoneStepCounts(3, 2), without -> MilestoneStepCounts.empty)
      }
    }

    "not query the counts when there are no milestones" in {
      service.countsByMilestoneIds(Nil).unsafeToFuture().map(_ shouldBe Map.empty)
    }

    "find the steps outside a new milestone range" in {
      val inside = newVersion(UUID.randomUUID(), "Inside", 0).copy(
        startDate = LocalDate.of(2024, 1, 10),
        dueDate = LocalDate.of(2024, 1, 20)
      )
      val startsEarly = newVersion(UUID.randomUUID(), "Starts early", 1).copy(
        startDate = LocalDate.of(2024, 1, 2),
        dueDate = LocalDate.of(2024, 1, 20)
      )
      val endsLate = newVersion(UUID.randomUUID(), "Ends late", 2).copy(
        startDate = LocalDate.of(2024, 1, 10),
        dueDate = LocalDate.of(2024, 2, 20)
      )
      (versionRepo.findLatestByMilestoneId _)
        .expects(milestoneId)
        .returns(List(endsLate, inside, startsEarly).pure[ConnectionIO])

      service
        .findStepsOutsideRange(milestoneId, LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 31))
        .unsafeToFuture()
        .map(_.map(_.name) shouldBe List("Starts early", "Ends late"))
    }

    "reject a reorder that does not list exactly the steps of the milestone" in {
      val design = newVersion(UUID.randomUUID(), "Design", 0)
      (versionRepo.findLatestByMilestoneId _).expects(milestoneId).returns(List(design).pure[ConnectionIO])

      service
        .reorder(milestoneId, List(design.milestoneStepId, UUID.randomUUID()), "comment", nowAsInstant)
        .unsafeToFuture()
        .map(_ shouldBe Left(StepsDoNotMatchMilestone))
    }
  }
}
