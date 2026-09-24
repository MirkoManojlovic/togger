package com.togger.ui.emoji

/** emoji-mart's `SearchIndex`, re-implemented.
  *
  * Every emoji is indexed by its id parts, the words of its name and its keywords. A query is split into terms, each
  * term must match some token of an emoji (AND, like upstream), and the emoji is scored by where the match lands in the
  * token — 0 for a prefix, higher for a substring — so prefix hits sort first. Ties fall back to dataset order so
  * results don't jitter.
  */
class EmojiSearchIndex(dataSet: EmojiDataSet) {

  private case class IndexedEmoji(emoji: Emoji, tokens: Vector[String], position: Int)

  private val entries: Vector[IndexedEmoji] =
    dataSet.byId.values.toVector
      .map { emoji =>
        val tokens = (emoji.id.toLowerCase.split("[-_]").toVector ++
          emoji.name.toLowerCase.split("\\s+").toVector ++
          emoji.keywords.map(_.toLowerCase)).filter(_.nonEmpty).distinct

        IndexedEmoji(emoji, tokens, dataSet.positionById.getOrElse(emoji.id, Int.MaxValue))
      }
      .sortBy(_.position)

  def search(
    query: String,
    maxResults: Int = EmojiSearchIndex.MaxResults,
    exceptEmojis: Set[String] = Set.empty
  ): Vector[Emoji] = {
    val terms = query.trim.toLowerCase.split("\\s+").toVector.filter(_.nonEmpty)

    if (terms.isEmpty) Vector.empty
    else
      entries.iterator
        .filterNot(entry => exceptEmojis.contains(entry.emoji.id))
        .flatMap(entry => score(entry, terms).map(entryScore => (entryScore, entry.position, entry.emoji)))
        .toVector
        .sortBy { case (entryScore, position, _) => (entryScore, position) }
        .take(maxResults)
        .map { case (_, _, emoji) => emoji }
  }

  private def score(entry: IndexedEmoji, terms: Vector[String]): Option[Int] =
    terms.foldLeft(Option(0)) { (total, term) =>
      total.flatMap(runningTotal => bestTokenScore(entry.tokens, term).map(_ + runningTotal))
    }

  private def bestTokenScore(tokens: Vector[String], term: String): Option[Int] =
    tokens.iterator.map(_.indexOf(term)).filter(_ >= 0).reduceOption(_ min _)

}

object EmojiSearchIndex {

  /** emoji-mart's own cap. */
  val MaxResults = 90

  def apply(dataSet: EmojiDataSet): EmojiSearchIndex = new EmojiSearchIndex(dataSet)
}
