package com.togger.ui.emoji

import enumeratum._

sealed trait SearchPosition extends EnumEntry

object SearchPosition extends Enum[SearchPosition] {
  val values: IndexedSeq[SearchPosition] = findValues

  case object Sticky extends SearchPosition
  case object Static extends SearchPosition
  case object Hidden extends SearchPosition
}

sealed trait NavPosition extends EnumEntry

object NavPosition extends Enum[NavPosition] {
  val values: IndexedSeq[NavPosition] = findValues

  case object Top extends NavPosition
  case object Bottom extends NavPosition
  case object Hidden extends NavPosition
}

sealed trait PreviewPosition extends EnumEntry

object PreviewPosition extends Enum[PreviewPosition] {
  val values: IndexedSeq[PreviewPosition] = findValues

  case object Top extends PreviewPosition
  case object Bottom extends PreviewPosition
  case object Hidden extends PreviewPosition
}

sealed trait SkinTonePosition extends EnumEntry

object SkinTonePosition extends Enum[SkinTonePosition] {
  val values: IndexedSeq[SkinTonePosition] = findValues

  case object Preview extends SkinTonePosition
  case object Search extends SkinTonePosition
  case object Hidden extends SkinTonePosition
}

/** Mirrors emoji-mart's picker props, with the same defaults. Features we don't implement (image sets, locales, custom
  * emoji, `emojiVersion` filtering) have no entry here — see claude_files/emoji_picker.md.
  */
case class TgEmojiPickerConfig(
  perLine: Int = 9,
  emojiSize: Int = 24,
  emojiButtonSize: Int = 36,
  maxFrequentRows: Int = 4,
  categories: Seq[EmojiCategoryId] = EmojiCategoryId.values,
  exceptEmojis: Set[String] = Set.empty,
  searchPosition: SearchPosition = SearchPosition.Sticky,
  navPosition: NavPosition = NavPosition.Top,
  previewPosition: PreviewPosition = PreviewPosition.Bottom,
  skinTonePosition: SkinTonePosition = SkinTonePosition.Preview,
  previewEmoji: String = "point_up",
  noResultsEmoji: String = "cry",
  autoFocus: Boolean = true,
  searchDebounceMillis: Int = 150
) {

  def frequentCount: Int = perLine * maxFrequentRows

  def panelWidthPx: Int = perLine * emojiButtonSize + 28

  def gridMaxHeightPx: Int = (emojiButtonSize * 7.5).toInt

}
