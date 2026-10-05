package com.togger.projectlabel

import com.togger.ProjectLabelSeedService
import com.togger.common.BusinessTgDbSpec
import doobie.implicits._
import doobie.postgres.implicits._

class ProjectLabelSeedServiceTest extends BusinessTgDbSpec {

  val projectLabelRepo = new ProjectLabelRepoImpl
  val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl

  testNameFromClassName should {

    "seed the default categories and one row per default language/framework/infrastructure when the table is empty" in {
      transactor =>
        val seedService = new ProjectLabelSeedService(transactor)

        for {
          _ <- seedService.run
          allCategories <- projectLabelCategoryRepo.findAll.transact(transactor)
          allProjectLabels <- projectLabelRepo.findAll.transact(transactor)
        } yield {
          allCategories.map(_.name).toSet shouldBe DefaultProjectLabelCategory.values.map(_.categoryName).toSet

          val categoryIdByName = allCategories.map(c => c.name -> c.id).toMap
          allProjectLabels.size shouldBe
            DefaultProgrammingLanguage.values.size + DefaultProgrammingFramework.values.size + DefaultInfrastructure.values.size
          allProjectLabels.count(
            _.categoryId == categoryIdByName(DefaultProjectLabelCategory.ProgrammingLanguage.categoryName)
          ) shouldBe DefaultProgrammingLanguage.values.size
          allProjectLabels.count(
            _.categoryId == categoryIdByName(DefaultProjectLabelCategory.Framework.categoryName)
          ) shouldBe DefaultProgrammingFramework.values.size
          allProjectLabels.count(
            _.categoryId == categoryIdByName(DefaultProjectLabelCategory.Infrastructure.categoryName)
          ) shouldBe DefaultInfrastructure.values.size
        }
    }

    "not duplicate categories or project labels when run again against an already-seeded table" in { transactor =>
      val seedService = new ProjectLabelSeedService(transactor)

      for {
        _ <- seedService.run
        _ <- seedService.run
        allCategories <- projectLabelCategoryRepo.findAll.transact(transactor)
        allProjectLabels <- projectLabelRepo.findAll.transact(transactor)
      } yield {
        allCategories.size shouldBe DefaultProjectLabelCategory.values.size
        allProjectLabels.size shouldBe
          DefaultProgrammingLanguage.values.size + DefaultProgrammingFramework.values.size + DefaultInfrastructure.values.size
      }
    }
  }

}
