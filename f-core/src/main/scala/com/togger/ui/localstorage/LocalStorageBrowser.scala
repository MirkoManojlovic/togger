package com.togger.ui.localstorage

import org.scalajs.dom

class LocalStorageBrowser extends LocalStorage {

  def getItem(key: String): Option[String] =
    Option(dom.window.localStorage.getItem(key))

  def setItem(key: String, value: String): Unit =
    dom.window.localStorage.setItem(key, value)

  def removeItem(key: String): Unit =
    dom.window.localStorage.removeItem(key)

}
