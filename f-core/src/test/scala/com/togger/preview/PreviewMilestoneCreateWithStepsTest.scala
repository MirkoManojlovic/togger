package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, PageRequest}
import com.togger.demo.DemoIdentity
import com.togger.dto._
import com.togger.ui.milestone.MilestoneFeServicePreview

import java.time.LocalDate

class PreviewMilestoneCreateWithStepsTest extends AsyncJsWordSpec {

  // 2026-07-06 is a Monday; the milestone lasts two work weeks.
  private val monday = LocalDate.parse("2026-07-06")
  private val friday = monday.plusDays(4)
  private val nextMonday = monday.plusDays(7)
  private val nextFriday = monday.plusDays(11)

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    val steps = env.module.milestoneSteps
    val milestones =
      new MilestoneFeServicePreview(env.module.context, env.module.projects, env.module.milestones, steps)
    env.loginAsDemo()
  }

  private def create(steps: List[NewMilestoneStepReq]) =
    CreateMilestoneReq(DemoIdentity.ProjectId, "With steps", "d", monday, nextFriday, steps)

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "create the milestone with its steps in order, all Planned, and count them" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(
          s.milestones.createMilestone(
            create(
              List(
                NewMilestoneStepReq("Design", "d", monday, friday),
                NewMilestoneStepReq("Build", "b", nextMonday, nextFriday)
              )
            )
          )
        )
        id = created.toOption.get.id
        found <- s.env.run(s.milestones.findMilestoneById(id))
      } yield {
        created.map(r => (r.stepsTotal, r.stepsClosed)) shouldBe Right((2, 0))
        found.map(r => (r.stepsTotal, r.stepsClosed)) shouldBe Right((2, 0))
        s.steps.latestVersionsOf(id).map(v => (v.name, v.position, v.status.statusName)) shouldBe
          List(("Design", 0, "Planned"), ("Build", 1, "Planned"))
      }
    }

    "give a milestone without steps the default step with the dates of the milestone" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(s.milestones.createMilestone(create(Nil)))
        id = created.toOption.get.id
      } yield {
        created.map(r => (r.stepsTotal, r.stepsClosed)) shouldBe Right((1, 0))
        s.steps
          .latestVersionsOf(id)
          .map(v => (v.name, v.position, v.status.statusName, v.startDate, v.dueDate)) shouldBe
          List(("Main", 0, "Planned", monday, nextFriday))
      }
    }

    "report the steps that are closed" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(
          s.milestones.createMilestone(
            create(List(NewMilestoneStepReq("A", "", monday, friday), NewMilestoneStepReq("B", "", monday, friday)))
          )
        )
        id = created.toOption.get.id
        first = s.steps.latestVersionsOf(id).head
        _ = s.steps.stepVersions.upsert(
          first.copy(version = java.util.UUID.randomUUID(), statusValue = com.togger.milestone.MilestoneStepStatus.Done.value)
        )
        found <- s.env.run(s.milestones.findMilestoneById(id))
        listed <- s.env.run(s.milestones.findByProjectId(DemoIdentity.ProjectId, MilestoneFilter.empty, PageRequest(1, 50)))
      } yield {
        found.map(r => (r.stepsTotal, r.stepsClosed)) shouldBe Right((2, 1))
        listed.map(_.items.find(_.id == id).map(r => (r.stepsTotal, r.stepsClosed))) shouldBe Right(Some((2, 1)))
      }
    }

    "reject a step outside the milestone, naming the step, and store nothing" in { _ =>
      val s = new Services
      val before = s.env.module.milestones.milestones.all.size
      val stepsBefore = s.steps.steps.all.size
      for {
        outside <- s.env.run(
          s.milestones.createMilestone(create(List(NewMilestoneStepReq("Late", "", nextMonday, nextFriday.plusDays(7)))))
        )
      } yield {
        isBadRequest(outside) shouldBe true
        outside.left.exists(_.toString.contains("Step 'Late'")) shouldBe true
        s.env.module.milestones.milestones.all.size shouldBe before
        s.steps.steps.all.size shouldBe stepsBefore
      }
    }

    "reject a step that starts on a day off or ends before it starts" in { _ =>
      val s = new Services
      for {
        weekend <- s.env.run(
          s.milestones.createMilestone(create(List(NewMilestoneStepReq("Weekend", "", monday.plusDays(5), nextMonday))))
        )
        backwards <- s.env.run(
          s.milestones.createMilestone(create(List(NewMilestoneStepReq("Backwards", "", friday, monday))))
        )
      } yield {
        isBadRequest(weekend) shouldBe true
        isBadRequest(backwards) shouldBe true
      }
    }

    "refuse to move the milestone so that a step falls outside, and allow it otherwise" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(
          s.milestones.createMilestone(create(List(NewMilestoneStepReq("Build", "", nextMonday, nextFriday))))
        )
        id = created.toOption.get.id
        shrink <- s.env.run(s.milestones.updateMilestone(UpdateMilestoneReq(id, "With steps", "d", dueDate = Some(friday))))
        rename <- s.env.run(s.milestones.updateMilestone(UpdateMilestoneReq(id, "Renamed", "d")))
        extend <- s.env.run(
          s.milestones.updateMilestone(UpdateMilestoneReq(id, "Renamed", "d", dueDate = Some(nextFriday.plusDays(7))))
        )
      } yield {
        isBadRequest(shrink) shouldBe true
        shrink.left.exists(_.toString.contains("Build")) shouldBe true
        rename.isRight shouldBe true
        extend.isRight shouldBe true
      }
    }

    "seed three steps for every demo milestone, spread over its dates and inside them" in { _ =>
      val s = new Services
      val milestones = s.env.module.milestones.milestones.all
      milestones.nonEmpty shouldBe true
      milestones.foreach { milestone =>
        val steps = s.steps.latestVersionsOf(milestone.id)
        steps.map(_.position) shouldBe List(0, 1, 2)
        steps.forall(step => !step.startDate.isBefore(milestone.startDate) && !step.dueDate.isAfter(milestone.dueDate)) shouldBe true
        steps.sliding(2).forall { case List(a, b) => !b.startDate.isBefore(a.dueDate) case _ => true } shouldBe true
        steps.forall(step => step.status.isClosing == step.endDate.isDefined) shouldBe true
      }
      s.steps.counts(com.togger.demo.DemoMilestonesData.FoundationsId) shouldBe (3 -> 2)
    }

    "drop the steps with their milestone" in { _ =>
      val s = new Services
      val milestones = s.env.module.milestones
      val stepsBefore = s.steps.steps.all.size
      val versionsBefore = s.steps.stepVersions.all.size
      for {
        created <- s.env.run(
          s.milestones.createMilestone(create(List(NewMilestoneStepReq("Design", "", monday, friday))))
        )
        id = created.toOption.get.id
        _ = milestones.deleteMilestone(id)
      } yield {
        s.steps.steps.all.size shouldBe stepsBefore
        s.steps.stepVersions.all.size shouldBe versionsBefore
      }
    }
  }
}
