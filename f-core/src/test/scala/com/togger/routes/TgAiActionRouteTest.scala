package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.{CreateTgAiActionPage, EditTgAiActionPage, ShowAllTgAiActionsPage, TgAiActionDefaultsPage}

import java.util.UUID

class TgAiActionRouteTest extends AsyncJsWordSpec {

  private val actionId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val routes = new TgRoutes().routes("")

  private def urlOf(page: FrontendPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[FrontendPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).headOption

  testNameFromClassName should {

    "round trip the create page" in { _ =>
      urlOf(CreateTgAiActionPage) shouldBe "/settings/ai-action/create"
      pageOf("/settings/ai-action/create") shouldBe Some(CreateTgAiActionPage)
    }

    "round trip the defaults page" in { _ =>
      urlOf(TgAiActionDefaultsPage) shouldBe "/settings/ai-action/defaults"
      pageOf("/settings/ai-action/defaults") shouldBe Some(TgAiActionDefaultsPage)
    }

    "round trip the edit page" in { _ =>
      val page = EditTgAiActionPage(actionId)
      urlOf(page) shouldBe s"/settings/ai-action/edit/$actionId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip the list page with its page and size" in { _ =>
      val page = ShowAllTgAiActionsPage(ListParams.default.atPage(2, 10))
      pageOf(urlOf(page)) shouldBe Some(page)
    }
  }
}
