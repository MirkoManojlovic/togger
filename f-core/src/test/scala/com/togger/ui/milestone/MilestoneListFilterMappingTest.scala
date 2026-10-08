package com.togger.ui.milestone

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.dto.MilestoneFilter
import com.togger.milestone.MilestoneStatus
import com.togger.routes.MilestoneListParams

class MilestoneListFilterMappingTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "filter by the milestones in progress for the default list" in { _ =>
      MilestoneListFilterMapping.toMilestoneFilter(MilestoneListParams.default) shouldBe
        MilestoneFilter(statuses = List(MilestoneStatus.InProgress))
    }

    "not filter when nothing is ticked" in { _ =>
      MilestoneListFilterMapping.toMilestoneFilter(MilestoneListParams(statuses = Some(Set.empty))) shouldBe
        MilestoneFilter.empty
    }

    "not filter when every status is ticked" in { _ =>
      val all = MilestoneStatus.values.map(_.value.toString).toSet
      MilestoneListFilterMapping.toMilestoneFilter(MilestoneListParams(statuses = Some(all))) shouldBe
        MilestoneFilter.empty
    }

    "filter by the ticked statuses" in { _ =>
      MilestoneListFilterMapping.toMilestoneFilter(MilestoneListParams(statuses = Some(Set("1", "3")))) shouldBe
        MilestoneFilter(statuses = List(MilestoneStatus.Done, MilestoneStatus.Abandoned))
    }

    "ignore unknown status values" in { _ =>
      MilestoneListFilterMapping.toMilestoneFilter(MilestoneListParams(statuses = Some(Set("1", "42")))) shouldBe
        MilestoneFilter(statuses = List(MilestoneStatus.Done))
    }

    "send a trimmed search and drop a blank one" in { _ =>
      MilestoneListFilterMapping.toMilestoneFilter(MilestoneListParams(search = "  release ")).search shouldBe
        Some("release")
      MilestoneListFilterMapping.toMilestoneFilter(MilestoneListParams(search = "   ")).search shouldBe None
    }

    "keep the default status selection out of the URL" in { _ =>
      MilestoneListFilterMapping.toParamSelection(MilestoneListParams.DefaultStatuses) shouldBe None
      MilestoneListFilterMapping.toParamSelection(Set("1")) shouldBe Some(Set("1"))
      MilestoneListFilterMapping.toParamSelection(Set.empty) shouldBe Some(Set.empty[String])
    }
  }
}
