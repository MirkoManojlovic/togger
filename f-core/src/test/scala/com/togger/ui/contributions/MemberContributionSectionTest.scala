package com.togger.ui.contributions

import com.togger.dto.{ContributionsDto, MemberContributionDto, MilestoneContributionsDto, TaskAttributesDto}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class MemberContributionSectionTest extends AnyWordSpec with Matchers {

  private def member(name: String, tasksAssigned: Int = 0) =
    MemberContributionDto(
      UUID.randomUUID(),
      name,
      List.empty,
      tasksAssigned,
      0,
      0,
      0,
      List.empty,
      TaskAttributesDto.empty
    )

  private def milestone(name: String, members: List[MemberContributionDto]) =
    MilestoneContributionsDto(
      Some(UUID.randomUUID()),
      name,
      None,
      None,
      None,
      None,
      0,
      0,
      members,
      TaskAttributesDto.empty
    )

  private val ann = member("Ann Adams")
  private val bob = member("Bob Brown")
  private val annInAlpha = ann.copy(tasksAssigned = 2)
  private val alpha = milestone("Alpha", List(annInAlpha, bob))
  private val beta = milestone("Beta", List(bob))
  private val gamma = milestone("Gamma", List(ann.copy(tasksAssigned = 5)))
  private val contributions =
    ContributionsDto(UUID.randomUUID(), List(ann, bob), TaskAttributesDto.empty, List(gamma, alpha, beta))

  "MemberContributionSection.milestonesOf" should {

    "list the milestones the member did something in, with their numbers there, in the order of the milestones" in {
      MemberContributionSection.milestonesOf(contributions, ann.memberId) shouldBe
        Seq(gamma -> gamma.members.head, alpha -> annInAlpha)
    }

    "leave out the milestones the member did nothing in" in {
      MemberContributionSection.milestonesOf(contributions, bob.memberId).map(_._1.name) shouldBe Seq("Alpha", "Beta")
    }

    "list nothing for someone who is not in any milestone" in {
      MemberContributionSection.milestonesOf(contributions, UUID.randomUUID()) shouldBe empty
    }
  }
}
