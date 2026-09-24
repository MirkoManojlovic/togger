package com.togger.ui.emoji

import com.raquo.laminar.api.L._
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._
import scala.util.{Failure, Success, Try}

sealed trait EmojiDataState

object EmojiDataState {
  case object Loading extends EmojiDataState
  case class Loaded(dataSet: EmojiDataSet) extends EmojiDataState
  case class Failed(message: String) extends EmojiDataState
}

/** Raw shape of `public/emoji-data.json`, as produced by `tools/emoji/build-emoji-data.mjs`. */
@js.native
private[emoji] trait RawEmojiCategory extends js.Object {
  val id: String = js.native
  val emojis: js.Array[String] = js.native
}

@js.native
private[emoji] trait RawEmoji extends js.Object {
  val name: String = js.native
  val keywords: js.Array[String] = js.native
  val skins: js.Array[String] = js.native
}

@js.native
private[emoji] trait RawEmojiData extends js.Object {
  val categories: js.Array[RawEmojiCategory] = js.native
  val emojis: js.Dictionary[RawEmoji] = js.native
}

/** Loads the vendored emoji dataset once per page and shares it with every picker.
  *
  * The dataset is a Vite static asset (`public/emoji-data.json`), not a ScalaJS resource — ScalaJS resources only reach
  * the JVM classpath and are invisible to the linker output. Parsing goes through `js.JSON.parse` rather than circe:
  * generic decoding of ~1900 nested objects is slow enough to be felt on first open.
  */
object EmojiDataLoader {

  val DataUrl = "/emoji-data.json"

  private implicit val executionContext: scala.concurrent.ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  private val stateVar: Var[EmojiDataState] = Var(EmojiDataState.Loading)
  private var started = false

  def state: Signal[EmojiDataState] = stateVar.signal

  /** Idempotent — the first picker to mount triggers the fetch, later ones reuse it. */
  def load(): Unit =
    if (!started) {
      started = true
      fetchDataSet()
    }

  /** Used by the picker's error state retry button. */
  def reload(): Unit = {
    started = true
    stateVar.set(EmojiDataState.Loading)
    fetchDataSet()
  }

  private def fetchDataSet(): Unit =
    dom
      .fetch(DataUrl)
      .flatMap { response =>
        if (response.ok) response.text()
        else Future.failed(new RuntimeException(s"HTTP ${response.status} ${response.statusText}"))
      }
      .onComplete {
        case Success(body)  => stateVar.set(parse(body))
        case Failure(error) => stateVar.set(EmojiDataState.Failed(error.getMessage))
      }

  private[emoji] def parse(body: String): EmojiDataState =
    Try {
      val raw = js.JSON.parse(body).asInstanceOf[RawEmojiData]

      val byId = raw.emojis.iterator.flatMap { case (id, rawEmoji) =>
        val skins = rawEmoji.skins.toVector
        if (skins.isEmpty) None
        else Some(id -> Emoji(id, rawEmoji.name, rawEmoji.keywords.toVector, skins))
      }.toMap

      val categories = raw.categories.toVector
        .flatMap { rawCategory =>
          EmojiCategoryId
            .fromJsonId(rawCategory.id)
            .map(categoryId => EmojiCategory(categoryId, rawCategory.emojis.toVector.filter(byId.contains)))
        }
        .filter(_.emojiIds.nonEmpty)

      EmojiDataSet(categories, byId)
    } match {
      case Success(dataSet) if dataSet.categories.nonEmpty => EmojiDataState.Loaded(dataSet)
      case Success(_)                                      => EmojiDataState.Failed("Emoji dataset is empty")
      case Failure(error) => EmojiDataState.Failed(s"Malformed emoji dataset: ${error.getMessage}")
    }

}
