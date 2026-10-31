package com.togger.ui.components

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages._

import java.util.UUID

class TgBreadcrumbTrailTest extends AsyncJsWordSpec {

  private val projectId = UUID.randomUUID()

  testNameFromClassName should {

    "have no trail for the login page" in { _ =>
      TgBreadcrumbTrail.forPage(LoginPage) shouldBe empty
    }

    "link a project-scoped page back to the project's tasks" in { _ =>
      TgBreadcrumbTrail.forPage(CreateTaskPage(projectId)).map(_.page) shouldBe
        Seq(
          Some(ShowAllOrganizationsPage()),
          Some(ProjectHomePage(projectId)),
          Some(ShowAllTasksPage(projectId)),
          None
        )
    }

    "lead the project home back to the organizations" in { _ =>
      TgBreadcrumbTrail.forPage(ProjectHomePage(projectId)).map(_.label) shouldBe Seq("Organizations", "Project")
    }

    "label the last crumb after the page" in { _ =>
      TgBreadcrumbTrail.forPage(ShowAllTasksPage(projectId)).map(_.label) shouldBe Seq("Organizations", "Project", "Tasks")
    }

    "put the contributions under the project" in { _ =>
      TgBreadcrumbTrail.forPage(ShowContributionsPage(projectId)).map(_.label) shouldBe
        Seq("Organizations", "Project", "Contributions")
    }

  }

}
