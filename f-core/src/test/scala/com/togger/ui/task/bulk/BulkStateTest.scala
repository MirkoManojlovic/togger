package com.togger.ui.task.bulk

import com.togger.dto.FindTaskStatusResp
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.task.TaskPriority
import com.togger.ui.task.TaskProjectData
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate
import java.util.UUID

class BulkStateTest extends AnyWordSpec with Matchers {

  private val today = LocalDate.of(2026, 10, 7)
  private val projectId = UUID.randomUUID()
  private val project = OrganizationProjectResp(projectId, "P", "", "🚀", Set(1, 2, 3, 4, 5))
  private val status = FindTaskStatusResp(UUID.randomUUID(), projectId, "📚 Backlog", "", "")
  private val loaded = TaskProjectData(statuses = Seq(status))

  private val unloaded = BulkState(today, projectId = Some(projectId))
  private val edited = {
    val task = BulkTask.withDefaults(UUID.randomUUID(), "A", unloaded.defaults)
    task.copy(priority = TaskPriority.High, statusId = Some(UUID.randomUUID()), labelIds = Set(UUID.randomUUID()))
  }

  "BulkState.afterDefaultsChange" should {
    "set the tasks back to the defaults when the defaults change, as for a new project" in {
      val state = unloaded.copy(projects = Seq(project), projectData = loaded, tasks = Seq(edited))
      val result = state.afterDefaultsChange.tasks.head
      (result.priority, result.statusId, result.labelIds) shouldBe (TaskPriority.Medium, Some(status.id), Set.empty)
    }

    "keep the attributes of a restored draft while the project data has not arrived" in {
      val restored = unloaded.copy(tasks = Seq(edited), awaitingRestoredData = true)
      restored.afterDefaultsChange shouldBe restored
    }

    "keep them while only the projects, or only the project data, have arrived" in {
      val onlyProjects = unloaded.copy(projects = Seq(project), tasks = Seq(edited), awaitingRestoredData = true)
      val onlyData = unloaded.copy(projectData = loaded, tasks = Seq(edited), awaitingRestoredData = true)
      (onlyProjects.afterDefaultsChange, onlyData.afterDefaultsChange) shouldBe (onlyProjects, onlyData)
    }

    "clean up a restored draft once everything has arrived, and from then on follow the defaults" in {
      val restored =
        unloaded.copy(projects = Seq(project), projectData = loaded, tasks = Seq(edited), awaitingRestoredData = true)
      val result = restored.afterDefaultsChange
      (
        result.awaitingRestoredData,
        result.tasks.head.priority,
        result.tasks.head.statusId,
        result.tasks.head.labelIds
      ) shouldBe
        (false, TaskPriority.High, Some(status.id), Set.empty)
    }

    "follow the defaults again once the user picks a project" in {
      val restored = unloaded.copy(tasks = Seq(edited), awaitingRestoredData = true)
      restored.withProject(UUID.randomUUID()).awaitingRestoredData shouldBe false
    }
  }

  "BulkState.draft" should {
    "hold the project, the text and the tasks" in {
      val state = unloaded.copy(rawInput = "- A", tasks = Seq(edited))
      state.draft shouldBe BulkDraft(Some(projectId), "- A", Seq(edited))
    }
  }
}
