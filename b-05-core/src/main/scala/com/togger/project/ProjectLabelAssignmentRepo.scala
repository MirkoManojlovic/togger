package com.togger.project

import com.togger.projectlabel.ProjectLabel
import doobie.ConnectionIO

import java.util.UUID

trait ProjectLabelAssignmentRepo {
  def insert(projectLabelAssignment: ProjectLabelAssignment): ConnectionIO[ProjectLabelAssignment]

  def deleteAllByProjectId(projectId: UUID): ConnectionIO[Int]

  /** Bounded by caller: the labels assigned to one project, embedded in that project's payload. */
  def findProjectLabelsByProjectId(projectId: UUID): ConnectionIO[List[ProjectLabel]]

  /** Bounded by caller (the ids come from a page of projects): the labels assigned to each of them. */
  def findProjectLabelsByProjectIds(projectIds: Seq[UUID]): ConnectionIO[Map[UUID, List[ProjectLabel]]]
}
