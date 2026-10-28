package com.togger.ai

import com.togger.common.BusinessTgDbSpec
import doobie.implicits._

class AiEndpointRepoTest extends BusinessTgDbSpec {

  private val repo = new AiEndpointRepoImpl

  testNameFromClassName should {

    "insert rows and find them ordered by id" in { transactor =>
      (for {
        _ <- repo.insert(AiEndpointRow(1, "second"))
        _ <- repo.insert(AiEndpointRow(0, "first"))
        all <- repo.findAll
      } yield all shouldBe List(AiEndpointRow(0, "first"), AiEndpointRow(1, "second"))).transact(transactor)
    }

    "reject a duplicate name" in { transactor =>
      (for {
        _ <- repo.insert(AiEndpointRow(0, "same"))
        _ <- repo.insert(AiEndpointRow(1, "same"))
      } yield ()).transact(transactor).attempt.map(_.isLeft shouldBe true)
    }
  }
}
