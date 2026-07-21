package com.togger.routes

import com.raquo.waypoint._
import com.togger.routes.Pages._

import java.util.UUID

class TgRoutes {

  def routes(basePath: String): Seq[Route[_ <: FrontendPage, _ <: Any]] =
    Seq(
      Route.static(
        HomePage,
        root / endOfSegments
//        basePath = basePath
      ),
      Route.static(
        LoginPage,
        root / "login"
      ),
      Route.static(
        ChangePasswordPage,
        root / "change-password"
      ),
      Route(
        encode = (page: CreateTaskPage) => page.projectId,
        decode = (projectId: Long) => CreateTaskPage(projectId),
        pattern = root / "create-task" / segment[Long] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: CreateTaskStatusPage) => page.projectId,
        decode = (projectId: Long) => CreateTaskStatusPage(projectId),
        pattern = root / "create-task-status" / segment[Long] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: EditTaskStatusPage) => page.projectId -> page.taskStatusId.toString,
        decode = { (projectIdAndTaskStatusId: (Long, String)) =>
          EditTaskStatusPage(projectIdAndTaskStatusId._1, UUID.fromString(projectIdAndTaskStatusId._2))
        },
        pattern = root / "edit-task-status" / "project-id" / segment[Long] / "task-status-id" / segment[
          String
        ] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: EditTaskPage) => page.projectId -> page.taskId.toString,
        decode = { (projectIdAndTaskId: (Long, String)) =>
          EditTaskPage(projectIdAndTaskId._1, UUID.fromString(projectIdAndTaskId._2))
        },
        pattern = root / "edit-task" / "project-id" / segment[Long] / "task-id" / segment[String] / endOfSegments
//        basePath = basePath
      ),
      Route(
        encode = (page: ShowAllTasksPage) => page.projectId,
        decode = (projectId: Long) => ShowAllTasksPage(projectId),
        pattern = root / "show-tasks" / segment[Long] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: ShowAllTaskStatusesPage) => page.projectId,
        decode = (projectId: Long) => ShowAllTaskStatusesPage(projectId),
        pattern = root / "task-status" / segment[Long] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: CreateProjectPage) => page.organizationId.toString,
        decode = (organizationId: String) => CreateProjectPage(UUID.fromString(organizationId)),
        pattern = root / "create-project" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: ShowProjectsForOrganizationIdTable) => page.id.toString,
        decode = (id: String) => ShowProjectsForOrganizationIdTable(UUID.fromString(id)),
        pattern = root / "project" / "table" / "organization" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: ShowProjectsForOrganizationIdStatus) => page.id.toString,
        decode = (id: String) => ShowProjectsForOrganizationIdStatus(UUID.fromString(id)),
        pattern = root / "project" / "status" / "organization" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: EditProjectPage) => page.id,
        decode = (id: Long) => EditProjectPage(id),
        pattern = root / "project" / segment[Long] / endOfSegments
        //        basePath = basePath
      ),
      Route.static(
        CreateOrganizationPage,
        root / "organization" / "create"
        //        basePath = basePath
      ),
      Route.static(
        ShowAllOrganizationsPage,
        root / "organization" / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: EditOrganizationPage) => page.id.toString,
        decode = (id: String) => EditOrganizationPage(UUID.fromString(id)),
        pattern = root / "organization" / segment[String] / endOfSegments
        //        basePath = basePath
      )
    )

}
