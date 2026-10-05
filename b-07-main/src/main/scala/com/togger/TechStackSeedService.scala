package com.togger

import cats.effect.IO
import cats.implicits._
import com.togger.common.{TgClockImpl, TgIds}
import com.togger.techstack.{
  DefaultInfrastructure,
  DefaultProgrammingFramework,
  DefaultProgrammingLanguage,
  TechStack,
  TechStackCategory,
  TechStackRepoImpl
}
import doobie.Transactor
import doobie.implicits._

class TechStackSeedService(transactor: Transactor[IO]) {
  private val techStackRepo = new TechStackRepoImpl
  private val tgClock = new TgClockImpl

  def run: IO[Unit] =
    for {
      existing <- techStackRepo.findAll.transact(transactor)
      _ <- if (existing.isEmpty) seed else IO.unit
    } yield ()

  private def seed: IO[Unit] = {
    val now = tgClock.currentInstant

    val languageRows = DefaultProgrammingLanguage.values.map(l =>
      TechStack(
        id = TgIds.UNASSIGNED_UUID,
        name = l.languageName,
        description = l.description,
        category = TechStackCategory.Language,
        createdAt = now,
        updatedAt = now
      )
    )

    val frameworkRows = DefaultProgrammingFramework.values.map(f =>
      TechStack(
        id = TgIds.UNASSIGNED_UUID,
        name = f.frameworkName,
        description = f.description,
        category = TechStackCategory.Framework,
        createdAt = now,
        updatedAt = now
      )
    )

    val infrastructureRows = DefaultInfrastructure.values.map(i =>
      TechStack(
        id = TgIds.UNASSIGNED_UUID,
        name = i.infrastructureName,
        description = i.description,
        category = TechStackCategory.Infrastructure,
        createdAt = now,
        updatedAt = now
      )
    )

    (languageRows ++ frameworkRows ++ infrastructureRows).toList.traverse(techStackRepo.insert).transact(transactor).void
  }
}
