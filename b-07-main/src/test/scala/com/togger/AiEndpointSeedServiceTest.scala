package com.togger

import com.togger.ai.{AiEndpointRepoImpl, AiEndpointRow, TgAiEndpoints}
import com.togger.common.BusinessTgDbSpec
import doobie.implicits._

class AiEndpointSeedServiceTest extends BusinessTgDbSpec {

  private val repo = new AiEndpointRepoImpl
  private val expected = TgAiEndpoints.values.map(e => AiEndpointRow(e.value, e.endpointName)).toList

  testNameFromClassName should {

    "insert every enum entry into an empty table" in { transactor =>
      for {
        _ <- new AiEndpointSeedService(transactor).run
        all <- repo.findAll.transact(transactor)
      } yield all shouldBe expected
    }

    "be idempotent" in { transactor =>
      val service = new AiEndpointSeedService(transactor)
      for {
        _ <- service.run
        _ <- service.run
        all <- repo.findAll.transact(transactor)
      } yield all shouldBe expected
    }

    "leave an existing row untouched" in { transactor =>
      for {
        _ <- repo.insert(AiEndpointRow(0, "renamed")).transact(transactor)
        _ <- new AiEndpointSeedService(transactor).run
        all <- repo.findAll.transact(transactor)
      } yield all.find(_.id == 0) shouldBe Some(AiEndpointRow(0, "renamed"))
    }
  }
}
