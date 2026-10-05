package com.togger.projectlabel

import cats.effect.IO
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class ProjectLabelCategoryService(
  projectLabelCategoryRepo: ProjectLabelCategoryRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createProjectLabelCategory(projectLabelCategory: ProjectLabelCategory): IO[ProjectLabelCategory] = {
    logger.info(s"Creating project label category ${sp(projectLabelCategory)}")
    (for {
      savedProjectLabelCategory <- projectLabelCategoryRepo.insert(projectLabelCategory)
    } yield {
      logger.info(s"Project label category created successfully ${sp(savedProjectLabelCategory)}")
      savedProjectLabelCategory
    }).transact(transactor)
  }

  def findAll: IO[List[ProjectLabelCategory]] =
    projectLabelCategoryRepo.findAll.transact(transactor)

  def findById(id: UUID): IO[ProjectLabelCategory] =
    (for {
      projectLabelCategory <- projectLabelCategoryRepo.findById(id)
    } yield projectLabelCategory).transact(transactor)

  def updateProjectLabelCategory(projectLabelCategory: ProjectLabelCategory): IO[ProjectLabelCategory] = {
    logger.info(s"Updating ${sp(projectLabelCategory)}")
    val now = tgClock.currentInstant
    (for {
      updatedProjectLabelCategory <- projectLabelCategoryRepo.update(projectLabelCategory, now)
    } yield {
      logger.info(s"Project label category updated successfully ${sp(updatedProjectLabelCategory)}")
      updatedProjectLabelCategory
    }).transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    projectLabelCategoryRepo.deleteById(id).transact(transactor)
}
