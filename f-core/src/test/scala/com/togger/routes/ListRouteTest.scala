package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.{
  ShowAllOrganizationsPage,
  ShowAllProjectLabelCategoriesPage,
  ShowAllProjectLabelsPage,
  ShowAllTaskLabelsPage,
  ShowAllTaskStatusesPage,
  ShowAllTaskTemplatesPage,
  ShowProjectsForOrganizationIdTable,
  ShowTimeboxesPage
}

import java.util.UUID

class ListRouteTest extends AsyncJsWordSpec {

  private val id = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val routes = new TgRoutes().routes("")

  private def urlOf(page: FrontendPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[FrontendPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).headOption

  private val listPages: Seq[(String, String, ListParams => FrontendPage)] = Seq(
    ("timeboxes", s"/show-timeboxes/$id", ShowTimeboxesPage(id, _)),
    ("projects table", s"/project/table/organization/$id", ShowProjectsForOrganizationIdTable(id, _)),
    ("organizations", "/organization", ShowAllOrganizationsPage(_)),
    ("task statuses", s"/task-status/$id", ShowAllTaskStatusesPage(id, _)),
    ("task labels", s"/task-label/$id", ShowAllTaskLabelsPage(id, _)),
    ("task templates", s"/task-template/$id", ShowAllTaskTemplatesPage(id, _)),
    ("project labels", "/settings/project-label", ShowAllProjectLabelsPage(_)),
    ("project label categories", "/settings/project-label-category", ShowAllProjectLabelCategoriesPage(_))
  )

  testNameFromClassName should {

    listPages.foreach { case (name, path, pageWith) =>
      s"add no params to the $name path for the default list" in { _ =>
        urlOf(pageWith(ListParams.default)).stripSuffix("?") shouldBe path
      }

      s"read the default $name list from the plain path" in { _ =>
        pageOf(path) shouldBe Some(pageWith(ListParams.default))
      }

      s"round trip page and size of the $name list" in { _ =>
        val page = pageWith(ListParams(page = 3, size = 50))
        pageOf(urlOf(page)) shouldBe Some(page)
      }

      s"fall back to the defaults for a bad page and size of the $name list" in { _ =>
        pageOf(s"$path?page=abc&size=7") shouldBe Some(pageWith(ListParams.default))
        pageOf(s"$path?page=0&size=-5") shouldBe Some(pageWith(ListParams.default))
        pageOf(s"$path?page=-3&size=1000") shouldBe Some(pageWith(ListParams.default))
      }

      s"accept a valid page and one of the size options of the $name list" in { _ =>
        pageOf(s"$path?page=2&size=10") shouldBe Some(pageWith(ListParams(page = 2, size = 10)))
      }
    }

    "keep an organization page apart from the organization list" in { _ =>
      pageOf(s"/organization/$id") should not be Some(ShowAllOrganizationsPage())
    }
  }
}
