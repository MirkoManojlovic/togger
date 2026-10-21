package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.ShowContributionsPage

import java.time.LocalDate
import java.util.UUID

class ContributionsRouteTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()
  private val origin = "http://localhost:5173"

  private val routes = new TgRoutes().routes("")

  private def urlOf(page: ShowContributionsPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[ShowContributionsPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).collectFirst { case page: ShowContributionsPage => page }

  private val range = ContributionsParams(Some(LocalDate.of(2026, 10, 1)), Some(LocalDate.of(2026, 10, 31)))

  testNameFromClassName should {

    "add no params to the path for every day" in { _ =>
      urlOf(ShowContributionsPage(projectId)).stripSuffix("?") shouldBe s"/contributions/$projectId"
    }

    "read every day from the plain path" in { _ =>
      pageOf(s"/contributions/$projectId") shouldBe Some(ShowContributionsPage(projectId))
    }

    "round trip a range" in { _ =>
      val page = ShowContributionsPage(projectId, range)
      urlOf(page) should include("from=2026-10-01")
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip a range with only one end" in { _ =>
      val from = ShowContributionsPage(projectId, ContributionsParams(from = range.from))
      val to = ShowContributionsPage(projectId, ContributionsParams(to = range.to))
      pageOf(urlOf(from)) shouldBe Some(from)
      pageOf(urlOf(to)) shouldBe Some(to)
    }

    "leave out days that are not dates and ranges that end before they start" in { _ =>
      pageOf(s"/contributions/$projectId?from=yesterday&to=2026-10-31").map(_.params) shouldBe
        Some(ContributionsParams(to = range.to))
      pageOf(s"/contributions/$projectId?from=2026-10-31&to=2026-10-01").map(_.params) shouldBe
        Some(ContributionsParams.default)
    }
  }
}
