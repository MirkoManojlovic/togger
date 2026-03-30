package com.togger.ui.localstorage

class LocalStorageTest extends LocalStorage {
  private val storage = scala.collection.mutable.Map[String, String]()

  override def getItem(key: String): Option[String] =
    storage.get(key)

  override def setItem(key: String, value: String): Unit =
    storage(key) = value

  override def removeItem(key: String): Unit =
    storage.remove(key)
}
