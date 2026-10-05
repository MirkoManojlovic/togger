package com.togger.techstack

import cats.implicits.catsSyntaxApplicativeError
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TechStackCategoryRepoTest extends BusinessTgDbSpec with TimeHelpers {

  val techStackCategoryRepo = new TechStackCategoryRepoImpl

  val techStackCategory = TechStackCategory(
    id = UUID.randomUUID(),
    name = "Language",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedTechStackCategory <- techStackCategoryRepo.insert(techStackCategory)
        foundTechStackCategory1 <- techStackCategoryRepo.findById(savedTechStackCategory.id)
        newUpdatedAt = nowAsInstant
        updatedTechStackCategory <- techStackCategoryRepo.update(
          savedTechStackCategory.copy(name = "Programming Language"),
          newUpdatedAt
        )
        foundTechStackCategory2 <- techStackCategoryRepo.findById(savedTechStackCategory.id)
        _ <- techStackCategoryRepo.deleteById(foundTechStackCategory2.id)
        foundTechStackCategory3 <- techStackCategoryRepo.findById(savedTechStackCategory.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedTechStackCategory shouldBe foundTechStackCategory1)
        cp(updatedTechStackCategory shouldBe foundTechStackCategory2)
        cp.reportAll()
        foundTechStackCategory3.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
