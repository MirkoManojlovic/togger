package com.togger.preview

import com.togger.ui.localstorage.LocalStorage

/** Puts the preview build id in front of every key, so builds never see each other's data. */
class PrefixedLocalStorage(prefix: String, delegate: LocalStorage) extends LocalStorage {

  def getItem(key: String): Option[String] = delegate.getItem(prefix + key)

  def setItem(key: String, value: String): Unit = delegate.setItem(prefix + key, value)

  def removeItem(key: String): Unit = delegate.removeItem(prefix + key)
}
