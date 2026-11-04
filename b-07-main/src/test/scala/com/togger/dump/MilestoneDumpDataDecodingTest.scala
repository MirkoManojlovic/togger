package com.togger.dump

import com.togger.milestone.Milestone
import com.togger.milestone.MilestoneStep
import com.togger.milestonetemplate.MilestoneTemplateVersion
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class MilestoneDumpDataDecodingTest extends AnyWordSpec with Matchers {

  private val dump =
    MilestoneDumpData(
      Milestone(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-12T10:00:00Z")),
      Nil,
      Nil,
      Nil
    )

  "MilestoneDumpData" should {

    "decode what it encodes" in {
      decode[MilestoneDumpData](dump.asJson.noSpaces) shouldBe Right(dump)
    }

    "decode a dump written before milestone summaries existed, as one without summaries" in {
      val withoutSummaries = dump.asJson.mapObject(_.remove("summaries")).noSpaces
      decode[MilestoneDumpData](withoutSummaries) shouldBe Right(dump)
    }

    "decode a dump written before milestone steps existed, as one without steps" in {
      val withoutSteps = dump.asJson.mapObject(_.remove("steps")).noSpaces
      decode[MilestoneDumpData](withoutSteps) shouldBe Right(dump)
    }

    "decode the steps it encodes" in {
      val withStep = dump.copy(steps =
        List(
          MilestoneStepDumpData(
            MilestoneStep(UUID.randomUUID(), dump.milestone.id, Instant.parse("2026-10-12T10:00:00Z")),
            Nil
          )
        )
      )
      decode[MilestoneDumpData](withStep.asJson.noSpaces) shouldBe Right(withStep)
    }
  }

  "MilestoneTemplateVersionDumpData" should {

    val version = MilestoneTemplateVersion(
      UUID.randomUUID(),
      UUID.randomUUID(),
      "Feature",
      "d",
      Instant.parse("2026-10-12T10:00:00Z")
    )
    val design = UUID.randomUUID()
    val backend = UUID.randomUUID()
    val frontend = UUID.randomUUID()

    "decode the stages it encodes" in {
      val data = MilestoneTemplateVersionDumpData(version, List(List(design), List(backend, frontend)))
      decode[MilestoneTemplateVersionDumpData](data.asJson.noSpaces) shouldBe Right(data)
    }

    "decode a dump written before steps could run in parallel, as one stage per step" in {
      val old = data(version).mapObject(_.add("stepTemplateIds", List(design, backend).asJson)).noSpaces
      decode[MilestoneTemplateVersionDumpData](old) shouldBe
        Right(MilestoneTemplateVersionDumpData(version, List(List(design), List(backend))))
    }
  }

  private def data(version: MilestoneTemplateVersion) =
    MilestoneTemplateVersionDumpData(version, Nil).asJson.mapObject(_.remove("stages"))
}
