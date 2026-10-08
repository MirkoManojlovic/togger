package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.ShowAllTasksPage

import java.util.UUID

class TaskListRouteTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val statusId = UUID.randomUUID().toString
  private val otherId = UUID.randomUUID().toString

  private val routes = new TgRoutes().routes("")

  private def urlOf(page: ShowAllTasksPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[ShowAllTasksPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).collectFirst { case page: ShowAllTasksPage => page }

  testNameFromClassName should {

    "add no params to the path for the default list" in { _ =>
      urlOf(ShowAllTasksPage(projectId)) should startWith(s"/show-tasks/$projectId")
      urlOf(ShowAllTasksPage(projectId)).stripSuffix("?") shouldBe s"/show-tasks/$projectId"
    }

    "round trip page, size, filters and search" in { _ =>
      val params = TaskListParams(
        page = 3,
        size = 50,
        statuses = Some(Set(statusId, otherId)),
        priorities = Some(Set.empty),
        labels = Some(Set(statusId)),
        assignees = Some(Set(otherId)),
        milestones = Some(Set("none")),
        search = "fix login"
      )
      val page = ShowAllTasksPage(projectId, params)
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "read the default list from the plain path" in { _ =>
      pageOf(s"/show-tasks/$projectId") shouldBe Some(ShowAllTasksPage(projectId))
    }

    "fall back to the defaults for bad page and size" in { _ =>
      pageOf(s"/show-tasks/$projectId?page=abc&size=7").map(_.list) shouldBe Some(TaskListParams.default)
      pageOf(s"/show-tasks/$projectId?page=0&size=-5").map(_.list) shouldBe Some(TaskListParams.default)
      pageOf(s"/show-tasks/$projectId?page=-3&size=1000").map(_.list) shouldBe Some(TaskListParams.default)
    }

    "accept a valid page and one of the size options" in { _ =>
      pageOf(s"/show-tasks/$projectId?page=2&size=10").map(_.list) shouldBe
        Some(TaskListParams.default.copy(page = 2, size = 10))
    }
  }
}
