package com.togger.demo

import com.togger.milestone.MilestoneStepStatus
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}

class DemoMilestoneStepsTest extends AnyWordSpec with Matchers {

  private val today = LocalDate.of(2026, 6, 15)
  private val demo = DemoDataSet.build(today, Instant.parse("2026-06-15T10:00:00Z"))

  "The steps of the demo milestones" should {

    "be three for every milestone, in order, spread over its dates" in {
      demo.milestones.nonEmpty shouldBe true
      demo.milestones.foreach { milestone =>
        milestone.steps.map(_.name) shouldBe List("Design", "Development", "Production")
        milestone.steps.map(_.position) shouldBe List(0, 1, 2)
        milestone.steps.forall(step => !step.startDate.isBefore(milestone.startDate)) shouldBe true
        milestone.steps.forall(step => !step.dueDate.isAfter(milestone.dueDate)) shouldBe true
        milestone.steps.forall(step => !step.dueDate.isBefore(step.startDate)) shouldBe true
        milestone.steps.sliding(2).forall { case List(a, b) => !b.startDate.isBefore(a.dueDate) case _ => true } shouldBe true
      }
    }

    "have an end date exactly when they are closed" in {
      demo.milestones.flatMap(_.steps).foreach(step => step.status.isClosing shouldBe step.endDate.isDefined)
    }

    "have unique ids" in {
      val steps = demo.milestones.flatMap(_.steps)
      steps.map(_.id).distinct should have size steps.size
      steps.map(_.version).distinct should have size steps.size
    }

    "follow today: ended steps are done and later ones are planned" in {
      val open = demo.milestones.filter(m => m.status == com.togger.milestone.MilestoneStatus.InProgress)
      val steps = open.flatMap(_.steps)
      steps.filter(_.dueDate.isBefore(today)).foreach(_.status shouldBe MilestoneStepStatus.Done)
      steps.filter(_.startDate.isAfter(today)).foreach(_.status shouldBe MilestoneStepStatus.Planned)
    }

    "close the foundations milestone early, with its last step still open" in {
      val foundations = demo.milestones.find(_.id == DemoMilestonesData.FoundationsId).get
      foundations.steps.map(_.status) shouldBe
        List(MilestoneStepStatus.Done, MilestoneStepStatus.Done, MilestoneStepStatus.InProgress)
    }

    "hold the demo tasks of a milestone in one of its steps, in the project of the milestone" in {
      val stepsById = demo.milestones.flatMap(m => m.steps.map(_.id -> m)).toMap
      val inSteps = demo.tasks.flatMap(task => task.versions.map(task -> _)).filter(_._2.milestoneStepId.isDefined)

      inSteps.nonEmpty shouldBe true
      inSteps.foreach { case (task, version) =>
        val milestone = stepsById(version.milestoneStepId.get)
        version.milestone shouldBe Some(milestone.id)
        milestone.projectId shouldBe task.projectId
      }
    }

    "give every task that names a milestone a step, and no step to the others" in {
      demo.tasks.flatMap(_.versions).foreach(version => version.milestone.isDefined shouldBe version.milestoneStepId.isDefined)
    }

    "put some tasks of a milestone in each of several of its steps" in {
      val betaSteps = demo.tasks
        .flatMap(_.versions)
        .filter(_.milestone.contains(DemoMilestonesData.BetaId))
        .flatMap(_.milestoneStepId)
        .toSet

      betaSteps.size should be > 1
    }
  }
}
