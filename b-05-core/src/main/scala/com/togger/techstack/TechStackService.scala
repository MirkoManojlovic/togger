package com.togger.techstack

import cats.effect.IO
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TechStackService(
  techStackRepo: TechStackRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createTechStack(techStack: TechStack): IO[TechStack] = {
    logger.info(s"Creating tech stack ${sp(techStack)}")
    (for {
      savedTechStack <- techStackRepo.insert(techStack)
    } yield {
      logger.info(s"Tech stack created successfully ${sp(savedTechStack)}")
      savedTechStack
    }).transact(transactor)
  }

  def findAll: IO[List[TechStack]] =
    techStackRepo.findAll.transact(transactor)

  def findById(id: UUID): IO[TechStack] =
    (for {
      techStack <- techStackRepo.findById(id)
    } yield techStack).transact(transactor)

  def updateTechStack(techStack: TechStack): IO[TechStack] = {
    logger.info(s"Updating ${sp(techStack)}")
    val now = tgClock.currentInstant
    (for {
      updatedTechStack <- techStackRepo.update(techStack, now)
    } yield {
      logger.info(s"Tech stack updated successfully ${sp(updatedTechStack)}")
      updatedTechStack
    }).transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    techStackRepo.deleteById(id).transact(transactor)
}
