package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.CreateMilestoneStepTemplatePage
import com.togger.routes.Pages.EditMilestoneStepTemplatePage
import com.togger.routes.Pages.ShowAllMilestoneStepTemplatesPage

import java.util.UUID

class MilestoneStepTemplateRouteTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()
  private val templateId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val routes = new TgRoutes().routes()

  private def urlOf(page: FrontendPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[FrontendPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).headOption

  testNameFromClassName should {

    "round trip the create page" in { _ =>
      urlOf(CreateMilestoneStepTemplatePage(projectId)) shouldBe s"/create-milestone-step-template/$projectId"
      pageOf(s"/create-milestone-step-template/$projectId") shouldBe Some(CreateMilestoneStepTemplatePage(projectId))
    }

    "round trip the edit page" in { _ =>
      val page = EditMilestoneStepTemplatePage(projectId, templateId)
      urlOf(page) shouldBe
        s"/edit-milestone-step-template/project-id/$projectId/milestone-step-template-id/$templateId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip the list page with its paging" in { _ =>
      val page = ShowAllMilestoneStepTemplatesPage(projectId)
      urlOf(page) should startWith(s"/milestone-step-template/$projectId")
      pageOf(urlOf(page)) shouldBe Some(page)
    }
  }
}
