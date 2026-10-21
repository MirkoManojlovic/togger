package com.togger.contribution

import cats.effect.IO
import com.togger.dto.{ContributionsDto, ContributionsFilter}
import com.togger.logging.TgLogging
import com.togger.project.{ProjectAccountRepo, ProjectRepo}
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class ContributionService(
  contributionRepo: ContributionRepo,
  projectRepo: ProjectRepo,
  projectAccountRepo: ProjectAccountRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def isMember(projectId: UUID, accountId: UUID): IO[Boolean] =
    projectAccountRepo.isMember(projectId, accountId).transact(transactor)

  def findByProjectId(projectId: UUID, filter: ContributionsFilter): IO[ContributionsDto] = {
    logger.info(s"Finding contributions of ${sp(projectId)} with ${sp(filter)}")
    (for {
      project <- projectRepo.findById(projectId)
      membersByProject <- projectAccountRepo.findAllMembersByProjectIds(Seq(projectId))
      milestones <- contributionRepo.milestoneHeaders(projectId)
      taskTotals <- contributionRepo.milestoneTaskTotals(projectId)
      activity <- contributionRepo.entryActivity(projectId, filter)
      assigned <- contributionRepo.tasksAssigned(projectId, filter)
      closed <- contributionRepo.tasksClosed(projectId, filter)
      statusCounts <- contributionRepo.tasksByStatus(projectId, filter)
      priorityCounts <- contributionRepo.tasksByPriority(projectId, filter)
      labelCounts <- contributionRepo.tasksByLabel(projectId, filter)
    } yield ContributionAssembler.assemble(
      projectId,
      membersByProject.getOrElse(projectId, Seq.empty),
      project.workDays,
      filter,
      ContributionAssembler.Rows(
        milestones,
        taskTotals,
        activity,
        assigned,
        closed,
        statusCounts,
        priorityCounts,
        labelCounts
      )
    )).transact(transactor)
  }
}
