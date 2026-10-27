package com.togger.dump

import com.togger.milestone.Milestone
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class MilestoneDumpDataDecodingTest extends AnyWordSpec with Matchers {

  private val dump =
    MilestoneDumpData(Milestone(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-12T10:00:00Z")), Nil, Nil, Nil)

  "MilestoneDumpData" should {

    "decode what it encodes" in {
      decode[MilestoneDumpData](dump.asJson.noSpaces) shouldBe Right(dump)
    }

    "decode a dump written before milestone summaries existed, as one without summaries" in {
      val withoutSummaries = dump.asJson.mapObject(_.remove("summaries")).noSpaces
      decode[MilestoneDumpData](withoutSummaries) shouldBe Right(dump)
    }
  }
}
