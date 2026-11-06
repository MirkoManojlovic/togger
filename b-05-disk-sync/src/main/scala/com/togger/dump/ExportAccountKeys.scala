package com.togger.dump

import io.circe.Json

/** An import maps the accounts of an export to the ones of the database by email, so their ids differ between the disk
  * and the database, and so can their names. The import check compares accounts by email instead: every account id in
  * an entity's json is replaced by that email before the two sides are compared.
  */
case class ExportAccountKeys(accountKeys: Map[String, String]) {

  def replaceIds(json: Json): Json =
    json.arrayOrObject(
      json.mapString(value => accountKeys.getOrElse(value, value)),
      values => Json.fromValues(values.map(replaceIds)),
      obj => Json.fromJsonObject(obj.mapValues(replaceIds))
    )

}

object ExportAccountKeys {

  val empty: ExportAccountKeys = ExportAccountKeys(Map.empty)

  /** The accounts a `project.json` references, by their id. */
  def ofProject(projectJson: Json): ExportAccountKeys =
    ExportAccountKeys(
      projectJson.hcursor
        .downField("referencedAccounts")
        .as[List[Json]]
        .getOrElse(Nil)
        .flatMap { account =>
          val cursor = account.hcursor
          for {
            id <- cursor.get[String]("id").toOption
            email <- cursor.get[String]("email").toOption
          } yield id -> s"account:$email"
        }
        .toMap
    )

}
