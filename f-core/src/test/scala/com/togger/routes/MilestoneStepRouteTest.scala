package com.togger.routes

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.{CreateMilestoneStepPage, EditMilestoneStepPage, ShowMilestoneStepsPage}

import java.util.UUID

class MilestoneStepRouteTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()
  private val milestoneId = UUID.randomUUID()
  private val stepId = UUID.randomUUID()
  private val origin = "http://localhost:5173"
  private val routes = new TgRoutes().routes()

  private def urlOf(page: FrontendPage): String =
    routes.flatMap(_.relativeUrlForPage(page)).head

  private def pageOf(url: String): Option[FrontendPage] =
    routes.flatMap(_.pageForRelativeUrl(origin, url)).headOption

  testNameFromClassName should {

    "round trip the steps page" in { _ =>
      val page = ShowMilestoneStepsPage(projectId, milestoneId)
      urlOf(page) shouldBe s"/milestone-steps/project-id/$projectId/milestone-id/$milestoneId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip the add step page" in { _ =>
      val page = CreateMilestoneStepPage(projectId, milestoneId)
      urlOf(page) shouldBe s"/create-milestone-step/project-id/$projectId/milestone-id/$milestoneId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }

    "round trip the edit step page" in { _ =>
      val page = EditMilestoneStepPage(projectId, stepId)
      urlOf(page) shouldBe s"/edit-milestone-step/project-id/$projectId/milestone-step-id/$stepId"
      pageOf(urlOf(page)) shouldBe Some(page)
    }
  }
}
