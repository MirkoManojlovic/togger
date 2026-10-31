package com.togger.preview

import com.togger.logging.TgLogging
import com.togger.ui.localstorage.LocalStorage
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

import java.util.UUID
import scala.util.Try

/** Entity collections persisted as one JSON array per collection in the (already build id prefixed) storage. */
class PreviewDb(storage: LocalStorage) extends TgLogging {

  private var collectionNames: Vector[String] = Vector.empty

  private var decodabilityChecks: Map[String, () => Boolean] = Map.empty

  def collection[A: Encoder: Decoder](name: String, idOf: A => UUID): PreviewCollection[A] = {
    if (!collectionNames.contains(name)) collectionNames = collectionNames :+ name
    decodabilityChecks = decodabilityChecks.updated(
      name,
      () => storage.getItem(PreviewDb.keyFor(name)).forall(raw => decode[List[A]](raw).isRight)
    )
    new PreviewCollection[A](PreviewDb.keyFor(name), storage, idOf)
  }

  def isSeeded: Boolean = storage.getItem(PreviewDb.SeededKey).isDefined

  def markSeeded(): Unit = storage.setItem(PreviewDb.SeededKey, "true")

  /** Names of every collection created so far. */
  def registeredCollectionNames: Seq[String] = collectionNames

  /** Names of the registered collections whose stored JSON does not decode any more. */
  def undecodableCollectionNames: Seq[String] =
    collectionNames.filterNot(name => decodabilityChecks(name)())

  def reset(): Unit = reset(collectionNames)

  def reset(collectionNames: Seq[String]): Unit = {
    collectionNames.foreach(name => storage.removeItem(PreviewDb.keyFor(name)))
    storage.removeItem(PreviewDb.SeededKey)
  }
}

object PreviewDb {
  val SeededKey = "preview-seeded"

  def keyFor(name: String): String = s"preview-db-$name"
}

class PreviewCollection[A: Encoder: Decoder](key: String, storage: LocalStorage, idOf: A => UUID) extends TgLogging {

  def all: List[A] =
    storage.getItem(key).fold(List.empty[A]) { raw =>
      decode[List[A]](raw).fold(
        error => {
          logger.error(s"Corrupt preview collection $key, resetting it", error)
          storage.removeItem(key)
          List.empty[A]
        },
        identity
      )
    }

  def find(id: UUID): Option[A] = all.find(idOf(_) == id)

  def filter(predicate: A => Boolean): List[A] = all.filter(predicate)

  def upsert(entity: A): A = {
    val id = idOf(entity)
    val current = all
    val updated =
      if (current.exists(idOf(_) == id)) current.map(existing => if (idOf(existing) == id) entity else existing)
      else current :+ entity
    write(updated)
    entity
  }

  def update(id: UUID)(f: A => A): Option[A] =
    find(id).map(existing => upsert(f(existing)))

  def delete(id: UUID): Boolean = {
    val current = all
    val remaining = current.filterNot(idOf(_) == id)
    write(remaining)
    remaining.size != current.size
  }

  def deleteWhere(predicate: A => Boolean): Unit = write(all.filterNot(predicate))

  def replaceAll(entities: List[A]): Unit = write(entities)

  private def write(entities: List[A]): Unit =
    Try(storage.setItem(key, entities.asJson.noSpaces)).failed.foreach { error =>
      logger.error(s"Could not store preview collection $key (storage full?)", error)
    }
}
