package com.togger.ui.contributions

import com.togger.dto.{MilestoneContributionsDto, TaskAttributesDto}
import com.togger.milestone.MilestoneStatus
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class MilestoneTabFilterTest extends AnyWordSpec with Matchers {

  private def milestone(name: String, status: Option[MilestoneStatus]) =
    MilestoneContributionsDto(
      status.map(_ => UUID.randomUUID()),
      name,
      status,
      None,
      None,
      0,
      0,
      List.empty,
      TaskAttributesDto.empty
    )

  private val none = milestone("No milestone", None)
  private val running = milestone("Running", Some(MilestoneStatus.InProgress))
  private val done = milestone("Done", Some(MilestoneStatus.Done))
  private val paused = milestone("Paused", Some(MilestoneStatus.Paused))
  private val milestones = Seq(none, running, done, paused)

  "MilestoneTabFilter" should {

    "offer every status, with only the ones in progress ticked" in {
      MilestoneTabFilter.items.map(_.name) shouldBe MilestoneStatus.values.sortBy(_.value).map(_.statusName)
      MilestoneTabFilter.items.filter(_.isSelected).map(_.id) shouldBe Seq(MilestoneStatus.InProgress.value.toString)
      MilestoneTabFilter.DefaultIds shouldBe Set(MilestoneStatus.InProgress.value.toString)
    }

    "show only the milestones in progress by default, and the tasks without a milestone" in {
      MilestoneTabFilter.visible(milestones, MilestoneTabFilter.DefaultIds) shouldBe Seq(none, running)
    }

    "show the milestones of every ticked status, in their order" in {
      val ids = Set(MilestoneStatus.InProgress, MilestoneStatus.Paused).map(MilestoneTabFilter.idOf)
      MilestoneTabFilter.visible(milestones, ids) shouldBe Seq(none, running, paused)
    }

    "show no milestone when no status is ticked, but still the tasks without one" in {
      MilestoneTabFilter.visible(milestones, Set.empty) shouldBe Seq(none)
    }

    "ignore ids that are not a status" in {
      MilestoneTabFilter.visible(milestones, Set("abc", MilestoneTabFilter.idOf(MilestoneStatus.Done))) shouldBe
        Seq(none, done)
    }
  }
}
