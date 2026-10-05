package com.togger.techstack

import cats.effect.IO
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TechStackCategoryService(
  techStackCategoryRepo: TechStackCategoryRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createTechStackCategory(techStackCategory: TechStackCategory): IO[TechStackCategory] = {
    logger.info(s"Creating tech stack category ${sp(techStackCategory)}")
    (for {
      savedTechStackCategory <- techStackCategoryRepo.insert(techStackCategory)
    } yield {
      logger.info(s"Tech stack category created successfully ${sp(savedTechStackCategory)}")
      savedTechStackCategory
    }).transact(transactor)
  }

  def findAll: IO[List[TechStackCategory]] =
    techStackCategoryRepo.findAll.transact(transactor)

  def findById(id: UUID): IO[TechStackCategory] =
    (for {
      techStackCategory <- techStackCategoryRepo.findById(id)
    } yield techStackCategory).transact(transactor)

  def updateTechStackCategory(techStackCategory: TechStackCategory): IO[TechStackCategory] = {
    logger.info(s"Updating ${sp(techStackCategory)}")
    val now = tgClock.currentInstant
    (for {
      updatedTechStackCategory <- techStackCategoryRepo.update(techStackCategory, now)
    } yield {
      logger.info(s"Tech stack category updated successfully ${sp(updatedTechStackCategory)}")
      updatedTechStackCategory
    }).transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    techStackCategoryRepo.deleteById(id).transact(transactor)
}
