package com.togger.ui.directorypicker

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** Native directory dialog of the desktop app. The backend runs on the same machine there, so the path is valid for it.
  */
object DirectoryPicker {

  // provided by main.js in the desktop app only
  private val picker: Option[js.Dynamic] =
    Option(js.Dynamic.global.window.tgDirectoryPicker).filterNot(js.isUndefined).map(_.asInstanceOf[js.Dynamic])

  /** True in the desktop app; on the web and in preview mode the path is typed. */
  val isAvailable: Boolean = picker.isDefined

  /** Opens the dialog at the current path; None when it is cancelled or not available. */
  def pick(currentPath: String): Future[Option[String]] =
    picker.fold(Future.successful(Option.empty[String])) { picker =>
      picker
        .pick(currentPath)
        .asInstanceOf[js.Promise[js.Dynamic]]
        .toFuture
        .map(chosen => Option(chosen).filterNot(js.isUndefined(_)).map(_.asInstanceOf[String]))
    }

}
