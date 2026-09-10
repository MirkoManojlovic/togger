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
        encode = (page: CreateTaskPage) => page.projectId.toString,
        decode = (projectId: String) => CreateTaskPage(UUID.fromString(projectId)),
        pattern = root / "create-task" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: CreateTaskStatusPage) => page.projectId.toString,
        decode = (projectId: String) => CreateTaskStatusPage(UUID.fromString(projectId)),
        pattern = root / "create-task-status" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: EditTaskStatusPage) => page.projectId.toString -> page.taskStatusId.toString,
        decode = { (projectIdAndTaskStatusId: (String, String)) =>
          EditTaskStatusPage(
            UUID.fromString(projectIdAndTaskStatusId._1),
            UUID.fromString(projectIdAndTaskStatusId._2)
          )
        },
        pattern = root / "edit-task-status" / "project-id" / segment[String] / "task-status-id" / segment[
          String
        ] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: EditTaskPage) => page.projectId.toString -> page.taskId.toString,
        decode = { (projectIdAndTaskId: (String, String)) =>
          EditTaskPage(UUID.fromString(projectIdAndTaskId._1), UUID.fromString(projectIdAndTaskId._2))
        },
        pattern = root / "edit-task" / "project-id" / segment[String] / "task-id" / segment[String] / endOfSegments
//        basePath = basePath
      ),
      Route(
        encode = (page: ShowAllTasksPage) => page.projectId.toString,
        decode = (projectId: String) => ShowAllTasksPage(UUID.fromString(projectId)),
        pattern = root / "show-tasks" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: ShowAllTaskStatusesPage) => page.projectId.toString,
        decode = (projectId: String) => ShowAllTaskStatusesPage(UUID.fromString(projectId)),
        pattern = root / "task-status" / segment[String] / endOfSegments
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
        encode = (page: EditProjectPage) => page.id.toString,
        decode = (id: String) => EditProjectPage(UUID.fromString(id)),
        pattern = root / "project" / segment[String] / endOfSegments
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
      ),
      Route(
        encode = (page: ShowAllTaskConfigsPage) => page.projectId.toString,
        decode = (projectId: String) => ShowAllTaskConfigsPage(UUID.fromString(projectId)),
        pattern = root / "task-config" / "project" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: CreateTaskDumpConfigPage) => page.projectId.toString,
        decode = (projectId: String) => CreateTaskDumpConfigPage(UUID.fromString(projectId)),
        pattern = root / "task-dump-config" / "create" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditTaskDumpConfigPage) => page.id.toString,
        decode = (id: String) => EditTaskDumpConfigPage(UUID.fromString(id)),
        pattern = root / "task-dump-config" / "edit" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: TaskDumpStatusPage) => page.projectId.toString,
        decode = (projectId: String) => TaskDumpStatusPage(UUID.fromString(projectId)),
        pattern = root / "task-dump-status" / "project" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: CreateTaskLoadConfigPage) => page.projectId.toString,
        decode = (projectId: String) => CreateTaskLoadConfigPage(UUID.fromString(projectId)),
        pattern = root / "task-load-config" / "create" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditTaskLoadConfigPage) => page.id.toString,
        decode = (id: String) => EditTaskLoadConfigPage(UUID.fromString(id)),
        pattern = root / "task-load-config" / "edit" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: CreateProjectMappingPage) => page.projectId.toString,
        decode = (projectId: String) => CreateProjectMappingPage(UUID.fromString(projectId)),
        pattern = root / "project-mapping" / "create" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: CreateMilestonePage) => page.projectId.toString,
        decode = (projectId: String) => CreateMilestonePage(UUID.fromString(projectId)),
        pattern = root / "create-milestone" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditMilestonePage) => page.id.toString,
        decode = (id: String) => EditMilestonePage(UUID.fromString(id)),
        pattern = root / "edit-milestone" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: ShowMilestonesPage) => page.projectId.toString,
        decode = (projectId: String) => ShowMilestonesPage(UUID.fromString(projectId)),
        pattern = root / "show-milestones" / segment[String] / endOfSegments
      )
    )

}
