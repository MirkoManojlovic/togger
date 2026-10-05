package com.togger.project

import cats.data.NonEmptyList
import cats.implicits._
import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.project.ProjectLabelAssignmentRepoImpl.PROJECT_LABEL_ASSIGNMENT_TABLE_FRAG
import com.togger.projectlabel.ProjectLabel
import com.togger.projectlabel.ProjectLabelRepoImpl.PROJECT_LABEL_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragments

import java.util.UUID

class ProjectLabelAssignmentRepoImpl extends ProjectLabelAssignmentRepo with TgLogging {

  override def insert(projectLabelAssignment: ProjectLabelAssignment): ConnectionIO[ProjectLabelAssignment] = {
    import projectLabelAssignment._
    logAroundInfoQuery(
      insertStatement(PROJECT_LABEL_ASSIGNMENT_TABLE_FRAG)(fr"""
        (project_id, project_label_id, created_at)
        values ($projectId, $projectLabelId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[ProjectLabelAssignment]("project_id", "project_label_id", "created_at")
    )(
      s"Inserting ${sp(projectLabelAssignment)}",
      projectLabelAssignment => s"ProjectLabelAssignment inserted ${sp(projectLabelAssignment)}"
    )
  }

  override def deleteAllByProjectId(projectId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(PROJECT_LABEL_ASSIGNMENT_TABLE_FRAG) ++ fr"where project_id = $projectId").update.run
    )(
      s"Deleting project project labels with ${sp(projectId)}",
      numDeleted => s"Project project labels deleted ${sp(numDeleted)} for ${sp(projectId)}"
    )

  override def findProjectLabelsByProjectId(projectId: UUID): ConnectionIO[List[ProjectLabel]] =
    logAroundInfoQuery(
      (selectAllStatement(PROJECT_LABEL_ASSIGNMENT_TABLE_FRAG) ++
        innerJoin(PROJECT_LABEL_TABLE_FRAG)(fr"project_label_assignment.project_label_id = project_label.id") ++
        fr"where project_label_assignment.project_id = $projectId")
        .query[(ProjectLabelAssignment, ProjectLabel)]
        .map(_._2)
        .to[List]
    )(
      s"Finding project labels for ${sp(projectId)}",
      projectLabels => s"Project labels found ${sp(projectLabels)} for ${sp(projectId)}"
    )

  override def findProjectLabelsByProjectIds(projectIds: Seq[UUID]): ConnectionIO[Map[UUID, List[ProjectLabel]]] =
    NonEmptyList.fromList(projectIds.toList) match {
      case None => Map.empty[UUID, List[ProjectLabel]].pure[ConnectionIO]
      case Some(nonEmptyProjectIds) =>
        logAroundInfoQuery(
          (selectAllStatement(PROJECT_LABEL_ASSIGNMENT_TABLE_FRAG) ++
            innerJoin(PROJECT_LABEL_TABLE_FRAG)(fr"project_label_assignment.project_label_id = project_label.id") ++
            fr"where" ++ fragments.in(fr"project_label_assignment.project_id", nonEmptyProjectIds))
            .query[(ProjectLabelAssignment, ProjectLabel)]
            .to[List]
            .map(_.groupMap(_._1.projectId)(_._2))
        )(
          s"Finding project labels for ${sp(projectIds)}",
          projectLabelsByProjectId => s"Project labels found ${sp(projectLabelsByProjectId)}"
        )
    }
}

object ProjectLabelAssignmentRepoImpl {
  val PROJECT_LABEL_ASSIGNMENT_TABLE: String = "project_label_assignment"
  val PROJECT_LABEL_ASSIGNMENT_TABLE_FRAG: Fragment = Fragment.const(PROJECT_LABEL_ASSIGNMENT_TABLE)
}
