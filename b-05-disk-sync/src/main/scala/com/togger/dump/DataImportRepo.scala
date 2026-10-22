package com.togger.dump

import com.togger.db.TgPages
import cats.implicits._
import com.togger.account.{Account, AccountRepo, AccountTypeRepo, HashedPasswordImpl}
import com.togger.accountmapping.{AccountMapping, AccountMappingRepo}
import com.togger.common.{Password, TgClock, TgIds}
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo}
import com.togger.milestone._
import com.togger.project.ProjectError.{AccountTypeMissing, ProjectError}
import com.togger.project.{ProjectAccount, ProjectAccountRepo, ProjectLabelAssignment, ProjectLabelAssignmentRepo}
import com.togger.projectlabel.{ProjectLabelCategoryRepo, ProjectLabelRepo}
import com.togger.projectmapping.{ProjectMapping, ProjectMappingRepo}
import com.togger.task._
import com.togger.tasktemplate.{TaskTemplateRepo, TaskTemplateVersionRepo}
import com.togger.timebox.{TimeboxRepo, TimeboxVersionRepo}
import doobie.ConnectionIO

import java.security.SecureRandom
import java.util.UUID

class DataImportRepo(
  taskRepo: TaskRepo,
  taskVersionRepo: TaskVersionRepo,
  linkRepo: LinkRepo,
  linkVersionRepo: LinkVersionRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo,
  projectMappingRepo: ProjectMappingRepo,
  taskStatusRepo: TaskStatusRepo,
  tgClock: TgClock,
  accountRepo: AccountRepo,
  accountTypeRepo: AccountTypeRepo,
  accountMappingRepo: AccountMappingRepo,
  taskLabelRepo: TaskLabelRepo,
  taskLabelAssignmentRepo: TaskLabelAssignmentRepo,
  projectLabelRepo: ProjectLabelRepo,
  projectLabelCategoryRepo: ProjectLabelCategoryRepo,
  projectLabelAssignmentRepo: ProjectLabelAssignmentRepo,
  projectAccountRepo: ProjectAccountRepo,
  milestoneRepo: MilestoneRepo,
  milestoneVersionRepo: MilestoneVersionRepo,
  milestoneDateRepo: MilestoneDateRepo,
  milestoneDateVersionRepo: MilestoneDateVersionRepo,
  taskTemplateRepo: TaskTemplateRepo,
  taskTemplateVersionRepo: TaskTemplateVersionRepo,
  timeboxRepo: TimeboxRepo,
  timeboxVersionRepo: TimeboxVersionRepo
) {

  private case class ChildMergeOutcome(rootInserted: Boolean, versionsAdded: Int)

  def resolveAccountMapping(accountDumpData: AccountDumpData): ConnectionIO[Either[ProjectError, UUID]] =
    findMappingOfDumpAccount(accountDumpData.id).flatMap {
      case Some(mapping) =>
        mapping.accountIdDb.asRight[ProjectError].pure[ConnectionIO]
      case None =>
        resolveOrCreateAccountByEmail(accountDumpData).flatMap {
          case Left(error) => (error: ProjectError).asLeft[UUID].pure[ConnectionIO]
          case Right(accountIdDb) =>
            val now = tgClock.currentInstant
            accountMappingRepo
              .insert(AccountMapping(accountIdDb, accountDumpData.id, now, now))
              .map(_ => accountIdDb.asRight[ProjectError])
        }
    }

  /** A dump account maps to a handful of database accounts at most, so reading every page stays a single query. */
  private def findMappingOfDumpAccount(accountIdDump: UUID): ConnectionIO[Option[AccountMapping]] =
    TgPages.readAll(accountMappingRepo.findPaginatedByAccountIdDump(accountIdDump, _)).map(_.headOption)

  def resolveAccountMappings(accounts: List[AccountDumpData]): ConnectionIO[Either[ProjectError, Map[UUID, UUID]]] =
    accounts
      .distinctBy(_.id)
      .foldLeftM(Map.empty[UUID, UUID].asRight[ProjectError]) {
        case (acc @ Left(_), _) =>
          (acc: Either[ProjectError, Map[UUID, UUID]]).pure[ConnectionIO]
        case (Right(map), accountDumpData) =>
          resolveAccountMapping(accountDumpData).map(_.map(dbId => map + (accountDumpData.id -> dbId)))
      }

  private def resolveOrCreateAccountByEmail(accountDumpData: AccountDumpData): ConnectionIO[Either[ProjectError, UUID]] =
    accountRepo.findByEmail(accountDumpData.email).attempt.flatMap {
      case Right(account) => account.id.asRight[ProjectError].pure[ConnectionIO]
      case Left(_) =>
        accountTypeRepo.findByName("developer").attempt.flatMap {
          case Left(_) => (AccountTypeMissing: ProjectError).asLeft[UUID].pure[ConnectionIO]
          case Right(accountType) =>
            val now = tgClock.currentInstant
            val account = Account(
              id = TgIds.UNASSIGNED_UUID,
              firstName = accountDumpData.firstName,
              lastName = accountDumpData.lastName,
              email = accountDumpData.email,
              password = HashedPasswordImpl.fromPassword(generateRandomPassword()),
              accountTypeId = accountType.id,
              verified = false,
              enabled = true,
              createdAt = now,
              updatedAt = now
            )
            accountRepo.insert(account).map(_.id.asRight[ProjectError])
        }
    }

  private def generateRandomPassword(): Password = {
    val random = new SecureRandom
    def randomUuid = new UUID(random.nextLong(), random.nextLong())
    val rawPassword = s"Aa1!$randomUuid$randomUuid"
    Password.fromString(rawPassword).toOption.get
  }

  /** Ids of the target database that the exported ids of reconciled entities map to. */
  case class ReconciledIds(
    taskStatusIds: Map[UUID, UUID],
    taskLabelIds: Map[UUID, UUID],
    projectLabelIds: Map[UUID, UUID]
  )

  case class PrepareOutcome(ids: ReconciledIds, results: List[DataImportEntityResult])

  def prepareImport(
    projectIdDb: UUID,
    projectDump: ProjectDumpData,
    taskStatuses: List[TaskStatus],
    taskLabels: List[TaskLabel],
    projectLabels: List[ProjectLabelDumpData]
  ): ConnectionIO[PrepareOutcome] =
    // the project has one mapping per export it was imported from, a handful at most
    TgPages.readAll(projectMappingRepo.findPaginatedByProjectIdDb(projectIdDb, _)).flatMap { mappings =>
      val ensureMapping: ConnectionIO[Unit] =
        if (mappings.exists(_.projectIdDump == projectDump.project.id)) ().pure[ConnectionIO]
        else {
          val now = tgClock.currentInstant
          projectMappingRepo
            .insert(ProjectMapping(projectIdDb, projectDump.project.id, now, now))
            .void
        }
      for {
        _ <- ensureMapping
        statuses <- reconcileTaskStatuses(projectIdDb, taskStatuses)
        labels <- reconcileTaskLabels(projectIdDb, taskLabels)
        projectLabelsOutcome <- reconcileProjectLabels(projectIdDb, projectLabels)
      } yield PrepareOutcome(
        ReconciledIds(statuses._1, labels._1, projectLabelsOutcome._1),
        statuses._2 ++ labels._2 ++ projectLabelsOutcome._2
      )
    }

  private def reconciledResult(
    entityType: DataExportEntityType,
    exportedId: UUID,
    name: String,
    inserted: Boolean
  ): DataImportEntityResult =
    DataImportEntityResult.merged(entityType, exportedId, Some(name), rootInserted = inserted, 0, 0)

  private def reconcileTaskStatuses(
    projectIdDb: UUID,
    diskStatuses: List[TaskStatus]
  ): ConnectionIO[(Map[UUID, UUID], List[DataImportEntityResult])] =
    for {
      // reconciles every status of the project, so all pages are read
      dbStatuses <- TgPages.readAll(taskStatusRepo.findPaginatedByProjectId(projectIdDb, _))
      now = tgClock.currentInstant
      initialByKey = dbStatuses.map(s => (s.name, s.description) -> s.id).toMap
      folded <- diskStatuses.foldLeftM((initialByKey, Map.empty[UUID, UUID], List.empty[DataImportEntityResult])) {
        case ((byKey, idMap, results), diskStatus) =>
          byKey.get((diskStatus.name, diskStatus.description)) match {
            case Some(dbId) =>
              val result = reconciledResult(DataExportEntityType.TaskStatus, diskStatus.id, diskStatus.name, false)
              (byKey, idMap + (diskStatus.id -> dbId), result :: results).pure[ConnectionIO]
            case None =>
              taskStatusRepo.insert(diskStatus.copy(projectId = projectIdDb, createdAt = now, updatedAt = now)).map {
                created =>
                  val result = reconciledResult(DataExportEntityType.TaskStatus, diskStatus.id, diskStatus.name, true)
                  (
                    byKey + ((diskStatus.name, diskStatus.description) -> created.id),
                    idMap + (diskStatus.id -> created.id),
                    result :: results
                  )
              }
          }
      }
    } yield (folded._2, folded._3.reverse)

  private def reconcileTaskLabels(
    projectIdDb: UUID,
    diskLabels: List[TaskLabel]
  ): ConnectionIO[(Map[UUID, UUID], List[DataImportEntityResult])] =
    for {
      dbLabels <- TgPages.readAll(taskLabelRepo.findPaginatedByProjectId(projectIdDb, _))
      now = tgClock.currentInstant
      folded <- diskLabels.foldLeftM((dbLabels.map(l => l.name -> l.id).toMap, Map.empty[UUID, UUID], List.empty[DataImportEntityResult])) {
        case ((byName, idMap, results), diskLabel) =>
          byName.get(diskLabel.name) match {
            case Some(dbId) =>
              val result = reconciledResult(DataExportEntityType.TaskLabel, diskLabel.id, diskLabel.name, false)
              (byName, idMap + (diskLabel.id -> dbId), result :: results).pure[ConnectionIO]
            case None =>
              taskLabelRepo.insert(diskLabel.copy(projectId = projectIdDb, createdAt = now, updatedAt = now)).map {
                created =>
                  val result = reconciledResult(DataExportEntityType.TaskLabel, diskLabel.id, diskLabel.name, true)
                  (byName + (diskLabel.name -> created.id), idMap + (diskLabel.id -> created.id), result :: results)
              }
          }
      }
    } yield (folded._2, folded._3.reverse)

  /** Project labels and their categories are shared by all projects, so they are matched by name and only assigned to
    * the project when missing.
    */
  private def reconcileProjectLabels(
    projectIdDb: UUID,
    diskLabels: List[ProjectLabelDumpData]
  ): ConnectionIO[(Map[UUID, UUID], List[DataImportEntityResult])] =
    for {
      dbCategories <- TgPages.readAll(projectLabelCategoryRepo.findPaginated)
      dbLabels <- TgPages.readAll(projectLabelRepo.findPaginated)
      assigned <- projectLabelAssignmentRepo.findProjectLabelsByProjectId(projectIdDb)
      now = tgClock.currentInstant
      folded <- diskLabels.foldLeftM(
        (
          dbCategories.map(c => c.name -> c.id).toMap,
          dbLabels.map(l => l.name -> l.id).toMap,
          assigned.map(_.id).toSet,
          Map.empty[UUID, UUID],
          List.empty[DataImportEntityResult]
        )
      ) { case ((categoriesByName, labelsByName, assignedIds, idMap, results), disk) =>
        for {
          categoryOutcome <- categoriesByName.get(disk.category.name) match {
            case Some(id) => (id, categoriesByName).pure[ConnectionIO]
            case None =>
              projectLabelCategoryRepo
                .insert(disk.category.copy(createdAt = now, updatedAt = now))
                .map(created => (created.id, categoriesByName + (disk.category.name -> created.id)))
          }
          (categoryId, newCategories) = categoryOutcome
          labelOutcome <- labelsByName.get(disk.label.name) match {
            case Some(id) => (id, false, labelsByName).pure[ConnectionIO]
            case None =>
              projectLabelRepo
                .insert(disk.label.copy(categoryId = categoryId, createdAt = now, updatedAt = now))
                .map(created => (created.id, true, labelsByName + (disk.label.name -> created.id)))
          }
          (labelId, labelInserted, newLabels) = labelOutcome
          assignmentAdded <-
            if (assignedIds.contains(labelId)) false.pure[ConnectionIO]
            else projectLabelAssignmentRepo.insert(ProjectLabelAssignment(projectIdDb, labelId, now)).as(true)
        } yield (
          newCategories,
          newLabels,
          assignedIds + labelId,
          idMap + (disk.label.id -> labelId),
          reconciledResult(DataExportEntityType.ProjectLabel, disk.label.id, disk.label.name, labelInserted || assignmentAdded) :: results
        )
      }
    } yield (folded._4, folded._5.reverse)

  private def exists[A](io: ConnectionIO[A]): ConnectionIO[Boolean] = io.attempt.map(_.isRight)

  /** Inserts the versions the database does not know yet and returns how many were added. */
  private def insertMissing[V](versions: List[V])(
    isKnown: V => ConnectionIO[Boolean],
    insert: V => ConnectionIO[_]
  ): ConnectionIO[Int] =
    versions.traverse(v => isKnown(v).flatMap(known => if (known) false.pure[ConnectionIO] else insert(v).as(true))).map(_.count(identity))

  def mergeMembers(projectIdDb: UUID, accountIds: List[UUID]): ConnectionIO[DataImportEntityResult] =
    for {
      now <- tgClock.currentInstant.pure[ConnectionIO]
      added <- accountIds.distinct.traverse { accountId =>
        projectAccountRepo.isMember(projectIdDb, accountId).flatMap { member =>
          if (member) false.pure[ConnectionIO]
          else projectAccountRepo.insert(ProjectAccount(projectIdDb, accountId, now)).as(true)
        }
      }
    } yield DataImportEntityResult.merged(
      DataExportEntityType.Members,
      projectIdDb,
      None,
      rootInserted = false,
      versionsAdded = 0,
      childrenAdded = added.count(identity)
    )

  def mergeTask(diskData: TaskDumpData): ConnectionIO[DataImportEntityResult] =
    for {
      taskExists <- exists(taskRepo.findById(diskData.task.id))
      _ <- if (taskExists) ().pure[ConnectionIO] else taskRepo.insertWithId(diskData.task).void
      taskVersionsAdded <- insertMissing(diskData.taskVersions)(
        v => exists(taskVersionRepo.findByVersion(v.version)),
        taskVersionRepo.insertWithId
      )
      linkOutcomes <- diskData.links.traverse(mergeLink)
      assignedLabels <- taskLabelAssignmentRepo.findLabelsByTaskId(diskData.task.id).map(_.map(_.id).toSet)
      now = tgClock.currentInstant
      labelsAdded <- diskData.labelIds.distinct.filterNot(assignedLabels.contains).traverse { labelId =>
        taskLabelAssignmentRepo.insert(TaskLabelAssignment(diskData.task.id, labelId, now))
      }
    } yield DataImportEntityResult.merged(
      DataExportEntityType.Task,
      diskData.task.id,
      diskData.taskVersions.lastOption.map(_.name),
      rootInserted = !taskExists,
      versionsAdded = taskVersionsAdded + linkOutcomes.map(_.versionsAdded).sum,
      childrenAdded = linkOutcomes.count(_.rootInserted) + labelsAdded.size
    )

  private def mergeLink(linkDump: LinkDumpData): ConnectionIO[ChildMergeOutcome] =
    exists(linkRepo.findById(linkDump.link.id)).flatMap { linkExists =>
      for {
        _ <- if (linkExists) ().pure[ConnectionIO] else linkRepo.insertWithId(linkDump.link).void
        added <- insertMissing(linkDump.versions)(
          v => exists(linkVersionRepo.findByVersion(v.version)),
          linkVersionRepo.insertWithId
        )
      } yield ChildMergeOutcome(rootInserted = !linkExists, versionsAdded = added)
    }

  def mergeLogEntry(logEntryDump: LogEntryDumpData): ConnectionIO[DataImportEntityResult] =
    exists(logEntryRepo.findById(logEntryDump.logEntry.id)).flatMap { logEntryExists =>
      for {
        _ <- if (logEntryExists) ().pure[ConnectionIO] else logEntryRepo.insertWithId(logEntryDump.logEntry).void
        added <- insertMissing(logEntryDump.versions)(
          v => exists(logEntryVersionRepo.findByVersion(v.version)),
          logEntryVersionRepo.insertWithId
        )
      } yield DataImportEntityResult.merged(
        DataExportEntityType.LogEntry,
        logEntryDump.logEntry.id,
        None,
        rootInserted = !logEntryExists,
        versionsAdded = added,
        childrenAdded = 0
      )
    }

  def mergeMilestone(milestoneDump: MilestoneDumpData): ConnectionIO[DataImportEntityResult] =
    exists(milestoneRepo.findById(milestoneDump.milestone.id)).flatMap { milestoneExists =>
      for {
        _ <- if (milestoneExists) ().pure[ConnectionIO] else milestoneRepo.insertWithId(milestoneDump.milestone).void
        added <- insertMissing(milestoneDump.versions)(
          v => exists(milestoneVersionRepo.findByVersion(v.version)),
          milestoneVersionRepo.insertWithId
        )
        dateOutcomes <- milestoneDump.dates.traverse(mergeMilestoneDate)
      } yield DataImportEntityResult.merged(
        DataExportEntityType.Milestone,
        milestoneDump.milestone.id,
        milestoneDump.versions.lastOption.map(_.name),
        rootInserted = !milestoneExists,
        versionsAdded = added + dateOutcomes.map(_.versionsAdded).sum,
        childrenAdded = dateOutcomes.count(_.rootInserted)
      )
    }

  private def mergeMilestoneDate(dateDump: MilestoneDateDumpData): ConnectionIO[ChildMergeOutcome] =
    exists(milestoneDateRepo.findById(dateDump.milestoneDate.id)).flatMap { dateExists =>
      for {
        _ <- if (dateExists) ().pure[ConnectionIO] else milestoneDateRepo.insertWithId(dateDump.milestoneDate).void
        added <- insertMissing(dateDump.versions)(
          v => exists(milestoneDateVersionRepo.findByVersion(v.version)),
          milestoneDateVersionRepo.insertWithId
        )
      } yield ChildMergeOutcome(rootInserted = !dateExists, versionsAdded = added)
    }

  def mergeTaskTemplate(templateDump: TaskTemplateDumpData): ConnectionIO[DataImportEntityResult] =
    exists(taskTemplateRepo.findById(templateDump.template.id)).flatMap { templateExists =>
      for {
        _ <- if (templateExists) ().pure[ConnectionIO] else taskTemplateRepo.insertWithId(templateDump.template).void
        added <- insertMissing(templateDump.versions)(
          v => exists(taskTemplateVersionRepo.findByVersion(v.version)),
          taskTemplateVersionRepo.insertWithId
        )
      } yield DataImportEntityResult.merged(
        DataExportEntityType.TaskTemplate,
        templateDump.template.id,
        templateDump.versions.lastOption.map(_.name),
        rootInserted = !templateExists,
        versionsAdded = added,
        childrenAdded = 0
      )
    }

  def mergeTimebox(timeboxDump: TimeboxDumpData): ConnectionIO[DataImportEntityResult] =
    exists(timeboxRepo.findById(timeboxDump.timebox.id)).flatMap { timeboxExists =>
      for {
        _ <- if (timeboxExists) ().pure[ConnectionIO] else timeboxRepo.insertWithId(timeboxDump.timebox).void
        added <- insertMissing(timeboxDump.versions)(
          v => exists(timeboxVersionRepo.findByVersion(v.version.version)),
          v =>
            timeboxVersionRepo.insertWithId(v.version) *>
              timeboxVersionRepo.insertPlannedTasks(v.version.version, v.plannedTaskVersions)
        )
      } yield DataImportEntityResult.merged(
        DataExportEntityType.Timebox,
        timeboxDump.timebox.id,
        None,
        rootInserted = !timeboxExists,
        versionsAdded = added,
        childrenAdded = 0
      )
    }

}
