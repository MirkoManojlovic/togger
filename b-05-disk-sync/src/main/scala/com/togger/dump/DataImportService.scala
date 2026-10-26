package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.dump.DayOfWeekCodec._
import com.togger.logging.TgLogging
import com.togger.project.ProjectError.{DumpManifestMissing, NotAssignedToProject, ProjectError}
import com.togger.task.{TaskLabel, TaskStatus}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.Transactor
import io.circe.Decoder
import io.circe.generic.auto._

import java.util.UUID

class DataImportService(
  dataExportRepo: DataExportRepo,
  dataImportRepo: DataImportRepo,
  dataExportTreeStore: DataExportTreeStore,
  transactor: Transactor[IO]
) extends TgLogging {

  private type Maybe[A] = Either[DataImportEntityResult, A]

  def importProject(projectId: UUID, accountId: UUID, projectFolderPath: String): IO[Either[ProjectError, DataImportResult]] = {
    logger.info(s"Importing project ${sp(projectId)} from ${sp(projectFolderPath)} requested by ${sp(accountId)}")
    timed("import total", projectId)(importProjectSteps(projectId, accountId, projectFolderPath))
  }

  private def importProjectSteps(
    projectId: UUID,
    accountId: UUID,
    projectFolderPath: String
  ): IO[Either[ProjectError, DataImportResult]] =
    timed("check assignment", projectId)(dataExportRepo.isAssigned(projectId, accountId).transact(transactor)).flatMap { assigned =>
      if (!assigned) IO.pure(Left(NotAssignedToProject))
      else
        timedNow("read index and project file", projectId)(for {
          importPath <- dataExportTreeStore.resolveImportFolder(projectFolderPath)
          index <- dataExportTreeStore.readIndexAtPath(importPath).toOption
          projectDump <- dataExportTreeStore
            .readEntityJson(importPath, index, DataExportEntityType.Project, firstId(index, DataExportEntityType.Project))
            .flatMap(_.as[ProjectDumpData].left.map(_.getMessage))
            .toOption
        } yield (importPath, index, projectDump)) match {
          case None => IO.pure(Left(DumpManifestMissing))
          case Some((importPath, index, projectDump)) =>
            importFromTree(projectId, importPath, index, projectDump)
        }
    }

  /** Logs how long a step of the import took, so a slow import can be traced to the step responsible. */
  private def timed[A](step: String, projectId: UUID)(io: IO[A]): IO[A] =
    io.timed.flatMap { case (duration, result) => IO(logStep(step, projectId, duration.toMillis)).as(result) }

  private def timedNow[A](step: String, projectId: UUID)(block: => A): A = {
    val start = System.nanoTime()
    val result = block
    logStep(step, projectId, (System.nanoTime() - start) / 1000000)
    result
  }

  private def logStep(step: String, projectId: UUID, millis: Long): Unit =
    logger.info(s"Import step ${sp(step)} of ${sp(projectId)} took ${sp(millis)} ms")

  private def firstId(index: ExportIndex, entityType: DataExportEntityType): UUID =
    index.entriesOf(entityType).keys.headOption.getOrElse(new UUID(0L, 0L))

  private def importFromTree(
    projectId: UUID,
    importPath: String,
    index: ExportIndex,
    projectDump: ProjectDumpData
  ): IO[Either[ProjectError, DataImportResult]] = {
    val read = new TreeReader(importPath, index)
    val (statusesMalformed, statuses, labelsMalformed, labels, projectLabelsMalformed, projectLabels) =
      timedNow("read statuses and labels", projectId) {
        val (statusesMalformed, statuses) = read.all[TaskStatus](DataExportEntityType.TaskStatus)
        val (labelsMalformed, labels) = read.all[TaskLabel](DataExportEntityType.TaskLabel)
        val (projectLabelsMalformed, projectLabels) = read.all[ProjectLabelDumpData](DataExportEntityType.ProjectLabel)
        (statusesMalformed, statuses, labelsMalformed, labels, projectLabelsMalformed, projectLabels)
      }

    timed("resolve account mappings", projectId)(
      dataImportRepo
        .resolveAccountMappings(index.exportedBy :: projectDump.referencedAccounts)
        .transact(transactor)
    ).flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right(accountIdMap) =>
          timed("reconcile statuses and labels", projectId)(
            dataImportRepo
              .prepareImport(projectId, projectDump, statuses, labels, projectLabels)
              .transact(transactor)
          ).flatMap { prepared =>
              val remap = new Remapper(projectId, accountIdMap, prepared.ids)
              for {
                state <- timed("load existing project state", projectId)(dataImportRepo.loadImportState(projectId).transact(transactor))
                milestones <- mergeAll[MilestoneDumpData](projectId, read, DataExportEntityType.Milestone)(
                  m => dataImportRepo.mergeMilestone(remap.milestone(m))
                )
                tasks <- mergeAll[TaskDumpData](projectId, read, DataExportEntityType.Task)(
                  t => dataImportRepo.mergeTask(remap.task(t), state)
                )
                logEntries <- mergeAll[LogEntryDumpData](projectId, read, DataExportEntityType.LogEntry)(
                  le => dataImportRepo.mergeLogEntry(remap.logEntry(le), state)
                )
                templates <- mergeAll[TaskTemplateDumpData](projectId, read, DataExportEntityType.TaskTemplate)(
                  t => dataImportRepo.mergeTaskTemplate(remap.taskTemplate(t))
                )
                timeboxes <- mergeAll[TimeboxDumpData](projectId, read, DataExportEntityType.Timebox)(
                  t => dataImportRepo.mergeTimebox(remap.timebox(t))
                )
                members <- readMembers(read).fold(
                  malformed => IO.pure(List(malformed)),
                  members =>
                    dataImportRepo
                      .mergeMembers(projectId, members.accountIds.map(remap.account))
                      .transact(transactor)
                      .attempt
                      .map(_.fold(e => failed(DataExportEntityType.Members, projectId, e), identity))
                      .map(List(_))
                )
              } yield Right(
                DataImportResult(
                  importPath,
                  statusesMalformed ++ labelsMalformed ++ projectLabelsMalformed ++ prepared.results ++
                    milestones ++ tasks ++ logEntries ++ templates ++ timeboxes ++ members
                )
              )
            }
      }
  }

  private def readMembers(read: TreeReader): Maybe[MembersDumpData] =
    read.one[MembersDumpData](DataExportEntityType.Members, firstId(read.index, DataExportEntityType.Members))

  /** Merges the entities of one type one by one, and logs how long reading their files took against merging them. */
  private def mergeAll[A: Decoder](projectId: UUID, read: TreeReader, entityType: DataExportEntityType)(
    merge: A => doobie.ConnectionIO[DataImportEntityResult]
  ): IO[List[DataImportEntityResult]] =
    read.index.entriesOf(entityType).keys.toList.sorted
      .traverse { id =>
        for {
          readStart <- IO(System.nanoTime())
          readResult = read.one[A](entityType, id)
          mergeStart <- IO(System.nanoTime())
          result <- readResult match {
            case Left(malformed) => IO.pure(malformed)
            case Right(data) =>
              merge(data).transact(transactor).attempt.map(_.fold(e => failed(entityType, id, e), identity))
          }
          mergeEnd <- IO(System.nanoTime())
        } yield (result, mergeStart - readStart, mergeEnd - mergeStart)
      }
      .flatMap { timedResults =>
        val readMillis = timedResults.map(_._2).sum / 1000000
        val mergeMillis = timedResults.map(_._3).sum / 1000000
        IO(
          logger.info(
            s"Import step ${sp(s"merge ${entityType.folder}")} of ${sp(projectId)}: ${sp(timedResults.size)} entities, " +
              s"reading files took ${sp(readMillis)} ms, merging took ${sp(mergeMillis)} ms"
          )
        ).as(timedResults.map(_._1))
      }

  private def failed(entityType: DataExportEntityType, id: UUID, e: Throwable) =
    DataImportEntityResult.failed(entityType, id, DataImportStatus.Failed, String.valueOf(e.getMessage))

  /** Reads the files of the tree, verified against the hashes in the index. */
  private class TreeReader(path: String, val index: ExportIndex) {

    def one[A: Decoder](entityType: DataExportEntityType, id: UUID): Maybe[A] =
      dataExportTreeStore
        .readEntityJson(path, index, entityType, id)
        .flatMap(_.as[A].left.map(_.getMessage))
        .left
        .map(error => DataImportEntityResult.failed(entityType, id, DataImportStatus.Malformed, error))

    /** The readable entities, and a result for each one that could not be read. */
    def all[A: Decoder](entityType: DataExportEntityType): (List[DataImportEntityResult], List[A]) = {
      val read = index.entriesOf(entityType).keys.toList.sorted.map(id => one[A](entityType, id))
      (read.collect { case Left(malformed) => malformed }, read.collect { case Right(data) => data })
    }

  }

  /** Points exported entities at the target project, the mapped accounts and the reconciled statuses and labels. */
  private class Remapper(projectIdDb: UUID, accountIdMap: Map[UUID, UUID], ids: DataImportRepo#ReconciledIds) {

    def account(id: UUID): UUID = accountIdMap.getOrElse(id, id)
    private def accountOpt(id: Option[UUID]): Option[UUID] = id.map(account)
    private def label(id: UUID): UUID = ids.taskLabelIds.getOrElse(id, id)

    def task(data: TaskDumpData): TaskDumpData =
      data.copy(
        task = data.task.copy(projectId = projectIdDb, createdBy = account(data.task.createdBy)),
        taskVersions = data.taskVersions.map(v =>
          v.copy(
            taskStatusId = ids.taskStatusIds.getOrElse(v.taskStatusId, v.taskStatusId),
            assignedTo = accountOpt(v.assignedTo),
            closedBy = accountOpt(v.closedBy)
          )
        ),
        labelIds = data.labelIds.map(label)
      )

    def logEntry(data: LogEntryDumpData): LogEntryDumpData =
      data.copy(
        logEntry = data.logEntry.copy(createdBy = account(data.logEntry.createdBy)),
        versions = data.versions.map(v => v.copy(createdBy = account(v.createdBy)))
      )

    def milestone(data: MilestoneDumpData): MilestoneDumpData =
      data.copy(milestone = data.milestone.copy(projectId = projectIdDb))

    def taskTemplate(data: TaskTemplateDumpData): TaskTemplateDumpData =
      data.copy(template =
        data.template.copy(
          projectId = projectIdDb,
          taskLabelId = data.template.taskLabelId.map(label),
          createdBy = account(data.template.createdBy)
        )
      )

    def timebox(data: TimeboxDumpData): TimeboxDumpData =
      data.copy(timebox = data.timebox.copy(projectId = projectIdDb))

  }

}
