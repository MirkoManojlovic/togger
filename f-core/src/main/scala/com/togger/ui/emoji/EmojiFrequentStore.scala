package com.togger.ui.emoji

import com.togger.ui.localstorage.LocalStorage

import scala.scalajs.js
import scala.util.Try

/** emoji-mart's `frequent` category: a LocalStorage-backed pick counter, seeded with upstream's default list so the
  * first-ever open isn't empty.
  */
class EmojiFrequentStore(localStorage: LocalStorage) {

  private var counts: Map[String, Int] = readCounts

  def record(id: String): Unit = {
    counts = counts.updated(id, counts.getOrElse(id, 0) + 1)
    persist()
  }

  def top(howMany: Int): Vector[String] =
    counts.toVector
      .sortBy { case (id, count) => (-count, EmojiFrequentStore.seedIndex(id), id) }
      .take(howMany)
      .map { case (id, _) => id }

  private def persist(): Unit = {
    val dictionary = js.Dictionary[Int](counts.toSeq: _*)
    localStorage.setItem(EmojiFrequentStore.StorageKey, js.JSON.stringify(dictionary))
  }

  private def readCounts: Map[String, Int] =
    localStorage
      .getItem(EmojiFrequentStore.StorageKey)
      .flatMap { stored =>
        Try {
          js.JSON
            .parse(stored)
            .asInstanceOf[js.Dictionary[js.Any]]
            .iterator
            .collect { case (id, count) if js.typeOf(count) == "number" => id -> count.asInstanceOf[Double].toInt }
            .toMap
        }.toOption
      }
      .filter(_.nonEmpty)
      .getOrElse(EmojiFrequentStore.seedCounts)

}

object EmojiFrequentStore {

  val StorageKey = "tg-emoji-frequent"

  /** emoji-mart's default frequent list, highest first. */
  val DefaultFrequent: Vector[String] = Vector(
    "+1",
    "grinning",
    "kissing_heart",
    "heart_eyes",
    "laughing",
    "stuck_out_tongue_winking_eye",
    "sweat_smile",
    "joy",
    "scream",
    "disappointed",
    "unamused",
    "weary",
    "sob",
    "sunglasses",
    "heart",
    "poop"
  )

  private val seedIndexById: Map[String, Int] = DefaultFrequent.zipWithIndex.toMap

  private def seedIndex(id: String): Int = seedIndexById.getOrElse(id, DefaultFrequent.size)

  private def seedCounts: Map[String, Int] =
    DefaultFrequent.zipWithIndex.map { case (id, index) => id -> (DefaultFrequent.size - index) }.toMap

}
