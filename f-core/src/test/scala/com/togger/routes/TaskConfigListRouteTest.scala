package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.ShowAllTaskConfigsPage

import java.util.UUID

class TaskConfigListRouteTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val path = s"/task-config/project/$projectId"

  private val routes = new TgRoutes().routes("")

  private def urlOf(page: ShowAllTaskConfigsPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[ShowAllTaskConfigsPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).collectFirst { case page: ShowAllTaskConfigsPage => page }

  testNameFromClassName should {

    "add no params to the path for the default lists" in { _ =>
      urlOf(ShowAllTaskConfigsPage(projectId)).stripSuffix("?") shouldBe path
    }

    "read the default lists from the plain path" in { _ =>
      pageOf(path) shouldBe Some(ShowAllTaskConfigsPage(projectId))
    }

    "round trip the page and size of both lists apart from each other" in { _ =>
      val page = ShowAllTaskConfigsPage(
        projectId,
        TaskConfigListParams(export = ListParams(page = 3, size = 50), importData = ListParams(page = 2, size = 10))
      )
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "move one list without moving the other" in { _ =>
      val moved = TaskConfigListParams.default.exportAtPage(4, 20).importAtPage(2, 10)
      (moved, TaskConfigListParams.default.importAtPage(3, 20).export) shouldBe
        (TaskConfigListParams(ListParams(4, 20), ListParams(2, 10)), ListParams.default)
    }

    "fall back to the defaults for a bad page and size" in { _ =>
      pageOf(s"$path?exportPage=abc&exportSize=7&loadPage=0&importSize=1000").map(_.list) shouldBe
        Some(TaskConfigListParams.default)
    }

    "accept a valid page and one of the size options" in { _ =>
      pageOf(s"$path?exportPage=2&exportSize=10").map(_.list) shouldBe
        Some(TaskConfigListParams(export = ListParams(page = 2, size = 10)))
    }
  }
}
