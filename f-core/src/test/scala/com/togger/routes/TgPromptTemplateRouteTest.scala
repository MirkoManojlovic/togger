package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.{CreateTgPromptTemplatePage, EditTgPromptTemplatePage, ShowAllTgPromptTemplatesPage}

import java.util.UUID

class TgPromptTemplateRouteTest extends AsyncJsWordSpec {

  private val promptId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val routes = new TgRoutes().routes("")

  private def urlOf(page: FrontendPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[FrontendPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).headOption

  testNameFromClassName should {

    "round trip the create page" in { _ =>
      urlOf(CreateTgPromptTemplatePage) shouldBe "/settings/prompt-template/create"
      pageOf("/settings/prompt-template/create") shouldBe Some(CreateTgPromptTemplatePage)
    }

    "round trip the edit page" in { _ =>
      val page = EditTgPromptTemplatePage(promptId)
      urlOf(page) shouldBe s"/settings/prompt-template/edit/$promptId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip the list page with its page and size" in { _ =>
      val page = ShowAllTgPromptTemplatesPage(ListParams.default.atPage(2, 10))
      pageOf(urlOf(page)) shouldBe Some(page)
    }
  }
}
