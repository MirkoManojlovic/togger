package com.togger.ui.backendstatus

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.routes.Pages.BackendStatusPage
import com.togger.routes.TgRoutes
import com.togger.ui.components.TgBreadcrumbTrail

class BackendStatusModelTest extends AsyncJsWordSpec {

  private def control(
    app: StatusApp,
    running: Boolean = true,
    version: Option[String] = Some("0.4.0"),
    pid: Option[Int] = Some(123),
    mismatch: Boolean = false,
    stopped: Boolean = false
  ) = ControlStatus(app.id, running, version, pid, "0.4.0", mismatch, stopped, 0, None)

  testNameFromClassName should {
    "show a running app with its version" in { _ =>
      val view = BackendStatusModel.view(StatusApp.Mcp, Some(control(StatusApp.Mcp)), None)
      view.state shouldBe AppState.Running("0.4.0")
      BackendStatusModel.statusText(view) shouldBe "Running"
      BackendStatusModel.versionText(view) shouldBe "0.4.0"
      view.pid shouldBe Some(123)
    }

    "show an app that does not answer as not running, or stopped when the user stopped it" in { _ =>
      val down = BackendStatusModel.view(StatusApp.Backend, Some(control(StatusApp.Backend, false, None)), None)
      BackendStatusModel.statusText(down) shouldBe "Not running"
      down.pid shouldBe None
      val stopped =
        BackendStatusModel.view(StatusApp.Backend, Some(control(StatusApp.Backend, false, None, stopped = true)), None)
      BackendStatusModel.statusText(stopped) shouldBe "Stopped"
    }

    "carry the version mismatch of the desktop app" in { _ =>
      val view = BackendStatusModel.view(StatusApp.Backend, Some(control(StatusApp.Backend, mismatch = true)), None)
      view.versionMismatch shouldBe true
      BackendStatusComponent.mismatchText(view) should include("0.4.0")
    }

    "use the public health of the backend when there is no desktop app" in { _ =>
      BackendStatusModel.view(StatusApp.Backend, None, None).state shouldBe AppState.Checking
      BackendStatusModel.view(StatusApp.Backend, None, Some(Some("1.0"))).state shouldBe AppState.Running("1.0")
      BackendStatusModel.view(StatusApp.Backend, None, Some(None)).state shouldBe AppState.Down
    }

    "not know the MCP server without the desktop app" in { _ =>
      BackendStatusModel.view(StatusApp.Mcp, None, Some(Some("1.0"))).state shouldBe AppState.Unknown
    }

    "be a public page that is reachable at /status with no breadcrumbs" in { _ =>
      BackendStatusPage.needsAuth shouldBe false
      TgBreadcrumbTrail.forPage(BackendStatusPage) shouldBe empty
      val routes = new TgRoutes().routes()
      routes.flatMap(_.relativeUrlForPage(BackendStatusPage)).head shouldBe "/status"
      routes.flatMap(_.pageForRelativeUrl("http://localhost:5173", "/status")) should contain(BackendStatusPage)
    }
  }
}
