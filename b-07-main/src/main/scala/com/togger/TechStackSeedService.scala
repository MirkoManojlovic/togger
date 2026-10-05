package com.togger

import cats.effect.IO
import cats.implicits._
import com.togger.common.{TgClockImpl, TgIds}
import com.togger.techstack.{
  DefaultInfrastructure,
  DefaultProgrammingFramework,
  DefaultProgrammingLanguage,
  DefaultTechStackCategory,
  TechStack,
  TechStackCategory,
  TechStackCategoryRepoImpl,
  TechStackRepoImpl
}
import doobie.Transactor
import doobie.implicits._
import java.util.UUID

class TechStackSeedService(transactor: Transactor[IO]) {
  private val techStackRepo = new TechStackRepoImpl
  private val techStackCategoryRepo = new TechStackCategoryRepoImpl
  private val tgClock = new TgClockImpl

  def run: IO[Unit] =
    for {
      categoryIdsByName <- ensureDefaultCategories
      existingTechStacks <- techStackRepo.findAll.transact(transactor)
      _ <- if (existingTechStacks.isEmpty) seedTechStacks(categoryIdsByName) else IO.unit
    } yield ()

  private def ensureDefaultCategories: IO[Map[String, UUID]] = {
    val now = tgClock.currentInstant
    for {
      existing <- techStackCategoryRepo.findAll.transact(transactor)
      existingByName = existing.map(c => c.name -> c.id).toMap
      missing = DefaultTechStackCategory.values.filterNot(c => existingByName.contains(c.categoryName))
      inserted <- missing.toList
        .traverse(c =>
          techStackCategoryRepo.insert(
            TechStackCategory(id = TgIds.UNASSIGNED_UUID, name = c.categoryName, createdAt = now, updatedAt = now)
          )
        )
        .transact(transactor)
    } yield existingByName ++ inserted.map(c => c.name -> c.id).toMap
  }

  private def seedTechStacks(categoryIdsByName: Map[String, UUID]): IO[Unit] = {
    val now = tgClock.currentInstant

    val languageRows = DefaultProgrammingLanguage.values.map(l =>
      TechStack(
        id = TgIds.UNASSIGNED_UUID,
        name = l.languageName,
        description = l.description,
        categoryId = categoryIdsByName(DefaultTechStackCategory.Language.categoryName),
        createdAt = now,
        updatedAt = now
      )
    )

    val frameworkRows = DefaultProgrammingFramework.values.map(f =>
      TechStack(
        id = TgIds.UNASSIGNED_UUID,
        name = f.frameworkName,
        description = f.description,
        categoryId = categoryIdsByName(DefaultTechStackCategory.Framework.categoryName),
        createdAt = now,
        updatedAt = now
      )
    )

    val infrastructureRows = DefaultInfrastructure.values.map(i =>
      TechStack(
        id = TgIds.UNASSIGNED_UUID,
        name = i.infrastructureName,
        description = i.description,
        categoryId = categoryIdsByName(DefaultTechStackCategory.Infrastructure.categoryName),
        createdAt = now,
        updatedAt = now
      )
    )

    (languageRows ++ frameworkRows ++ infrastructureRows).toList.traverse(techStackRepo.insert).transact(transactor).void
  }
}
