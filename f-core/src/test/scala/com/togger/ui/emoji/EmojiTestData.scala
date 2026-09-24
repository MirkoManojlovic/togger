package com.togger.ui.emoji

/** A hand-written stand-in for `public/emoji-data.json` — the real dataset has ~1900 entries and would make the
  * expectations below unreadable.
  */
trait EmojiTestData {

  val thumbsUp: Emoji =
    Emoji("+1", "Thumbs Up", Vector("thumbsup", "yes", "hand"), Vector("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"))

  val grinning: Emoji = Emoji("grinning", "Grinning Face", Vector("smile", "happy"), Vector("😀"))

  val cat: Emoji = Emoji("cat", "Cat Face", Vector("animal", "pet"), Vector("🐱"))

  val copycat: Emoji = Emoji("copycat", "Copycat", Vector("mimic"), Vector("🐈"))

  val flagUs: Emoji =
    Emoji("flag-us", "Flag: United States", Vector("united", "states", "american"), Vector("🇺🇸"))

  val allEmojis: Vector[Emoji] = Vector(thumbsUp, grinning, cat, copycat, flagUs)

  val dataSet: EmojiDataSet = EmojiDataSet(
    categories = Vector(
      EmojiCategory(EmojiCategoryId.People, Vector("+1", "grinning")),
      EmojiCategory(EmojiCategoryId.Nature, Vector("cat", "copycat")),
      EmojiCategory(EmojiCategoryId.Flags, Vector("flag-us"))
    ),
    byId = allEmojis.map(emoji => emoji.id -> emoji).toMap
  )

}
