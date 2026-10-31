package com.togger.demo

import com.togger.dto.dataexportconfig.DataExportConfigResp
import com.togger.dto.dataimportconfig.DataImportConfigResp
import com.togger.dto.projectmapping.ProjectMappingResp

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Export and import configs and project mappings of the demo projects. */
object DemoDataSyncData {
  private def configId(number: Int): UUID = DemoSeedIds.id(DemoSeedIds.DataSyncConfig, number)

  def exportConfigs(now: Instant): List[DataExportConfigResp] =
    exportSpecs.map { case (number, projectId, name, path, ageDays) =>
      val created = now.minus(ageDays, ChronoUnit.DAYS)
      DataExportConfigResp(configId(number), projectId, DemoIdentity.AccountId, name, path, created, created)
    }

  def importConfigs(now: Instant): List[DataImportConfigResp] =
    importSpecs.map { case (number, projectId, name, path, ageDays) =>
      val created = now.minus(ageDays, ChronoUnit.DAYS)
      DataImportConfigResp(configId(number), name, projectId, DemoIdentity.AccountId, path, created, created)
    }

  def mappings(now: Instant): List[DemoProjectMapping] =
    mappingSpecs.map { case (number, projectIdDb, projectIdDump, ageDays) =>
      val created = now.minus(ageDays, ChronoUnit.DAYS)
      DemoProjectMapping(
        DemoSeedIds.id(DemoSeedIds.ProjectMapping, number),
        ProjectMappingResp(projectIdDb, projectIdDump, created, created)
      )
    }

  private val exportSpecs = List(
    (1, DemoIdentity.ProjectId, "Team repository", "~/togger-export/website-relaunch", 30L),
    (2, DemoIdentity.ProjectId, "Backup drive", "/Volumes/Backup/togger/website-relaunch", 12L),
    (3, DemoIdentity.SecondProjectId, "Team repository", "~/togger-export/mobile-app", 21L)
  )

  private val importSpecs = List(
    (11, DemoIdentity.ProjectId, "Teammate export", "~/shared/togger/website-relaunch", 20L),
    (12, DemoIdentity.SecondProjectId, "Teammate export", "~/shared/togger/mobile-app", 9L)
  )

  private val mappingSpecs = List(
    (1, DemoIdentity.ProjectId, UUID.fromString("00000000-0000-7000-8000-00000000d201"), 20L),
    (2, DemoIdentity.SecondProjectId, UUID.fromString("00000000-0000-7000-8000-00000000d202"), 9L)
  )
}
