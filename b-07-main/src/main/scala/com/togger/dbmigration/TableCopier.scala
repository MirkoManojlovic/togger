package com.togger.dbmigration

import cats.effect.IO
import com.togger.TgLogging

import java.sql.Connection
import java.sql.ResultSet
import scala.collection.mutable.ListBuffer

/** Copies every table of the source database into the same-named (or renamed) table of the target database.
  *
  * Only the columns both sides have are copied, a column the target added is filled by its default unless a derived
  * value is given. Foreign keys are not checked during the copy (the source already satisfied them), and everything is
  * one transaction of the target, so a failure leaves nothing behind.
  */
class TableCopier(rules: CopyRules) extends TgLogging {
  import TableCopier._

  private val BatchSize = 500
  private val schemaTables = Set("databasechangelog", "databasechangeloglock", "db_migration_log")

  def copy(source: Connection, target: Connection): IO[Unit] =
    IO.blocking {
      target.setAutoCommit(false)
      source.setAutoCommit(false)
      try {
        execute(target, "SET session_replication_role = replica")
        tables(source).foreach(copyTable(source, target, _))
        execute(target, "SET session_replication_role = DEFAULT")
        target.commit()
      } catch {
        case e: Throwable =>
          target.rollback()
          throw e
      } finally source.rollback()
    }

  private def copyTable(source: Connection, target: Connection, sourceTable: String): Unit = {
    val targetTable = rules.tableRenames.getOrElse(sourceTable, sourceTable)
    val targetColumns = columns(target, targetTable)
    if (rules.skippedTables.contains(sourceTable)) logger.info(s"Skipping table $sourceTable")
    else if (targetColumns.isEmpty) logger.warn(s"Table $sourceTable has no counterpart in the new schema, skipped")
    else {
      val sourceColumns = columns(source, sourceTable).toSet
      val plan = targetColumns.flatMap(column => planFor(targetTable, column, sourceColumns))
      val count = insertAll(source, target, sourceTable, targetTable, plan)
      logger.info(s"Copied $count rows of $sourceTable into $targetTable")
    }
  }

  private def planFor(table: String, column: String, sourceColumns: Set[String]): Option[ColumnPlan] = {
    val renamedFrom = rules.columnRenames.get((table, column))
    if (sourceColumns.contains(column)) Some(FromSource(column, column))
    else if (renamedFrom.exists(sourceColumns.contains)) renamedFrom.map(FromSource(column, _))
    else rules.derivedColumns.get((table, column)).map(Derived(column, _))
  }

  private def insertAll(
    source: Connection,
    target: Connection,
    sourceTable: String,
    targetTable: String,
    plan: List[ColumnPlan]
  ): Int = {
    val names = plan.map(column => s""""${column.target}"""").mkString(", ")
    val placeholders = plan.map(_ => "?").mkString(", ")
    val insert = target.prepareStatement(s"""INSERT INTO "$targetTable" ($names) VALUES ($placeholders)""")
    val select = source.createStatement()
    select.setFetchSize(BatchSize)
    val rows = select.executeQuery(s"""SELECT * FROM "$sourceTable"""")
    try {
      var count = 0
      while (rows.next()) {
        val row = readRow(rows)
        plan.zipWithIndex.foreach { case (column, index) =>
          val value = column match {
            case FromSource(_, sourceColumn) => row(sourceColumn)
            case Derived(_, derive)          => derive(row)
          }
          insert.setObject(index + 1, value)
        }
        insert.addBatch()
        count += 1
        if (count % BatchSize == 0) insert.executeBatch()
      }
      insert.executeBatch()
      count
    } finally {
      rows.close()
      select.close()
      insert.close()
    }
  }

  private def readRow(rows: ResultSet): Map[String, AnyRef] = {
    val metaData = rows.getMetaData
    (1 to metaData.getColumnCount).map(index => metaData.getColumnName(index) -> rows.getObject(index)).toMap
  }

  private def tables(connection: Connection): List[String] =
    query(
      connection,
      "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name"
    ).filterNot(schemaTables.contains)

  private def columns(connection: Connection, table: String): List[String] = {
    val statement = connection.prepareStatement(
      "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position"
    )
    try {
      statement.setString(1, table)
      collect(statement.executeQuery())
    } finally statement.close()
  }

  private def query(connection: Connection, sql: String): List[String] = {
    val statement = connection.createStatement()
    try collect(statement.executeQuery(sql))
    finally statement.close()
  }

  private def collect(rows: ResultSet): List[String] = {
    val result = ListBuffer.empty[String]
    while (rows.next()) result += rows.getString(1)
    rows.close()
    result.toList
  }

  private def execute(connection: Connection, sql: String): Unit = {
    val statement = connection.createStatement()
    try { statement.execute(sql); () }
    finally statement.close()
  }
}

object TableCopier {
  private sealed trait ColumnPlan { def target: String }
  private final case class FromSource(target: String, source: String) extends ColumnPlan
  private final case class Derived(target: String, value: Map[String, AnyRef] => AnyRef) extends ColumnPlan
}
