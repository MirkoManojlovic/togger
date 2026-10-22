package com.togger.routes

/** The pages of the two lists on the task config screen, as carried by the URL: the export configs and the import
  * configs each have their own page and size.
  */
case class TaskConfigListParams(export: ListParams = ListParams.default, importData: ListParams = ListParams.default) {

  /** Another page of the export config list, the import config list stays where it is. */
  def exportAtPage(newPage: Int, newSize: Int): TaskConfigListParams = copy(export = export.atPage(newPage, newSize))

  /** Another page of the import config list, the export config list stays where it is. */
  def importAtPage(newPage: Int, newSize: Int): TaskConfigListParams = copy(importData = importData.atPage(newPage, newSize))
}

object TaskConfigListParams {
  val default: TaskConfigListParams = TaskConfigListParams()

  /** A hand-edited URL never gets to the backend as a 400: bad page or size fall back to the defaults. */
  def fromQuery(
    exportPage: Option[String],
    exportSize: Option[String],
    importPage: Option[String],
    importSize: Option[String]
  ): TaskConfigListParams =
    TaskConfigListParams(ListParams.fromQuery(exportPage, exportSize), ListParams.fromQuery(importPage, importSize))
}
