package com.togger.routes

/** The pages of the two lists on the task config screen, as carried by the URL: the dump configs and the load
  * configs each have their own page and size.
  */
case class TaskConfigListParams(dump: ListParams = ListParams.default, load: ListParams = ListParams.default) {

  /** Another page of the dump config list, the load config list stays where it is. */
  def dumpAtPage(newPage: Int, newSize: Int): TaskConfigListParams = copy(dump = dump.atPage(newPage, newSize))

  /** Another page of the load config list, the dump config list stays where it is. */
  def loadAtPage(newPage: Int, newSize: Int): TaskConfigListParams = copy(load = load.atPage(newPage, newSize))
}

object TaskConfigListParams {
  val default: TaskConfigListParams = TaskConfigListParams()

  /** A hand-edited URL never gets to the backend as a 400: bad page or size fall back to the defaults. */
  def fromQuery(
    dumpPage: Option[String],
    dumpSize: Option[String],
    loadPage: Option[String],
    loadSize: Option[String]
  ): TaskConfigListParams =
    TaskConfigListParams(ListParams.fromQuery(dumpPage, dumpSize), ListParams.fromQuery(loadPage, loadSize))
}
