package com.togger.dump

import io.circe.Json

/** One leaf of an entity's json that differs between the disk and the database; `None` when it is absent on a side. */
case class ExportFieldDiff(field: String, disk: Option[String], database: Option[String])

/** Compares two versions of an entity's json leaf by leaf, whatever the entity type. A nested field is named by its
  * path (`taskVersions[1a2b3c4d].name`); the elements of a list of objects with ids are matched by id instead of
  * position, so a version added in the middle is not reported as a change of all the versions after it.
  */
object ExportJsonDiff {

  private val MaxDiffs = 200
  private val MaxValueLength = 300
  private val IdPrefixLength = 8

  def diff(disk: Json, database: Json): List[ExportFieldDiff] = {
    val diskLeaves = leaves(disk, "")
    val databaseLeaves = leaves(database, "")
    val diskByPath = diskLeaves.toMap
    val databaseByPath = databaseLeaves.toMap
    val paths = (databaseLeaves.map(_._1) ++ diskLeaves.map(_._1)).distinct

    paths
      .filter(path => diskByPath.get(path) != databaseByPath.get(path))
      .take(MaxDiffs)
      .map(path => ExportFieldDiff(path, diskByPath.get(path).map(show), databaseByPath.get(path).map(show)))
  }

  private def leaves(json: Json, path: String): List[(String, Json)] =
    json.arrayOrObject(
      List(path -> json),
      values =>
        if (values.isEmpty) List(path -> json)
        else {
          val labels = elementLabels(values)
          values.toList.zip(labels).flatMap { case (value, label) => leaves(value, s"$path[$label]") }
        },
      obj =>
        if (obj.isEmpty) List(path -> json)
        else obj.toList.sortBy(_._1).flatMap { case (key, value) => leaves(value, if (path.isEmpty) key else s"$path.$key") }
    )

  private def elementLabels(values: Vector[Json]): List[String] = {
    val ids = values.flatMap(_.hcursor.get[String]("id").toOption)
    if (ids.size == values.size && ids.distinct.size == ids.size) ids.map(_.take(IdPrefixLength)).toList
    else values.indices.map(_.toString).toList
  }

  private def show(json: Json): String = {
    val text = json.asString.getOrElse(json.noSpaces)
    if (text.length > MaxValueLength) text.take(MaxValueLength) + "…" else text
  }

}
