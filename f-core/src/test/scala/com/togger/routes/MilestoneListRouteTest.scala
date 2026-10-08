package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.ShowMilestonesPage

import java.util.UUID

class MilestoneListRouteTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()
  private val origin = "http://localhost:5173"

  private val routes = new TgRoutes().routes("")

  private def urlOf(page: ShowMilestonesPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[ShowMilestonesPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).collectFirst { case page: ShowMilestonesPage => page }

  testNameFromClassName should {

    "add no params to the path for the default list" in { _ =>
      urlOf(ShowMilestonesPage(projectId)).stripSuffix("?") shouldBe s"/show-milestones/$projectId"
    }

    "read the default list from the plain path, with the milestones in progress ticked" in { _ =>
      pageOf(s"/show-milestones/$projectId") shouldBe Some(ShowMilestonesPage(projectId))
      MilestoneListParams.default.selectedStatuses shouldBe Set("0")
    }

    "round trip page, size, statuses and search" in { _ =>
      val page = ShowMilestonesPage(
        projectId,
        MilestoneListParams(page = 3, size = 50, statuses = Some(Set("1", "2")), search = "release")
      )
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip an empty status selection apart from the default one" in { _ =>
      val none = ShowMilestonesPage(projectId, MilestoneListParams(statuses = Some(Set.empty)))
      pageOf(urlOf(none)) shouldBe Some(none)
      pageOf(urlOf(none)) should not be Some(ShowMilestonesPage(projectId))
    }

    "fall back to the defaults for bad page and size" in { _ =>
      pageOf(s"/show-milestones/$projectId?page=abc&size=7").map(_.list) shouldBe Some(MilestoneListParams.default)
      pageOf(s"/show-milestones/$projectId?page=0&size=-5").map(_.list) shouldBe Some(MilestoneListParams.default)
      pageOf(s"/show-milestones/$projectId?page=-3&size=1000").map(_.list) shouldBe Some(MilestoneListParams.default)
    }

    "accept a valid page and one of the size options" in { _ =>
      pageOf(s"/show-milestones/$projectId?page=2&size=10").map(_.list) shouldBe
        Some(MilestoneListParams.default.copy(page = 2, size = 10))
    }
  }
}
