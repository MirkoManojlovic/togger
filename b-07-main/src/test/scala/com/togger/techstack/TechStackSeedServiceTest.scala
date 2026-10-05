package com.togger.techstack

import com.togger.TechStackSeedService
import com.togger.common.BusinessTgDbSpec
import doobie.implicits._
import doobie.postgres.implicits._

class TechStackSeedServiceTest extends BusinessTgDbSpec {

  val techStackRepo = new TechStackRepoImpl
  val techStackCategoryRepo = new TechStackCategoryRepoImpl

  testNameFromClassName should {

    "seed the default categories and one row per default language/framework/infrastructure when the table is empty" in {
      transactor =>
        val seedService = new TechStackSeedService(transactor)

        for {
          _ <- seedService.run
          allCategories <- techStackCategoryRepo.findAll.transact(transactor)
          allTechStacks <- techStackRepo.findAll.transact(transactor)
        } yield {
          allCategories.map(_.name).toSet shouldBe DefaultTechStackCategory.values.map(_.categoryName).toSet

          val categoryIdByName = allCategories.map(c => c.name -> c.id).toMap
          allTechStacks.size shouldBe
            DefaultProgrammingLanguage.values.size + DefaultProgrammingFramework.values.size + DefaultInfrastructure.values.size
          allTechStacks.count(
            _.categoryId == categoryIdByName(DefaultTechStackCategory.Language.categoryName)
          ) shouldBe DefaultProgrammingLanguage.values.size
          allTechStacks.count(
            _.categoryId == categoryIdByName(DefaultTechStackCategory.Framework.categoryName)
          ) shouldBe DefaultProgrammingFramework.values.size
          allTechStacks.count(
            _.categoryId == categoryIdByName(DefaultTechStackCategory.Infrastructure.categoryName)
          ) shouldBe DefaultInfrastructure.values.size
        }
    }

    "not duplicate categories or tech stacks when run again against an already-seeded table" in { transactor =>
      val seedService = new TechStackSeedService(transactor)

      for {
        _ <- seedService.run
        _ <- seedService.run
        allCategories <- techStackCategoryRepo.findAll.transact(transactor)
        allTechStacks <- techStackRepo.findAll.transact(transactor)
      } yield {
        allCategories.size shouldBe DefaultTechStackCategory.values.size
        allTechStacks.size shouldBe
          DefaultProgrammingLanguage.values.size + DefaultProgrammingFramework.values.size + DefaultInfrastructure.values.size
      }
    }
  }

}
