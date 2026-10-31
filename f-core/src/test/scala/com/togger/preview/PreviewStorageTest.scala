package com.togger.preview

import com.togger.common.PageRequest
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.ui.localstorage.LocalStorageTest
import io.circe.generic.auto._

import java.util.UUID

case class PreviewThing(id: UUID, name: String)

class PreviewStorageTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "prefix every key with the build id and never write an unprefixed key" in { _ =>
      val raw = new RecordingStorage
      val storage = new PrefixedLocalStorage(PreviewConfig.prefixFor("abc123"), raw)

      storage.setItem("tg-color-theme", "dark")
      storage.getItem("tg-color-theme") shouldBe Some("dark")
      raw.keys shouldBe Set("abc123:tg-color-theme")

      storage.removeItem("tg-color-theme")
      raw.keys shouldBe empty
    }

    "keep the data of two build ids apart" in { _ =>
      val raw = new RecordingStorage
      new PrefixedLocalStorage(PreviewConfig.prefixFor("one"), raw).setItem("k", "1")

      new PrefixedLocalStorage(PreviewConfig.prefixFor("two"), raw).getItem("k") shouldBe None
    }

    "store, update and delete entities in a collection" in { _ =>
      val things = new PreviewDb(new LocalStorageTest).collection[PreviewThing]("things", _.id)
      val thing = PreviewThing(UUID.randomUUID(), "a")

      things.upsert(thing)
      things.upsert(thing.copy(name = "b"))
      things.all shouldBe List(thing.copy(name = "b"))
      things.delete(thing.id) shouldBe true
      things.all shouldBe empty
    }

    "reset a corrupt collection instead of failing" in { _ =>
      val storage = new LocalStorageTest
      storage.setItem(PreviewDb.keyFor("things"), "not json")

      new PreviewDb(storage).collection[PreviewThing]("things", _.id).all shouldBe empty
    }

    "page items 1 based" in { _ =>
      val page = PreviewPaging.page(1 to 25, PageRequest(2, 10))

      page.items shouldBe (11 to 20).toList
      page.totalNumberOfItems shouldBe 25L
      page.totalNumberOfPages shouldBe 3L
    }
  }

  private class RecordingStorage extends LocalStorageTest {
    private val written = scala.collection.mutable.Set[String]()
    override def setItem(key: String, value: String): Unit = { written += key; super.setItem(key, value) }
    override def removeItem(key: String): Unit = { written -= key; super.removeItem(key) }
    def keys: Set[String] = written.toSet
  }
}
