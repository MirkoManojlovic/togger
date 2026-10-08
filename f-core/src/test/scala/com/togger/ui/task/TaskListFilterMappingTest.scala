package com.togger.ui.task

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.TgIds
import com.togger.dto.TaskFilter
import com.togger.routes.TaskListParams
import com.togger.task.TaskPriority

import java.util.UUID

class TaskListFilterMappingTest extends AsyncJsWordSpec {

  private val idA = UUID.randomUUID()
  private val idB = UUID.randomUUID()

  private def selection(all: Set[String], selected: Set[String]) = FilterSelection(all, selected)
  private val nothing = selection(Set.empty, Set.empty)

  private def filterOf(
    statuses: FilterSelection = nothing,
    priorities: FilterSelection = nothing,
    labels: FilterSelection = nothing,
    assignees: FilterSelection = nothing,
    milestones: FilterSelection = nothing,
    search: String = ""
  ) = TaskListFilterMapping.toTaskFilter(statuses, priorities, labels, assignees, milestones, search)

  testNameFromClassName should {

    "not filter when nothing is ticked" in { _ =>
      filterOf(statuses = selection(Set(idA.toString), Set.empty)) shouldBe TaskFilter.empty
    }

    "not filter when everything is ticked" in { _ =>
      filterOf(labels = selection(Set(idA.toString, idB.toString), Set(idA.toString, idB.toString))) shouldBe TaskFilter.empty
    }

    "filter by the ticked ids when some are unticked" in { _ =>
      filterOf(statuses = selection(Set(idA.toString, idB.toString), Set(idA.toString))) shouldBe
        TaskFilter(statusIds = List(idA))
    }

    "map priority values to priorities" in { _ =>
      filterOf(priorities = selection(Set("1", "2", "3"), Set("1", "3"))) shouldBe
        TaskFilter(priorities = List(TaskPriority.Critical, TaskPriority.Medium))
    }

    "map the not assigned and no milestone options to the backend markers" in { _ =>
      val filter = filterOf(
        assignees = selection(Set(TaskListFilterMapping.UnassignedId, idA.toString), Set(TaskListFilterMapping.UnassignedId)),
        milestones = selection(Set(TaskListFilterMapping.NoMilestoneId, idB.toString), Set(TaskListFilterMapping.NoMilestoneId))
      )
      filter.assigneeIds shouldBe List(TgIds.UNASSIGNED_UUID)
      filter.milestoneIds shouldBe List(TgIds.NO_MILESTONE_UUID)
    }

    "trim the search and drop a blank one" in { _ =>
      filterOf(search = "  fix ").search shouldBe Some("fix")
      filterOf(search = "   ").search shouldBe None
    }

    "put nothing in the URL for the default selection" in { _ =>
      TaskListFilterMapping.toParamSelection(Set("a"), Set("a")) shouldBe None
      TaskListFilterMapping.toParamSelection(Set("a", "b"), Set("a")) shouldBe Some(Set("a", "b"))
      TaskListFilterMapping.toParamSelection(Set.empty, Set("a")) shouldBe Some(Set.empty)
    }

    "go back to the first page when the search changes" in { _ =>
      TaskListFilterMapping.withSearch(TaskListParams.default.copy(page = 4), " x ") shouldBe
        TaskListParams.default.copy(page = 1, search = "x")
    }
  }
}
