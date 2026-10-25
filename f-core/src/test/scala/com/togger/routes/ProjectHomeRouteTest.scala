package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.ProjectHomePage

import java.util.UUID

class ProjectHomeRouteTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val routes = new TgRoutes().routes("")

  testNameFromClassName should {

    "round trip the page" in { _ =>
      routes.flatMap(_.relativeUrlForPage(ProjectHomePage(projectId))).head shouldBe s"/project-home/$projectId"
      routes.flatMap(_.pageForRelativeUrl(origin, s"/project-home/$projectId")).headOption shouldBe
        Some(ProjectHomePage(projectId))
    }
  }
}
