package com.togger.techstack

import cats.implicits.catsSyntaxApplicativeError
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TechStackRepoTest extends BusinessTgDbSpec with TimeHelpers {

  val techStackRepo = new TechStackRepoImpl
  val techStackCategoryRepo = new TechStackCategoryRepoImpl

  val category1 = TechStackCategory(id = UUID.randomUUID(), name = "Language", createdAt = nowAsInstant, updatedAt = nowAsInstant)
  val category2 = TechStackCategory(id = UUID.randomUUID(), name = "Framework", createdAt = nowAsInstant, updatedAt = nowAsInstant)

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedCategory1 <- techStackCategoryRepo.insert(category1)
        savedCategory2 <- techStackCategoryRepo.insert(category2)
        techStack = TechStack(
          id = UUID.randomUUID(),
          name = "Scala",
          description = "Statically typed JVM/JS/Native language blending OO and FP",
          categoryId = savedCategory1.id,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
        savedTechStack <- techStackRepo.insert(techStack)
        foundTechStack1 <- techStackRepo.findById(savedTechStack.id)
        newUpdatedAt = nowAsInstant
        updatedTechStack <- techStackRepo.update(
          savedTechStack.copy(name = "Scala 3", categoryId = savedCategory2.id),
          newUpdatedAt
        )
        foundTechStack2 <- techStackRepo.findById(savedTechStack.id)
        _ <- techStackRepo.deleteById(foundTechStack2.id)
        foundTechStack3 <- techStackRepo.findById(savedTechStack.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedTechStack shouldBe foundTechStack1)
        cp(updatedTechStack shouldBe foundTechStack2)
        cp(foundTechStack2.categoryId shouldBe savedCategory2.id)
        cp.reportAll()
        foundTechStack3.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
