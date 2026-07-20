package com.togger.db

import java.util.UUID

import cats.effect.IO
import cats.free.Free
import com.togger.common.TgIds
import com.togger.logging.TgLogging
import doobie.free.connection
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}
import scribe.mdc.MDC

object Util extends TgLogging {

  val UNASSIGNED_ACCOUNT_ID: UUID = TgIds.UNASSIGNED_ACCOUNT_ID

  def whereId(id: Long): Fragment =
    fr"where id = $id"

  def whereIdUUID(id: UUID): Fragment =
    fr"where id = $id"

  def insertStatement(table: Fragment)(rest: Fragment): Fragment =
    fr"insert into " ++ table ++ rest

  def selectAllStatement(table: Fragment): Fragment =
    fr"select * from" ++ table

  def findByIdStatement(table: Fragment)(id: Long): Fragment =
    selectAllStatement(table) ++ whereId(id)

  def findByIdStatementUUID(table: Fragment)(id: UUID): Fragment =
    selectAllStatement(table) ++ whereIdUUID(id)

  def findByIdSoftDeletedStatement(table: Fragment)(id: Long): Fragment =
    selectAllStatement(table) ++ whereId(id) ++ andNotSoftDeleted

  def findByIdSoftDeletedStatementUUID(table: Fragment)(id: UUID): Fragment =
    selectAllStatement(table) ++ whereIdUUID(id) ++ andNotSoftDeleted

  def deleteByIdStatement(table: Fragment)(id: Long): Fragment =
    deleteStatement(table) ++ whereId(id)

  def deleteByIdStatementUUID(table: Fragment)(id: UUID): Fragment =
    deleteStatement(table) ++ whereIdUUID(id)

  def softDeleteByIdStatement(table: Fragment)(id: Long): Fragment =
    updateStatement(table)(id)(fr"deleted_at = now()")

  def softDeleteByIdStatementUUID(table: Fragment)(id: UUID): Fragment =
    updateStatementUUID(table)(id)(fr"deleted_at = now()")

  def andNotSoftDeleted: Fragment =
    fr"and deleted_at is null"

  def whereNotSoftDeleted: Fragment =
    fr"where deleted_at is null"

  def updateStatementUUID(table: Fragment)(id: UUID)(rest: Fragment): Fragment =
    fr"update" ++ table ++ fr"set" ++ rest ++ whereIdUUID(id)

  def updateStatement(table: Fragment)(id: Long)(rest: Fragment): Fragment =
    fr"update" ++ table ++ fr"set" ++ rest ++ whereId(id)

  def countStatement(table: Fragment): Fragment =
    fr"select count(*) from" ++ table

  def softCountStatement(table: Fragment): Fragment =
    fr"select count(*) from" ++ table ++ whereNotSoftDeleted

  def deleteStatement(table: Fragment): Fragment =
    fr"delete from " ++ table

  def softDeleteStatement(table: Fragment): Fragment =
    fr"update " ++ table ++ fr"set deleted_at = now()"

  def innerJoin(table: Fragment)(on: Fragment): Fragment =
    fr"join " ++ table ++ fr" on " ++ on

  def leftJoin(table: Fragment)(on: Fragment): Fragment =
    fr"left join " ++ table ++ fr" on " ++ on

  def logAroundInfoQuery[A](
    codeBlock: => ConnectionIO[A]
  )(msgBefore: String, msgToLogAfter: A => String)(implicit
    pkg: sourcecode.Pkg,
    fileName: sourcecode.FileName,
    name: sourcecode.Name,
    line: sourcecode.Line,
    mdc: MDC
  ): Free[connection.ConnectionOp, A] = {
    logger.info(msgBefore)
    codeBlock.map { result =>
      logger.info(msgToLogAfter(result))
      result
    }
  }

  def logAroundInfoQueryIO[A](
    codeBlock: => IO[A]
  )(msgBefore: String, msgToLogAfter: A => String)(implicit
    pkg: sourcecode.Pkg,
    fileName: sourcecode.FileName,
    name: sourcecode.Name,
    line: sourcecode.Line,
    mdc: MDC
  ): IO[A] = {
    logger.info(msgBefore)
    codeBlock.map { result =>
      logger.info(msgToLogAfter(result))
      result
    }
  }
}
