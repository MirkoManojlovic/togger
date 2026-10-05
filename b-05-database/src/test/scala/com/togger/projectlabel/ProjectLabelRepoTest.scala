package com.togger.projectlabel

import cats.implicits.catsSyntaxApplicativeError
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class ProjectLabelRepoTest extends BusinessTgDbSpec with TimeHelpers {

  val projectLabelRepo = new ProjectLabelRepoImpl
  val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl

  val category1 = ProjectLabelCategory(id = UUID.randomUUID(), name = "Language", createdAt = nowAsInstant, updatedAt = nowAsInstant)
  val category2 = ProjectLabelCategory(id = UUID.randomUUID(), name = "Framework", createdAt = nowAsInstant, updatedAt = nowAsInstant)

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedCategory1 <- projectLabelCategoryRepo.insert(category1)
        savedCategory2 <- projectLabelCategoryRepo.insert(category2)
        projectLabel = ProjectLabel(
          id = UUID.randomUUID(),
          name = "Scala",
          description = "Statically typed JVM/JS/Native language blending OO and FP",
          categoryId = savedCategory1.id,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
        savedProjectLabel <- projectLabelRepo.insert(projectLabel)
        foundProjectLabel1 <- projectLabelRepo.findById(savedProjectLabel.id)
        newUpdatedAt = nowAsInstant
        updatedProjectLabel <- projectLabelRepo.update(
          savedProjectLabel.copy(name = "Scala 3", categoryId = savedCategory2.id),
          newUpdatedAt
        )
        foundProjectLabel2 <- projectLabelRepo.findById(savedProjectLabel.id)
        _ <- projectLabelRepo.deleteById(foundProjectLabel2.id)
        foundProjectLabel3 <- projectLabelRepo.findById(savedProjectLabel.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedProjectLabel shouldBe foundProjectLabel1)
        cp(updatedProjectLabel shouldBe foundProjectLabel2)
        cp(foundProjectLabel2.categoryId shouldBe savedCategory2.id)
        cp.reportAll()
        foundProjectLabel3.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
