package com.togger.projectlabel

import cats.implicits.catsSyntaxApplicativeError
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class ProjectLabelCategoryRepoTest extends BusinessTgDbSpec with TimeHelpers {

  val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl

  val projectLabelCategory = ProjectLabelCategory(
    id = UUID.randomUUID(),
    name = "Language",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedProjectLabelCategory <- projectLabelCategoryRepo.insert(projectLabelCategory)
        foundProjectLabelCategory1 <- projectLabelCategoryRepo.findById(savedProjectLabelCategory.id)
        newUpdatedAt = nowAsInstant
        updatedProjectLabelCategory <- projectLabelCategoryRepo.update(
          savedProjectLabelCategory.copy(name = "Programming Language"),
          newUpdatedAt
        )
        foundProjectLabelCategory2 <- projectLabelCategoryRepo.findById(savedProjectLabelCategory.id)
        _ <- projectLabelCategoryRepo.deleteById(foundProjectLabelCategory2.id)
        foundProjectLabelCategory3 <- projectLabelCategoryRepo.findById(savedProjectLabelCategory.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedProjectLabelCategory shouldBe foundProjectLabelCategory1)
        cp(updatedProjectLabelCategory shouldBe foundProjectLabelCategory2)
        cp.reportAll()
        foundProjectLabelCategory3.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
