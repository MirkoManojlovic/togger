package com.togger.preview

import com.togger.demo.DemoIdentity

import com.togger.common.BadRequest
import com.togger.common.PageRequest
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.dto.dataexportconfig.{CreateDataExportConfigReq, UpdateDataExportConfigReq}
import com.togger.dto.dataimportconfig.CreateDataImportConfigReq
import com.togger.dto.projectmapping.CreateProjectMappingReq
import com.togger.ui.dataexportconfig.DataExportConfigFeServicePreview
import com.togger.ui.dataimportconfig.DataImportConfigFeServicePreview
import com.togger.ui.projectmapping.ProjectMappingFeServicePreview
import com.togger.ui.task.{DataExportFeServicePreview}

import java.util.UUID

class PreviewDataSyncDomainTest extends AsyncJsWordSpec {

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val exportConfigs = new DataExportConfigFeServicePreview(m.context, m.projects, m.dataSync)
    val importConfigs = new DataImportConfigFeServicePreview(m.context, m.projects, m.dataSync)
    val mappings = new ProjectMappingFeServicePreview(m.context, m.projects, m.dataSync)
    val exports = new DataExportFeServicePreview(m.context, m.projects, m.dataSync)
    env.loginAsDemo()
  }

  private val firstPage = PageRequest(1, 20)

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "create, update and delete export configs of a project" in { _ =>
      val s = new Services
      for {
        seeded <- s.env.run(s.exportConfigs.findByProjectId(DemoIdentity.ProjectId, firstPage))
        created <- s.env.run(s.exportConfigs.create(CreateDataExportConfigReq(DemoIdentity.ProjectId, "Zip", "/tmp/zip")))
        id = created.toOption.get.id
        updated <- s.env.run(
          s.exportConfigs.update(UpdateDataExportConfigReq(id, DemoIdentity.ProjectId, "Zip 2", "/tmp/zip2"))
        )
        deleted <- s.env.runEmpty(s.exportConfigs.deleteById(id))
        gone <- s.env.run(s.exportConfigs.findById(id))
        empty <- s.env.run(s.exportConfigs.create(CreateDataExportConfigReq(DemoIdentity.ProjectId, " ", "/tmp/zip")))
      } yield {
        seeded.map(_.totalNumberOfItems) shouldBe Right(2L)
        updated.map(_.name) shouldBe Right("Zip 2")
        deleted.isRight shouldBe true
        gone.isLeft shouldBe true
        isBadRequest(empty) shouldBe true
      }
    }

    "create import configs and list every config of a project" in { _ =>
      val s = new Services
      for {
        _ <- s.env.run(s.importConfigs.create(CreateDataImportConfigReq("Extra", DemoIdentity.ProjectId, "/tmp/in")))
        all <- s.env.run(s.importConfigs.findAllByProjectId(DemoIdentity.ProjectId))
        otherProject <- s.env.run(s.importConfigs.findAllByProjectId(DemoIdentity.SecondProjectId))
      } yield {
        all.map(_.map(_.name)) shouldBe Right(List("Extra", "Teammate export"))
        otherProject.map(_.size) shouldBe Right(1)
      }
    }

    "refuse a duplicate mapping and find mappings by either project id" in { _ =>
      val s = new Services
      val dumpId = UUID.randomUUID()
      for {
        created <- s.env.run(s.mappings.create(CreateProjectMappingReq(DemoIdentity.ProjectId, dumpId)))
        duplicate <- s.env.run(s.mappings.create(CreateProjectMappingReq(DemoIdentity.ProjectId, dumpId)))
        byDb <- s.env.run(s.mappings.findAllByProjectIdDb(DemoIdentity.ProjectId))
        byDump <- s.env.run(s.mappings.findByProjectIdDump(dumpId, firstPage))
        deleted <- s.env.runEmpty(s.mappings.deleteByIds(DemoIdentity.ProjectId, dumpId))
        again <- s.env.runEmpty(s.mappings.deleteByIds(DemoIdentity.ProjectId, dumpId))
      } yield {
        created.isRight shouldBe true
        isBadRequest(duplicate) shouldBe true
        byDb.map(_.size) shouldBe Right(2)
        byDump.map(_.totalNumberOfItems) shouldBe Right(1L)
        deleted.isRight shouldBe true
        again.isLeft shouldBe true
      }
    }

    "report nothing pending but refuse to export or import" in { _ =>
      val s = new Services
      for {
        exportPending <- s.env.run(s.exports.exportPending(DemoIdentity.ProjectId))
        importPending <- s.env.run(s.exports.importPending(DemoIdentity.ProjectId))
        status <- s.env.run(s.exports.checkStatus(DemoIdentity.ProjectId))
        exported <- s.env.run(s.exports.exportData(DemoIdentity.ProjectId))
        imported <- s.env.run(s.exports.importData(DemoIdentity.ProjectId, "/tmp/in"))
      } yield {
        exportPending.map(_.pending) shouldBe Right(false)
        importPending.map(_.pending) shouldBe Right(false)
        status.map(_.pathStatuses.size) shouldBe Right(2)
        isBadRequest(exported) shouldBe true
        isBadRequest(imported) shouldBe true
        exported.left.map(_.toString).left.toOption.exists(_.contains(DataExportFeServicePreview.UnavailableMessage)) shouldBe true
      }
    }

    "drop the configs and mappings of a deleted project" in { _ =>
      val s = new Services
      s.env.module.projects.deleteProject(DemoIdentity.SecondProjectId)
      for {
        exportConfigs <- s.env.run(s.exportConfigs.findByProjectId(DemoIdentity.SecondProjectId, firstPage))
        mappings <- s.env.run(s.mappings.findByProjectIdDb(DemoIdentity.SecondProjectId, firstPage))
      } yield {
        exportConfigs.map(_.totalNumberOfItems) shouldBe Right(0L)
        mappings.map(_.totalNumberOfItems) shouldBe Right(0L)
      }
    }
  }
}
