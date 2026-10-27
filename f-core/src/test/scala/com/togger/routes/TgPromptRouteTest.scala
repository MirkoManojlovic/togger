package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.{CreateTgPromptPage, EditTgPromptPage, ShowAllTgPromptsPage}

import java.util.UUID

class TgPromptRouteTest extends AsyncJsWordSpec {

  private val promptId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val routes = new TgRoutes().routes("")

  private def urlOf(page: FrontendPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[FrontendPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).headOption

  testNameFromClassName should {

    "round trip the create page" in { _ =>
      urlOf(CreateTgPromptPage) shouldBe "/settings/prompt/create"
      pageOf("/settings/prompt/create") shouldBe Some(CreateTgPromptPage)
    }

    "round trip the edit page" in { _ =>
      val page = EditTgPromptPage(promptId)
      urlOf(page) shouldBe s"/settings/prompt/edit/$promptId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip the list page with its page and size" in { _ =>
      val page = ShowAllTgPromptsPage(ListParams.default.atPage(2, 10))
      pageOf(urlOf(page)) shouldBe Some(page)
    }
  }
}
