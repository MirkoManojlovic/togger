package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.{CreateTaskTemplatePage, EditTaskTemplatePage}

import java.util.UUID

class TaskTemplateRouteTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()
  private val templateId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val routes = new TgRoutes().routes("")

  private def urlOf(page: FrontendPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[FrontendPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).headOption

  testNameFromClassName should {

    "round trip the create page" in { _ =>
      urlOf(CreateTaskTemplatePage(projectId)) shouldBe s"/create-task-template/$projectId"
      pageOf(s"/create-task-template/$projectId") shouldBe Some(CreateTaskTemplatePage(projectId))
    }

    "round trip the edit page" in { _ =>
      val page = EditTaskTemplatePage(projectId, templateId)
      urlOf(page) shouldBe s"/edit-task-template/project-id/$projectId/task-template-id/$templateId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }
  }
}
