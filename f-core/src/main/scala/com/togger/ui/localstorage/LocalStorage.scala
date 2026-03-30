package com.togger.ui.localstorage

trait LocalStorage {

  def getItem(key: String): Option[String]

  def setItem(key: String, value: String): Unit

  def removeItem(key: String): Unit

}
