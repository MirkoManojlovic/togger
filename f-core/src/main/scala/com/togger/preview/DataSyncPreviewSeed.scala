package com.togger.preview

import com.togger.common.TgClock
import com.togger.dto.dataexportconfig.DataExportConfigResp
import com.togger.dto.dataimportconfig.DataImportConfigResp
import com.togger.dto.projectmapping.ProjectMappingResp

import java.time.temporal.ChronoUnit
import java.util.UUID

/** Seeds export and import configs and project mappings for the demo projects; needs the projects seeded first. */
class DataSyncPreviewSeed(data: DataSyncPreviewData, clock: TgClock) extends PreviewSeeder {
  import DataSyncPreviewSeed._

  override def seed(): Unit = {
    val now = clock.currentInstant
    exportConfigs.foreach { case (number, projectId, name, path, ageDays) =>
      val created = now.minus(ageDays, ChronoUnit.DAYS)
      data.exportConfigs.upsert(
        DataExportConfigResp(configId(number), projectId, PreviewDemo.AccountId, name, path, created, created)
      )
    }
    importConfigs.foreach { case (number, projectId, name, path, ageDays) =>
      val created = now.minus(ageDays, ChronoUnit.DAYS)
      data.importConfigs.upsert(
        DataImportConfigResp(configId(number), name, projectId, PreviewDemo.AccountId, path, created, created)
      )
    }
    mappings.foreach { case (number, projectIdDb, projectIdDump, ageDays) =>
      val created = now.minus(ageDays, ChronoUnit.DAYS)
      data.mappings.upsert(
        PreviewProjectMapping(
          PreviewSeedIds.id(PreviewSeedIds.ProjectMapping, number),
          ProjectMappingResp(projectIdDb, projectIdDump, created, created)
        )
      )
    }
  }
}

object DataSyncPreviewSeed {
  private def configId(number: Int): UUID = PreviewSeedIds.id(PreviewSeedIds.DataSyncConfig, number)

  private val exportConfigs = List(
    (1, PreviewDemo.ProjectId, "Team repository", "~/togger-export/website-relaunch", 30L),
    (2, PreviewDemo.ProjectId, "Backup drive", "/Volumes/Backup/togger/website-relaunch", 12L),
    (3, PreviewDemo.SecondProjectId, "Team repository", "~/togger-export/mobile-app", 21L)
  )

  private val importConfigs = List(
    (11, PreviewDemo.ProjectId, "Teammate export", "~/shared/togger/website-relaunch", 20L),
    (12, PreviewDemo.SecondProjectId, "Teammate export", "~/shared/togger/mobile-app", 9L)
  )

  private val mappings = List(
    (1, PreviewDemo.ProjectId, UUID.fromString("00000000-0000-7000-8000-00000000d201"), 20L),
    (2, PreviewDemo.SecondProjectId, UUID.fromString("00000000-0000-7000-8000-00000000d202"), 9L)
  )
}
