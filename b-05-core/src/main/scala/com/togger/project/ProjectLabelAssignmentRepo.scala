package com.togger.project

import com.togger.projectlabel.ProjectLabel
import doobie.ConnectionIO

import java.util.UUID

trait ProjectLabelAssignmentRepo {
  def insert(projectLabelAssignment: ProjectLabelAssignment): ConnectionIO[ProjectLabelAssignment]

  def deleteAllByProjectId(projectId: UUID): ConnectionIO[Int]

  def findProjectLabelsByProjectId(projectId: UUID): ConnectionIO[List[ProjectLabel]]

  def findProjectLabelsByProjectIds(projectIds: Seq[UUID]): ConnectionIO[Map[UUID, List[ProjectLabel]]]
}
