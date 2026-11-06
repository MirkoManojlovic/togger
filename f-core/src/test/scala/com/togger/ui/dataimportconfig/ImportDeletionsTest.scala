package com.togger.ui.dataimportconfig

import com.togger.dto.DataExportEntityStatusResp
import com.togger.dto.DataExportEntityTypeResp
import com.togger.dto.DataExportPathStatusResp
import com.togger.dto.DataExportSyncStatusResp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class ImportDeletionsTest extends AnyWordSpec with Matchers {

  private def entity(
    status: DataExportSyncStatusResp,
    name: Option[String] = Some("Fix login"),
    id: UUID = UUID.randomUUID()
  ) =
    DataExportEntityStatusResp(DataExportEntityTypeResp.Task, id, name, status, Nil)

  private def path(entities: DataExportEntityStatusResp*) = DataExportPathStatusResp("/a", entities.toList)

  "ImportDeletions" should {

    "list only the entities that the import would delete" in {
      val doomed = entity(DataExportSyncStatusResp.PendingDelete)
      val paths = List(
        path(doomed, entity(DataExportSyncStatusResp.InSync), entity(DataExportSyncStatusResp.MissingFromDb))
      )

      ImportDeletions.of(paths) shouldBe List(doomed)
    }

    "list an entity once when several paths report it" in {
      val doomed = entity(DataExportSyncStatusResp.PendingDelete)

      ImportDeletions.of(List(path(doomed), path(doomed))) shouldBe List(doomed)
    }

    "describe an entity by its type and name, or by the start of its id without a name" in {
      val id = UUID.fromString("12345678-0000-0000-0000-000000000000")

      ImportDeletions.describe(entity(DataExportSyncStatusResp.PendingDelete)) shouldBe "Task: Fix login"
      ImportDeletions.describe(entity(DataExportSyncStatusResp.PendingDelete, None, id)) shouldBe "Task: 12345678"
    }

    "list the first ten entities and count the rest" in {
      val deletions = List.fill(12)(entity(DataExportSyncStatusResp.PendingDelete))

      val lines = ImportDeletions.lines(deletions)

      lines should have size 11
      lines.last shouldBe "and 2 more"
    }

  }

}
