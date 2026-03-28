package com.togger.db

import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

trait TgTestDbDrops {

  def dropAll(tableNames: Seq[String]): Seq[ConnectionIO[Int]] =
    tableNames
      .map(tableName => (fr"delete from " ++ Fragment.const(tableName)))
      .map(_.update.run)
}
