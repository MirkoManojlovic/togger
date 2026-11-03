package com.togger.milestone

import doobie.ConnectionIO

import java.util.UUID

trait MilestoneStepRepo {

  def insert(milestoneStep: MilestoneStep): ConnectionIO[MilestoneStep]
  def insertWithId(milestoneStep: MilestoneStep): ConnectionIO[MilestoneStep]

  def findById(id: UUID): ConnectionIO[MilestoneStep]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** Not paginated on purpose: the steps of one milestone are a short, user maintained list. */
  def findAllByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneStep]]

  /** Every step of every milestone of the project: a project has a short list of them per milestone. */
  def findAllByProjectId(projectId: UUID): ConnectionIO[List[MilestoneStep]]

  /** The milestone of each of the steps; a step that does not exist is absent from the result. */
  def findMilestoneIdsByStepIds(stepIds: List[UUID]): ConnectionIO[Map[UUID, UUID]]

  /** The project of each of the steps (through its milestone); a step that does not exist is absent. */
  def findProjectIdsByStepIds(stepIds: List[UUID]): ConnectionIO[Map[UUID, UUID]]

  /** The tasks (by their latest version) in each of the steps; a step without tasks is absent from the result. */
  def countTasksByStepIds(stepIds: List[UUID]): ConnectionIO[Map[UUID, MilestoneStepTaskCounts]]

  /** The tasks (by their latest version) in any step of the milestone. */
  /** The milestones without a Shelf step, with the dates of their latest version and their number of steps. */
  def findMilestonesWithoutShelf: ConnectionIO[List[MilestoneWithoutShelf]]

  def countTasksOfMilestone(milestoneId: UUID): ConnectionIO[Int]

}

case class MilestoneWithoutShelf(
  milestoneId: UUID,
  startDate: java.time.LocalDate,
  dueDate: java.time.LocalDate,
  stepCount: Int
)
