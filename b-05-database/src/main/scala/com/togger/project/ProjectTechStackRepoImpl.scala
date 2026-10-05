package com.togger.project

import cats.data.NonEmptyList
import cats.implicits._
import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.project.ProjectTechStackRepoImpl.PROJECT_TECH_STACK_TABLE_FRAG
import com.togger.techstack.TechStack
import com.togger.techstack.TechStackRepoImpl.TECH_STACK_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragments

import java.util.UUID

class ProjectTechStackRepoImpl extends ProjectTechStackRepo with TgLogging {

  override def insert(projectTechStack: ProjectTechStack): ConnectionIO[ProjectTechStack] = {
    import projectTechStack._
    logAroundInfoQuery(
      insertStatement(PROJECT_TECH_STACK_TABLE_FRAG)(fr"""
        (project_id, tech_stack_id, created_at)
        values ($projectId, $techStackId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[ProjectTechStack]("project_id", "tech_stack_id", "created_at")
    )(
      s"Inserting ${sp(projectTechStack)}",
      projectTechStack => s"ProjectTechStack inserted ${sp(projectTechStack)}"
    )
  }

  override def deleteAllByProjectId(projectId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(PROJECT_TECH_STACK_TABLE_FRAG) ++ fr"where project_id = $projectId").update.run
    )(
      s"Deleting project tech stacks with ${sp(projectId)}",
      numDeleted => s"Project tech stacks deleted ${sp(numDeleted)} for ${sp(projectId)}"
    )

  override def findTechStacksByProjectId(projectId: UUID): ConnectionIO[List[TechStack]] =
    logAroundInfoQuery(
      (selectAllStatement(PROJECT_TECH_STACK_TABLE_FRAG) ++
        innerJoin(TECH_STACK_TABLE_FRAG)(fr"project_tech_stack.tech_stack_id = tech_stack.id") ++
        fr"where project_tech_stack.project_id = $projectId")
        .query[(ProjectTechStack, TechStack)]
        .map(_._2)
        .to[List]
    )(
      s"Finding tech stacks for ${sp(projectId)}",
      techStacks => s"Tech stacks found ${sp(techStacks)} for ${sp(projectId)}"
    )

  override def findTechStacksByProjectIds(projectIds: Seq[UUID]): ConnectionIO[Map[UUID, List[TechStack]]] =
    NonEmptyList.fromList(projectIds.toList) match {
      case None => Map.empty[UUID, List[TechStack]].pure[ConnectionIO]
      case Some(nonEmptyProjectIds) =>
        logAroundInfoQuery(
          (selectAllStatement(PROJECT_TECH_STACK_TABLE_FRAG) ++
            innerJoin(TECH_STACK_TABLE_FRAG)(fr"project_tech_stack.tech_stack_id = tech_stack.id") ++
            fr"where" ++ fragments.in(fr"project_tech_stack.project_id", nonEmptyProjectIds))
            .query[(ProjectTechStack, TechStack)]
            .to[List]
            .map(_.groupMap(_._1.projectId)(_._2))
        )(
          s"Finding tech stacks for ${sp(projectIds)}",
          techStacksByProjectId => s"Tech stacks found ${sp(techStacksByProjectId)}"
        )
    }
}

object ProjectTechStackRepoImpl {
  val PROJECT_TECH_STACK_TABLE: String = "project_tech_stack"
  val PROJECT_TECH_STACK_TABLE_FRAG: Fragment = Fragment.const(PROJECT_TECH_STACK_TABLE)
}
