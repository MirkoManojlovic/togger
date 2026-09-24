package com.togger.ui.emoji

import enumeratum._

/** Categories as they appear in the dataset, plus the synthetic `Frequent` one which is built client side from
  * [[EmojiFrequentStore]]. Labels are emoji-mart's own English i18n strings.
  */
sealed abstract class EmojiCategoryId(val jsonId: String, val label: String) extends EnumEntry

object EmojiCategoryId extends Enum[EmojiCategoryId] {
  val values: IndexedSeq[EmojiCategoryId] = findValues

  case object Frequent extends EmojiCategoryId("frequent", "Frequently used")
  case object People extends EmojiCategoryId("people", "Smileys & People")
  case object Nature extends EmojiCategoryId("nature", "Animals & Nature")
  case object Foods extends EmojiCategoryId("foods", "Food & Drink")
  case object Activity extends EmojiCategoryId("activity", "Activity")
  case object Places extends EmojiCategoryId("places", "Travel & Places")
  case object Objects extends EmojiCategoryId("objects", "Objects")
  case object Symbols extends EmojiCategoryId("symbols", "Symbols")
  case object Flags extends EmojiCategoryId("flags", "Flags")

  private val byJsonId: Map[String, EmojiCategoryId] = values.map(category => category.jsonId -> category).toMap

  def fromJsonId(jsonId: String): Option[EmojiCategoryId] = byJsonId.get(jsonId)
}

/** `skins` holds the native strings of every variant: one entry for emoji without skin tones, six (default plus tones
  * 2-6, in [[SkinTone]] order) for those that have them.
  */
case class Emoji(id: String, name: String, keywords: Vector[String], skins: Vector[String]) {
  def native(tone: SkinTone): String = skins.lift(tone.index).getOrElse(skins.head)

  def shortcode: String = s":$id:"

  def hasSkinTones: Boolean = skins.sizeIs > 1
}

case class EmojiCategory(id: EmojiCategoryId, emojiIds: Vector[String])

/** What the picker hands back on selection — the caller only ever needs [[native]]. */
case class SelectedEmoji(emoji: Emoji, tone: SkinTone, native: String)

case class EmojiDataSet(categories: Vector[EmojiCategory], byId: Map[String, Emoji]) {

  /** Position in the dataset, used to keep search results stable when scores tie. */
  lazy val positionById: Map[String, Int] =
    categories.flatMap(_.emojiIds).distinct.zipWithIndex.toMap

  private lazy val byNative: Map[String, Emoji] =
    byId.values.flatMap(emoji => emoji.skins.map(_ -> emoji)).toMap

  def get(id: String): Option[Emoji] = byId.get(id)

  def resolveAll(ids: Vector[String]): Vector[Emoji] = ids.flatMap(byId.get)

  def category(id: EmojiCategoryId): Option[EmojiCategory] = categories.find(_.id == id)

  /** emoji-mart's `getEmojiDataFromNative` — resolves any skin variant back to its base emoji. */
  def findByNative(native: String): Option[Emoji] = byNative.get(native)
}
