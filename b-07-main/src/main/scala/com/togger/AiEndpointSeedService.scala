package com.togger

import cats.effect.IO
import cats.implicits._
import com.togger.ai.{AiEndpointRepoImpl, AiEndpointRow, TgAiEndpoints}
import com.togger.logging.TgLogging
import doobie.Transactor
import doobie.implicits._

class AiEndpointSeedService(transactor: Transactor[IO]) extends TgLogging {
  private val aiEndpointRepo = new AiEndpointRepoImpl

  def run: IO[Unit] =
    for {
      existing <- aiEndpointRepo.findAll.transact(transactor)
      existingById = existing.map(row => row.id -> row).toMap
      _ <- warnOnMismatches(existingById)
      missing = TgAiEndpoints.values.filterNot(endpoint => existingById.contains(endpoint.value))
      _ <- missing.toList.traverse_(endpoint =>
        aiEndpointRepo.insert(AiEndpointRow(endpoint.value, endpoint.endpointName)).transact(transactor)
      )
    } yield ()

  private def warnOnMismatches(existingById: Map[Int, AiEndpointRow]): IO[Unit] =
    existingById.values.toList.traverse_ { row =>
      TgAiEndpoints.withValueOpt(row.id) match {
        case None                                        => IO(logger.warn(s"AI endpoint $row is not in TgAiEndpoints"))
        case Some(endpoint) if endpoint.endpointName != row.name =>
          IO(logger.warn(s"AI endpoint $row differs from the enum name ${endpoint.endpointName}"))
        case _ => IO.unit
      }
    }
}
