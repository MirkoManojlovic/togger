package com.togger.dump

import io.circe.Json

/** An import maps the accounts of an export to the ones of the database by email, so their ids differ between the disk
  * and the database. The import check compares accounts by first name, last name and email instead: every account id
  * in an entity's json is replaced by that content before the two sides are compared.
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
            firstName <- cursor.get[String]("firstName").toOption
            lastName <- cursor.get[String]("lastName").toOption
            email <- cursor.get[String]("email").toOption
          } yield id -> s"account:$firstName|$lastName|$email"
        }
        .toMap
    )

}
