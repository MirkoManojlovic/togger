package com.togger.techstack

import com.togger.TechStackSeedService
import com.togger.common.BusinessTgDbSpec
import doobie.implicits._
import doobie.postgres.implicits._

class TechStackSeedServiceTest extends BusinessTgDbSpec {

  val techStackRepo = new TechStackRepoImpl

  testNameFromClassName should {

    "seed one row per default language/framework when the table is empty" in { transactor =>
      val seedService = new TechStackSeedService(transactor)

      for {
        _ <- seedService.run
        allTechStacks <- techStackRepo.findAll.transact(transactor)
      } yield {
        allTechStacks.size shouldBe DefaultProgrammingLanguage.values.size + DefaultProgrammingFramework.values.size
        allTechStacks.count(_.category == TechStackCategory.Language) shouldBe DefaultProgrammingLanguage.values.size
        allTechStacks.count(_.category == TechStackCategory.Framework) shouldBe DefaultProgrammingFramework.values.size
      }
    }

    "not duplicate rows when run again against an already-seeded table" in { transactor =>
      val seedService = new TechStackSeedService(transactor)

      for {
        _ <- seedService.run
        _ <- seedService.run
        allTechStacks <- techStackRepo.findAll.transact(transactor)
      } yield allTechStacks.size shouldBe DefaultProgrammingLanguage.values.size + DefaultProgrammingFramework.values.size
    }
  }

}
