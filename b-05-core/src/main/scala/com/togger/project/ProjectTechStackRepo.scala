package com.togger.project

import com.togger.techstack.TechStack
import doobie.ConnectionIO

import java.util.UUID

trait ProjectTechStackRepo {
  def insert(projectTechStack: ProjectTechStack): ConnectionIO[ProjectTechStack]

  def deleteAllByProjectId(projectId: UUID): ConnectionIO[Int]

  def findTechStacksByProjectId(projectId: UUID): ConnectionIO[List[TechStack]]

  def findTechStacksByProjectIds(projectIds: Seq[UUID]): ConnectionIO[Map[UUID, List[TechStack]]]
}
