package com.togger

import cats.effect.IO
import cats.implicits._
import com.togger.common.{TgClockImpl, TgIds}
import com.togger.projectlabel.{
  DefaultInfrastructure,
  DefaultProgrammingFramework,
  DefaultProgrammingLanguage,
  DefaultProjectLabelCategory,
  ProjectLabel,
  ProjectLabelCategory,
  ProjectLabelCategoryRepoImpl,
  ProjectLabelRepoImpl
}
import doobie.Transactor
import doobie.implicits._
import java.util.UUID

class ProjectLabelSeedService(transactor: Transactor[IO]) {
  private val projectLabelRepo = new ProjectLabelRepoImpl
  private val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl
  private val tgClock = new TgClockImpl

  def run: IO[Unit] =
    for {
      categoryIdsByName <- ensureDefaultCategories
      existingProjectLabels <- projectLabelRepo.findAll.transact(transactor)
      _ <- if (existingProjectLabels.isEmpty) seedProjectLabels(categoryIdsByName) else IO.unit
    } yield ()

  private def ensureDefaultCategories: IO[Map[String, UUID]] = {
    val now = tgClock.currentInstant
    for {
      existing <- projectLabelCategoryRepo.findAll.transact(transactor)
      existingByName = existing.map(c => c.name -> c.id).toMap
      missing = DefaultProjectLabelCategory.values.filterNot(c => existingByName.contains(c.categoryName))
      inserted <- missing.toList
        .traverse(c =>
          projectLabelCategoryRepo.insert(
            ProjectLabelCategory(id = TgIds.UNASSIGNED_UUID, name = c.categoryName, createdAt = now, updatedAt = now)
          )
        )
        .transact(transactor)
    } yield existingByName ++ inserted.map(c => c.name -> c.id).toMap
  }

  private def seedProjectLabels(categoryIdsByName: Map[String, UUID]): IO[Unit] = {
    val now = tgClock.currentInstant

    val languageRows = DefaultProgrammingLanguage.values.map(l =>
      ProjectLabel(
        id = TgIds.UNASSIGNED_UUID,
        name = l.languageName,
        description = l.description,
        categoryId = categoryIdsByName(DefaultProjectLabelCategory.Language.categoryName),
        createdAt = now,
        updatedAt = now
      )
    )

    val frameworkRows = DefaultProgrammingFramework.values.map(f =>
      ProjectLabel(
        id = TgIds.UNASSIGNED_UUID,
        name = f.frameworkName,
        description = f.description,
        categoryId = categoryIdsByName(DefaultProjectLabelCategory.Framework.categoryName),
        createdAt = now,
        updatedAt = now
      )
    )

    val infrastructureRows = DefaultInfrastructure.values.map(i =>
      ProjectLabel(
        id = TgIds.UNASSIGNED_UUID,
        name = i.infrastructureName,
        description = i.description,
        categoryId = categoryIdsByName(DefaultProjectLabelCategory.Infrastructure.categoryName),
        createdAt = now,
        updatedAt = now
      )
    )

    (languageRows ++ frameworkRows ++ infrastructureRows).toList.traverse(projectLabelRepo.insert).transact(transactor).void
  }
}
