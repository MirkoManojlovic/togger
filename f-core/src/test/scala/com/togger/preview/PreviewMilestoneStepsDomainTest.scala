package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, PageRequest, Unauthenticated}
import com.togger.demo.{DemoIdentity, DemoMilestonesData}
import com.togger.dto._
import com.togger.milestone.MilestoneStepStatus
import com.togger.ui.milestone.{MilestoneFeServicePreview, MilestoneStepFeServicePreview}

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future

class PreviewMilestoneStepsDomainTest extends AsyncJsWordSpec {

  // 2026-07-06 is a Monday; the milestone lasts two work weeks.
  private val monday = LocalDate.parse("2026-07-06")
  private val friday = monday.plusDays(4)
  private val nextMonday = monday.plusDays(7)
  private val nextFriday = monday.plusDays(11)

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val steps = new MilestoneStepFeServicePreview(m.context, m.projects, m.milestones, m.milestoneSteps)
    val milestones = new MilestoneFeServicePreview(m.context, m.projects, m.milestones, m.milestoneSteps)
    env.loginAsDemo()

    /** A new milestone with steps A, B, C (one per half week) and the ids of the steps in order. */
    def milestoneWithSteps(): Future[(UUID, List[UUID])] =
      for {
        created <- env.run(
          milestones.createMilestone(
            CreateMilestoneReq(
              DemoIdentity.ProjectId,
              "M",
              "d",
              monday,
              nextFriday,
              List(
                NewMilestoneStepReq("A", "", monday, friday),
                NewMilestoneStepReq("B", "", monday, friday),
                NewMilestoneStepReq("C", "", nextMonday, nextFriday)
              )
            )
          )
        )
        listed <- env.run(steps.findByMilestoneId(created.toOption.get.id))
      } yield created.toOption.get.id -> listed.toOption.get.drop(1).map(_.id)
  }

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  private def update(
    id: UUID,
    status: MilestoneStepStatus = MilestoneStepStatus.InProgress,
    start: LocalDate = monday,
    due: LocalDate = friday,
    end: Option[LocalDate] = None,
    comment: String = "why",
    name: String = "A"
  ) = UpdateMilestoneStepReq(id, name, "", status, start, due, end, comment)

  testNameFromClassName should {

    "answer unauthenticated without a session" in { _ =>
      val env = new PreviewTestEnv
      val m = env.module
      val steps = new MilestoneStepFeServicePreview(m.context, m.projects, m.milestones, m.milestoneSteps)
      env.run(steps.findByMilestoneId(DemoMilestonesData.FoundationsId)).map(_ shouldBe Left(Unauthenticated))
    }

    "list the seeded steps of a demo milestone in order" in { _ =>
      val s = new Services
      s.env.run(s.steps.findByMilestoneId(DemoMilestonesData.FoundationsId)).map { result =>
        result.map(_.map(_.name)) shouldBe Right(List("Design", "Development", "Production"))
        result.map(_.map(_.position)) shouldBe Right(List(0, 1, 2))
      }
    }

    "append a step and insert one at a position, moving the later steps with a comment" in { _ =>
      val s = new Services
      for {
        (milestoneId, ids) <- s.milestoneWithSteps()
        appended <- s.env.run(s.steps.createMilestoneStep(CreateMilestoneStepReq(milestoneId, "D", "", monday, friday)))
        inserted <- s.env.run(
          s.steps.createMilestoneStep(CreateMilestoneStepReq(milestoneId, "X", "", monday, friday, Some(1)))
        )
        listed <- s.env.run(s.steps.findByMilestoneId(milestoneId))
        moved <- s.env.run(s.steps.listAllVersions(ids(1)))
      } yield {
        appended.map(_.position) shouldBe Right(4)
        inserted.map(_.position) shouldBe Right(1)
        listed.map(_.map(_.name)) shouldBe Right(List("Shelf", "X", "A", "B", "C", "D"))
        listed.map(_.map(_.position)) shouldBe Right(List(0, 1, 2, 3, 4, 5))
        moved.map(_.map(_.changeComment)) shouldBe Right(List("Moved to make room for a new step", ""))
      }
    }

    "reject dates outside the milestone or on a day off, and an unknown milestone" in { _ =>
      val s = new Services
      for {
        (milestoneId, _) <- s.milestoneWithSteps()
        outside <- s.env.run(
          s.steps.createMilestoneStep(CreateMilestoneStepReq(milestoneId, "O", "", monday, nextFriday.plusDays(7)))
        )
        dayOff <- s.env.run(
          s.steps.createMilestoneStep(CreateMilestoneStepReq(milestoneId, "W", "", monday.plusDays(5), nextMonday))
        )
        unknown <- s.env.run(
          s.steps.createMilestoneStep(CreateMilestoneStepReq(UUID.randomUUID(), "U", "", monday, friday))
        )
      } yield {
        isBadRequest(outside) shouldBe true
        isBadRequest(dayOff) shouldBe true
        isBadRequest(unknown) shouldBe true
      }
    }

    "save a new version on update, keep the position and require a comment" in { _ =>
      val s = new Services
      for {
        (_, ids) <- s.milestoneWithSteps()
        noComment <- s.env.run(s.steps.updateMilestoneStep(update(ids(1), comment = "  ")))
        saved <- s.env.run(s.steps.updateMilestoneStep(update(ids(1), name = "B2", comment = " Renamed ")))
        versions <- s.env.run(s.steps.listAllVersions(ids(1)))
      } yield {
        isBadRequest(noComment) shouldBe true
        saved.map(r => (r.name, r.position, r.changeComment)) shouldBe Right(("B2", 2, "Renamed"))
        versions.map(_.map(_.name)) shouldBe Right(List("B2", "B"))
      }
    }

    "close a step only with an end date, reject dates outside the milestone and let it be reopened" in { _ =>
      val s = new Services
      for {
        (_, ids) <- s.milestoneWithSteps()
        noEnd <- s.env.run(s.steps.updateMilestoneStep(update(ids(0), status = MilestoneStepStatus.Done)))
        done <- s.env.run(
          s.steps.updateMilestoneStep(update(ids(0), status = MilestoneStepStatus.Done, end = Some(friday)))
        )
        abandoned <- s.env.run(
          s.steps.updateMilestoneStep(update(ids(1), status = MilestoneStepStatus.Abandoned, end = Some(monday)))
        )
        outside <- s.env.run(s.steps.updateMilestoneStep(update(ids(2), start = monday.minusDays(7), due = friday)))
        reopened <- s.env.run(s.steps.updateMilestoneStep(update(ids(0), status = MilestoneStepStatus.InProgress)))
      } yield {
        isBadRequest(noEnd) shouldBe true
        done.map(r => (r.status, r.endDate)) shouldBe Right((MilestoneStepStatus.Done, Some(friday)))
        abandoned.map(_.status) shouldBe Right(MilestoneStepStatus.Abandoned)
        isBadRequest(outside) shouldBe true
        reopened.map(r => (r.status, r.endDate)) shouldBe Right((MilestoneStepStatus.InProgress, None))
      }
    }

    "count the closed steps of the milestone" in { _ =>
      val s = new Services
      for {
        (milestoneId, ids) <- s.milestoneWithSteps()
        _ <- s.env.run(s.steps.updateMilestoneStep(update(ids(0), status = MilestoneStepStatus.Done, end = Some(friday))))
        found <- s.env.run(s.milestones.findMilestoneById(milestoneId))
      } yield found.map(r => (r.stepsTotal, r.stepsClosed)) shouldBe Right((4, 1))
    }

    "version only the steps that move when reordering, with the shared comment" in { _ =>
      val s = new Services
      for {
        (milestoneId, ids) <- s.milestoneWithSteps()
        noComment <- s.env.run(s.steps.reorderMilestoneSteps(ReorderMilestoneStepsReq(milestoneId, ids, " ")))
        reordered <- s.env.run(
          s.steps.reorderMilestoneSteps(ReorderMilestoneStepsReq(milestoneId, List(ids(0), ids(2), ids(1)), "Swap"))
        )
        untouched <- s.env.run(s.steps.listAllVersions(ids(0)))
        moved <- s.env.run(s.steps.listAllVersions(ids(1)))
        mismatch <- s.env.run(s.steps.reorderMilestoneSteps(ReorderMilestoneStepsReq(milestoneId, ids.take(2), "x")))
      } yield {
        isBadRequest(noComment) shouldBe true
        reordered.map(_.map(_.name)) shouldBe Right(List("Shelf", "A", "C", "B"))
        untouched.map(_.size) shouldBe Right(1)
        moved.map(_.map(v => (v.position, v.changeComment))) shouldBe Right(List((3, "Swap"), (2, "")))
        isBadRequest(mismatch) shouldBe true
      }
    }

    "refuse to delete the Shelf step" in { _ =>
      val s = new Services
      for {
        (milestoneId, ids) <- s.milestoneWithSteps()
        listed <- s.env.run(s.steps.findByMilestoneId(milestoneId))
        deleted <- s.env.runEmpty(s.steps.deleteMilestoneStepById(listed.toOption.get.head.id))
        edited <- s.env.run(s.steps.updateMilestoneStep(update(listed.toOption.get.head.id, comment = "x")))
        reserved <- s.env.run(s.steps.updateMilestoneStep(update(ids(0), name = "shelf", comment = "x")))
      } yield {
        isBadRequest(deleted) shouldBe true
        isBadRequest(edited) shouldBe true
        isBadRequest(reserved) shouldBe true
      }
    }

    "delete a step and answer bad request for one that does not exist" in { _ =>
      val s = new Services
      for {
        (milestoneId, ids) <- s.milestoneWithSteps()
        deleted <- s.env.runEmpty(s.steps.deleteMilestoneStepById(ids(0)))
        again <- s.env.runEmpty(s.steps.deleteMilestoneStepById(ids(0)))
        listed <- s.env.run(s.steps.findByMilestoneId(milestoneId))
        missing <- s.env.run(s.steps.findMilestoneStepById(UUID.randomUUID()))
        paged <- s.env.run(s.steps.listVersions(ids(1), PageRequest(1, 10)))
      } yield {
        deleted.isRight shouldBe true
        isBadRequest(again) shouldBe true
        listed.map(_.map(_.name)) shouldBe Right(List("Shelf", "B", "C"))
        isBadRequest(missing) shouldBe true
        paged.map(_.totalNumberOfItems) shouldBe Right(1L)
      }
    }
  }
}
