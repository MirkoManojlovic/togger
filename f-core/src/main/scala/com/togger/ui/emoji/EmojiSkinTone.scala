package com.togger.ui.emoji

import com.raquo.laminar.api.L._
import com.togger.ui.localstorage.LocalStorage
import enumeratum._

import scala.util.Try

/** The six Fitzpatrick options emoji-mart offers; `index` is the position in [[Emoji.skins]]. */
sealed abstract class SkinTone(val index: Int, val label: String, val swatch: String) extends EnumEntry

object SkinTone extends Enum[SkinTone] {
  val values: IndexedSeq[SkinTone] = findValues

  case object Default extends SkinTone(0, "Default", "✋")
  case object Light extends SkinTone(1, "Light", "✋🏻")
  case object MediumLight extends SkinTone(2, "Medium-Light", "✋🏼")
  case object Medium extends SkinTone(3, "Medium", "✋🏽")
  case object MediumDark extends SkinTone(4, "Medium-Dark", "✋🏾")
  case object Dark extends SkinTone(5, "Dark", "✋🏿")

  def fromIndex(index: Int): Option[SkinTone] = values.find(_.index == index)
}

class EmojiSkinToneStore(localStorage: LocalStorage) {

  private val toneVar: Var[SkinTone] = Var(readTone)

  val current: Signal[SkinTone] = toneVar.signal

  def now(): SkinTone = toneVar.now()

  val updater: Observer[SkinTone] = Observer[SkinTone](set)

  def set(tone: SkinTone): Unit = {
    toneVar.set(tone)
    localStorage.setItem(EmojiSkinToneStore.StorageKey, tone.index.toString)
  }

  private def readTone: SkinTone =
    localStorage
      .getItem(EmojiSkinToneStore.StorageKey)
      .flatMap(stored => Try(stored.trim.toInt).toOption)
      .flatMap(SkinTone.fromIndex)
      .getOrElse(SkinTone.Default)

}

object EmojiSkinToneStore {
  val StorageKey = "tg-emoji-skin"
}
