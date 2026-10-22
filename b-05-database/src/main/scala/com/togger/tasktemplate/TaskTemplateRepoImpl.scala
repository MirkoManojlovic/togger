package com.togger.tasktemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.task.TaskLabelRepoImpl.TASK_LABEL_TABLE_FRAG
import com.togger.tasktemplate.TaskTemplateRepoImpl.TASK_TEMPLATE_TABLE_FRAG
import com.togger.tasktemplate.TaskTemplateVersionRepoImpl.TASK_TEMPLATE_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TaskTemplateRepoImpl extends TaskTemplateRepo with TgLogging {

  private lazy val returnFields = List("id", "project_id", "task_label_id", "created_by", "created_at")

  override def insert(taskTemplate: TaskTemplate): ConnectionIO[TaskTemplate] = {
    import taskTemplate._
    logAroundInfoQuery(
      insertStatement(TASK_TEMPLATE_TABLE_FRAG)(fr"""
        (project_id, task_label_id, created_by, created_at)
        values ($projectId, $taskLabelId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TaskTemplate](returnFields: _*)
    )(s"Inserting ${sp(taskTemplate)}", saved => s"Task template inserted ${sp(saved)}")
  }

  override def insertWithId(taskTemplate: TaskTemplate): ConnectionIO[TaskTemplate] = {
    import taskTemplate._
    logAroundInfoQuery(
      insertStatement(TASK_TEMPLATE_TABLE_FRAG)(fr"""
        (id, project_id, task_label_id, created_by, created_at)
        values ($id, $projectId, $taskLabelId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TaskTemplate](returnFields: _*)
    )(
      s"Inserting ${sp(taskTemplate)} with existing id",
      saved => s"Task template inserted with existing id ${sp(saved)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[TaskTemplate] =
    logAroundInfoQuery(findByIdStatementUUID(TASK_TEMPLATE_TABLE_FRAG)(id).query[TaskTemplate].unique)(
      s"Finding task template with ${sp(id)}",
      found => s"Task template found ${sp(found)}"
    )

  override def findByLabelId(taskLabelId: UUID): ConnectionIO[Option[TaskTemplate]] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_TEMPLATE_TABLE_FRAG) ++ fr"where task_label_id = $taskLabelId")
        .query[TaskTemplate]
        .option
    )(
      s"Finding task template of label ${sp(taskLabelId)}",
      found => s"Task template of label ${sp(taskLabelId)} found ${sp(found)}"
    )

  override def updateLabel(id: UUID, taskLabelId: Option[UUID]): ConnectionIO[TaskTemplate] =
    logAroundInfoQuery(
      updateStatementUUID(TASK_TEMPLATE_TABLE_FRAG)(id)(fr"task_label_id = $taskLabelId").update
        .withUniqueGeneratedKeys[TaskTemplate](returnFields: _*)
    )(
      s"Linking task template ${sp(id)} to label ${sp(taskLabelId)}",
      updated => s"Task template label updated ${sp(updated)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TASK_TEMPLATE_TABLE_FRAG)(id).update.run)(
      s"Deleting task template with ${sp(id)}",
      _ => s"Task template with ${sp(id)} deleted"
    )

  override def findFullById(id: UUID): ConnectionIO[TaskTemplateFull] =
    logAroundInfoQuery(
      (fullSelect ++ fr"where t.id = $id").query[(TaskTemplate, TaskTemplateVersion, Option[String])].unique.map(toFull)
    )(
      s"Finding full task template with ${sp(id)}",
      found => s"Full task template found ${sp(found)}"
    )

  override def findFullByLabelId(taskLabelId: UUID): ConnectionIO[Option[TaskTemplateFull]] =
    logAroundInfoQuery(
      (fullSelect ++ fr"where t.task_label_id = $taskLabelId")
        .query[(TaskTemplate, TaskTemplateVersion, Option[String])]
        .option
        .map(_.map(toFull))
    )(
      s"Finding full task template of label ${sp(taskLabelId)}",
      found => s"Full task template of label ${sp(taskLabelId)} found ${sp(found)}"
    )

  override def findPaginatedFullByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskTemplateFull]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TaskTemplateFull](
        pageQuery = request =>
          (fullSelect ++ fr"where t.project_id = $projectId" ++
            PaginationSql.orderBy(fr"tv.name", fr"t.id") ++
            PaginationSql.limitOffset(request))
            .query[(TaskTemplate, TaskTemplateVersion, Option[String])]
            .map(toFull)
            .to[List],
        countQuery = (countStatement(TASK_TEMPLATE_TABLE_FRAG) ++ fr"where project_id = $projectId")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding task templates of ${sp(projectId)} with ${sp(page)}",
      found => s"Task templates found ${sp(found)} for ${sp(projectId)}"
    )

  private def toFull(row: (TaskTemplate, TaskTemplateVersion, Option[String])): TaskTemplateFull =
    TaskTemplateFull(template = row._1, latestVersion = row._2, taskLabelName = row._3)

  // The template, its latest version (via LATERAL) and the name of the linked label, if any.
  private def fullSelect: Fragment =
    fr"select t.id, t.project_id, t.task_label_id, t.created_by, t.created_at," ++
      fr"tv.version, tv.task_template_id, tv.name, tv.body, tv.created_at, l.name from" ++
      TASK_TEMPLATE_TABLE_FRAG ++ fr"t join lateral (select * from" ++ TASK_TEMPLATE_VERSION_TABLE_FRAG ++
      fr"where task_template_id = t.id order by version desc limit 1) tv on true" ++
      fr"left join" ++ TASK_LABEL_TABLE_FRAG ++ fr"l on l.id = t.task_label_id"
}

object TaskTemplateRepoImpl {
  val TASK_TEMPLATE_TABLE: String = "task_template"
  val TASK_TEMPLATE_TABLE_FRAG: Fragment = Fragment.const(TASK_TEMPLATE_TABLE)
}
