package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.{CreateMilestoneTemplatePage, EditMilestoneTemplatePage, ShowAllMilestoneTemplatesPage}

import java.util.UUID

class MilestoneTemplateRouteTest extends AsyncJsWordSpec {

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
      urlOf(CreateMilestoneTemplatePage(projectId)) shouldBe s"/create-milestone-template/$projectId"
      pageOf(s"/create-milestone-template/$projectId") shouldBe Some(CreateMilestoneTemplatePage(projectId))
    }

    "round trip the edit page" in { _ =>
      val page = EditMilestoneTemplatePage(projectId, templateId)
      urlOf(page) shouldBe s"/edit-milestone-template/project-id/$projectId/milestone-template-id/$templateId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip the list page with its paging" in { _ =>
      val page = ShowAllMilestoneTemplatesPage(projectId)
      urlOf(page) should startWith(s"/milestone-template/$projectId")
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "not mix up the step template pages with the milestone template pages" in { _ =>
      pageOf(s"/milestone-step-template/$projectId") should not be Some(ShowAllMilestoneTemplatesPage(projectId))
    }
  }
}
