package com.togger.techstack

import cats.implicits.catsSyntaxApplicativeError
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TechStackRepoTest extends BusinessTgDbSpec with TimeHelpers {

  val techStackRepo = new TechStackRepoImpl

  val techStack = TechStack(
    id = UUID.randomUUID(),
    name = "Scala",
    description = "Statically typed JVM/JS/Native language blending OO and FP",
    category = TechStackCategory.Language,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedTechStack <- techStackRepo.insert(techStack)
        foundTechStack1 <- techStackRepo.findById(savedTechStack.id)
        newUpdatedAt = nowAsInstant
        updatedTechStack <- techStackRepo.update(
          savedTechStack.copy(name = "Scala 3", category = TechStackCategory.Framework),
          newUpdatedAt
        )
        foundTechStack2 <- techStackRepo.findById(savedTechStack.id)
        _ <- techStackRepo.deleteById(foundTechStack2.id)
        foundTechStack3 <- techStackRepo.findById(savedTechStack.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedTechStack shouldBe foundTechStack1)
        cp(updatedTechStack shouldBe foundTechStack2)
        cp(foundTechStack2.category shouldBe TechStackCategory.Framework)
        cp.reportAll()
        foundTechStack3.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
