package com.togger.dump

import cats.implicits._
import com.togger.account.Account
import com.togger.account.AccountRepo
import com.togger.account.AccountTypeRepo
import com.togger.account.HashedPasswordImpl
import com.togger.accountmapping.AccountMapping
import com.togger.accountmapping.AccountMappingRepo
import com.togger.common.Password
import com.togger.common.TgClock
import com.togger.common.TgIds
import com.togger.db.TgPages
import com.togger.deletedentity.DeletedEntity
import com.togger.deletedentity.DeletedEntityRepo
import com.togger.deletedentity.DeletedEntityType
import com.togger.logentry.LogEntry
import com.togger.logentry.LogEntryRepo
import com.togger.logentry.LogEntryVersion
import com.togger.logentry.LogEntryVersionRepo
import com.togger.milestone._
import com.togger.milestonesteptemplate.MilestoneStepTemplate
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepo
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersionRepo
import com.togger.milestonetemplate.MilestoneTemplateFull
import com.togger.milestonetemplate.MilestoneTemplateRepo
import com.togger.milestonetemplate.MilestoneTemplateVersionRepo
import com.togger.project.Project
import com.togger.project.ProjectAccount
import com.togger.project.ProjectAccountRepo
import com.togger.project.ProjectError.AccountTypeMissing
import com.togger.project.ProjectError.ProjectError
import com.togger.project.ProjectLabelAssignment
import com.togger.project.ProjectLabelAssignmentRepo
import com.togger.project.ProjectRepo
import com.togger.projectlabel.ProjectLabelCategoryRepo
import com.togger.projectlabel.ProjectLabelRepo
import com.togger.projectmapping.ProjectMapping
import com.togger.projectmapping.ProjectMappingRepo
import com.togger.task._
import com.togger.tasktemplate.TaskTemplate
import com.togger.tasktemplate.TaskTemplateRepo
import com.togger.tasktemplate.TaskTemplateVersion
import com.togger.tasktemplate.TaskTemplateVersionRepo
import com.togger.tgprompttemplate.TgPromptTemplateRepo
import com.togger.tgprompttemplate.TgPromptTemplateVersionRepo
import com.togger.timebox.TimeboxRepo
import com.togger.timebox.TimeboxVersionRepo
import doobie.ConnectionIO

import java.security.SecureRandom
import java.time.Instant
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
  milestoneSummaryRepo: MilestoneSummaryRepo,
  milestoneSummaryVersionRepo: MilestoneSummaryVersionRepo,
  milestoneStepRepo: MilestoneStepRepo,
  milestoneStepVersionRepo: MilestoneStepVersionRepo,
  milestoneStepTemplateRepo: MilestoneStepTemplateRepo,
  milestoneStepTemplateVersionRepo: MilestoneStepTemplateVersionRepo,
  milestoneTemplateRepo: MilestoneTemplateRepo,
  milestoneTemplateVersionRepo: MilestoneTemplateVersionRepo,
  taskTemplateRepo: TaskTemplateRepo,
  taskTemplateVersionRepo: TaskTemplateVersionRepo,
  timeboxRepo: TimeboxRepo,
  timeboxVersionRepo: TimeboxVersionRepo,
  tgPromptTemplateRepo: TgPromptTemplateRepo,
  tgPromptTemplateVersionRepo: TgPromptTemplateVersionRepo,
  projectRepo: ProjectRepo,
  deletedEntityRepo: DeletedEntityRepo
) {

  private case class ChildMergeOutcome(rootInserted: Boolean, versionsAdded: Int)

  /** A new project in the organization with the fields of the exported one and the importing account as its member. */
  def createProject(organizationId: UUID, accountId: UUID, exported: Project): ConnectionIO[Project] = {
    val now = tgClock.currentInstant
    for {
      project <- projectRepo.insert(
        exported.copy(id = UUID.randomUUID(), organizationId = organizationId, createdAt = now, updatedAt = now)
      )
      _ <- projectAccountRepo.insert(ProjectAccount(project.id, accountId, now))
    } yield project
  }

  /** Applies the exported description, icon and work days to the project when the disk copy is newer. The name is left
    * alone, as the import check ignores it too, and the disk's `updatedAt` is kept so that the check finds both in
    * sync.
    */
  def mergeProject(projectIdDb: UUID, exported: Project): ConnectionIO[DataImportEntityResult] =
    projectRepo.findById(projectIdDb).flatMap { current =>
      val changed = current.description != exported.description || current.icon != exported.icon ||
        current.workDays != exported.workDays
      if (changed && exported.updatedAt.isAfter(current.updatedAt))
        projectRepo
          .update(
            current.copy(description = exported.description, icon = exported.icon, workDays = exported.workDays),
            exported.updatedAt
          )
          .as(DataImportEntityResult.updated(DataExportEntityType.Project, projectIdDb, Some(current.name)))
      else
        DataImportEntityResult
          .merged(DataExportEntityType.Project, projectIdDb, Some(current.name), false, 0, 0)
          .pure[ConnectionIO]
    }

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

  private def resolveOrCreateAccountByEmail(
    accountDumpData: AccountDumpData
  ): ConnectionIO[Either[ProjectError, UUID]] =
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
      folded <- diskLabels.foldLeftM(
        (dbLabels.map(l => l.name -> l.id).toMap, Map.empty[UUID, UUID], List.empty[DataImportEntityResult])
      ) { case ((byName, idMap, results), diskLabel) =>
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
          reconciledResult(
            DataExportEntityType.ProjectLabel,
            disk.label.id,
            disk.label.name,
            labelInserted || assignmentAdded
          ) :: results
        )
      }
    } yield (folded._4, folded._5.reverse)

  /** What the project already holds of the entities that dominate an import, read once instead of probing the database
    * for every task, link, log entry and version.
    */
  case class ProjectImportState(
    taskIds: Set[UUID],
    taskVersions: Set[UUID],
    linkIds: Set[UUID],
    linkVersions: Set[UUID],
    logEntryIds: Set[UUID],
    logEntryVersions: Set[UUID],
    labelIdsByTask: Map[UUID, Set[UUID]],
    taskChangedAt: Map[UUID, Instant],
    logEntryChangedAt: Map[UUID, Instant],
    tombstones: Map[(String, UUID), Instant],
    labelsAssignedAt: Map[UUID, Instant]
  )

  def loadImportState(projectId: UUID): ConnectionIO[ProjectImportState] =
    for {
      tasks <- taskRepo.findAllByProjectId(projectId)
      taskVersions <- taskVersionRepo.findAllByProjectId(projectId)
      links <- linkRepo.findAllByProjectId(projectId)
      linkVersions <- linkVersionRepo.findAllByProjectId(projectId)
      logEntries <- logEntryRepo.findAllByProjectId(projectId)
      logEntryVersions <- logEntryVersionRepo.findAllByProjectId(projectId)
      labels <- taskLabelAssignmentRepo.findLabelsByProjectId(projectId)
      tombstones <- deletedEntityRepo.findAllByProjectId(projectId)
      assignments <- taskLabelAssignmentRepo.findByProjectId(projectId)
      labelsAssignedAt = assignments.groupMapReduce(_.taskId)(_.createdAt)((a, b) => if (a.isAfter(b)) a else b)
    } yield ProjectImportState(
      tasks.map(_.id).toSet,
      taskVersions.map(_.version).toSet,
      links.map(_.id).toSet,
      linkVersions.map(_.version).toSet,
      logEntries.map(_.id).toSet,
      logEntryVersions.map(_.version).toSet,
      labels.view.mapValues(_.map(_.id).toSet).toMap,
      taskChangedAt(tasks, taskVersions, links, linkVersions, labelsAssignedAt),
      logEntryChangedAt(logEntries, logEntryVersions),
      tombstones.map(t => (t.entityType, t.entityId) -> t.deletedAt).toMap,
      labelsAssignedAt
    )

  /** The newest creation or update inside a task as the export dumps it: the task, its versions, its links and their
    * versions.
    */
  private def taskChangedAt(
    tasks: List[Task],
    taskVersions: List[TaskVersion],
    links: List[Link],
    linkVersions: List[LinkVersion],
    labelsAssignedAt: Map[UUID, Instant]
  ): Map[UUID, Instant] = {
    val linkTask = links.map(l => l.id -> l.taskId).toMap
    val stamps = tasks.map(t => t.id -> t.createdAt) ++
      taskVersions.flatMap(v => List(v.taskId -> v.createdAt, v.taskId -> v.updatedAt)) ++
      links.map(l => l.taskId -> l.createdAt) ++
      labelsAssignedAt.toList ++
      linkVersions.flatMap(v =>
        linkTask.get(v.linkId).toList.flatMap(taskId => List(taskId -> v.createdAt, taskId -> v.updatedAt))
      )
    stamps.groupMapReduce(_._1)(_._2)((a, b) => if (a.isAfter(b)) a else b)
  }

  private def logEntryChangedAt(logEntries: List[LogEntry], versions: List[LogEntryVersion]): Map[UUID, Instant] = {
    val versionsByEntry = versions.groupBy(_.logEntryId)
    logEntries.flatMap { entry =>
      val entryVersions = versionsByEntry.getOrElse(entry.id, Nil)
      val timestamps = entry.createdAt :: entryVersions.flatMap(v => List(v.createdAt, v.updatedAt))
      LogEntryChangedAt.of(entry.id, entryVersions.map(_.version), timestamps).map(entry.id -> _)
    }.toMap
  }

  /** Applies the deletions of the tree. A task or log entry that the project has is deleted unless it changed after the
    * deletion, in which case it is kept. Every deletion is recorded in the project, whether the entity is there or not,
    * so that the next export carries it on and an older tree cannot bring the entity back.
    */
  def applyTombstones(
    projectId: UUID,
    tombstones: Map[String, Map[String, Instant]],
    state: ProjectImportState
  ): ConnectionIO[List[DataImportEntityResult]] = {
    def deletions(folder: String): List[(UUID, Instant)] =
      tombstones.getOrElse(folder, Map.empty).toList.map { case (id, at) => UUID.fromString(id) -> at }.sortBy(_._1)

    def keep(entityType: DataExportEntityType, id: UUID): DataImportEntityResult =
      DataImportEntityResult.skipped(entityType, id, "changed after it was deleted in the tree, so it is kept")

    val tasks = deletions(DeletedEntityType.Task).traverse { case (id, deletedAt) =>
      val changedAt = state.taskChangedAt.get(id)
      if (changedAt.exists(_.isAfter(deletedAt))) List(keep(DataExportEntityType.Task, id)).pure[ConnectionIO]
      else
        for {
          logEntries <- logEntryRepo.findByTaskId(id)
          deleted <- taskRepo.deleteById(id)
          _ <- deletedEntityRepo.insertAll(
            DeletedEntity(projectId, DeletedEntityType.Task, id, deletedAt) ::
              logEntries.map(e => DeletedEntity(projectId, DeletedEntityType.LogEntry, e.id, deletedAt))
          )
        } yield if (deleted > 0) List(DataImportEntityResult.deleted(DataExportEntityType.Task, id)) else Nil
    }
    val logEntries = deletions(DeletedEntityType.LogEntry).traverse { case (id, deletedAt) =>
      val changedAt = state.logEntryChangedAt.get(id)
      if (changedAt.exists(_.isAfter(deletedAt))) List(keep(DataExportEntityType.LogEntry, id)).pure[ConnectionIO]
      else
        for {
          deleted <- logEntryRepo.deleteById(id)
          _ <- deletedEntityRepo.insertAll(List(DeletedEntity(projectId, DeletedEntityType.LogEntry, id, deletedAt)))
        } yield if (deleted > 0) List(DataImportEntityResult.deleted(DataExportEntityType.LogEntry, id)) else Nil
    }
    for {
      taskResults <- tasks
      logEntryResults <- logEntries
    } yield (taskResults ++ logEntryResults).flatten
  }

  def clearTombstone(projectId: UUID, folder: String, id: UUID): ConnectionIO[Int] =
    deletedEntityRepo.deleteByEntity(projectId, folder, id)

  private def exists[A](io: ConnectionIO[A]): ConnectionIO[Boolean] = io.attempt.map(_.isRight)

  /** Inserts the versions the database does not know yet and returns how many were added. */
  private def insertMissing[V](versions: List[V])(
    isKnown: V => ConnectionIO[Boolean],
    insert: V => ConnectionIO[_]
  ): ConnectionIO[Int] =
    versions
      .traverse(v => isKnown(v).flatMap(known => if (known) false.pure[ConnectionIO] else insert(v).as(true)))
      .map(_.count(identity))

  /** Inserts the versions that are not known yet and returns how many were added. */
  private def insertUnknown[V](versions: List[V])(
    isKnown: V => Boolean,
    insert: V => ConnectionIO[_]
  ): ConnectionIO[Int] =
    versions.filterNot(isKnown).traverse(v => insert(v).void).map(_.size)

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

  /** A task whose links carry an address that the link validation does not allow is not imported at all. */
  def mergeTask(diskData: TaskDumpData, state: ProjectImportState): ConnectionIO[DataImportEntityResult] =
    unallowedLinkUrl(diskData).fold(mergeAllowedTask(diskData, state))(url =>
      DataImportEntityResult
        .failed(
          DataExportEntityType.Task,
          diskData.task.id,
          DataImportStatus.Malformed,
          s"${LinkValidation.LinkUrlNotAllowed.message}: $url"
        )
        .pure[ConnectionIO]
    )

  private def unallowedLinkUrl(diskData: TaskDumpData): Option[String] =
    diskData.links.flatMap(_.versions).map(_.url).find(url => url.trim.nonEmpty && LinkValidation.safeHref(url).isEmpty)

  private def mergeAllowedTask(
    diskData: TaskDumpData,
    state: ProjectImportState
  ): ConnectionIO[DataImportEntityResult] =
    for {
      taskExists <- state.taskIds.contains(diskData.task.id).pure[ConnectionIO]
      _ <- if (taskExists) ().pure[ConnectionIO] else taskRepo.insertWithId(diskData.task).void
      taskVersionsAdded <- insertUnknown(diskData.taskVersions)(
        v => state.taskVersions.contains(v.version),
        taskVersionRepo.insertWithId
      )
      linkOutcomes <- diskData.links.traverse(mergeLink(_, state))
      labelsAdded <- mergeLabels(diskData, state)
    } yield DataImportEntityResult.merged(
      DataExportEntityType.Task,
      diskData.task.id,
      diskData.taskVersions.lastOption.map(_.name),
      rootInserted = !taskExists,
      versionsAdded = taskVersionsAdded + linkOutcomes.map(_.versionsAdded).sum,
      childrenAdded = linkOutcomes.count(_.rootInserted) + labelsAdded.size
    )

  /** The labels of a task are the set of its last update, so the newer set replaces the older one: a label that the
    * newer side removed goes too. A tree without the time (an old one) only adds the labels the task does not have. The
    * labels that are set keep the time of the tree, so that the next export carries it on.
    */
  private def mergeLabels(
    diskData: TaskDumpData,
    state: ProjectImportState
  ): ConnectionIO[List[TaskLabelAssignment]] = {
    val taskId = diskData.task.id
    val assigned = state.labelIdsByTask.getOrElse(taskId, Set.empty[UUID])
    val diskLabels = diskData.labelIds.distinct
    def insertAll(labelIds: List[UUID], at: Instant) =
      labelIds.traverse(labelId => taskLabelAssignmentRepo.insert(TaskLabelAssignment(taskId, labelId, at)))
    (diskData.labelsAssignedAt, state.labelsAssignedAt.get(taskId)) match {
      case (None, _) => insertAll(diskLabels.filterNot(assigned.contains), tgClock.currentInstant)
      case (Some(diskAt), Some(dbAt)) if !diskAt.isAfter(dbAt) => List.empty[TaskLabelAssignment].pure[ConnectionIO]
      case (Some(diskAt), _) => taskLabelAssignmentRepo.deleteAllByTaskId(taskId) *> insertAll(diskLabels, diskAt)
    }
  }

  private def mergeLink(linkDump: LinkDumpData, state: ProjectImportState): ConnectionIO[ChildMergeOutcome] = {
    val linkExists = state.linkIds.contains(linkDump.link.id)
    for {
      _ <- if (linkExists) ().pure[ConnectionIO] else linkRepo.insertWithId(linkDump.link).void
      added <- insertUnknown(linkDump.versions)(
        v => state.linkVersions.contains(v.version),
        linkVersionRepo.insertWithId
      )
    } yield ChildMergeOutcome(rootInserted = !linkExists, versionsAdded = added)
  }

  def mergeLogEntry(logEntryDump: LogEntryDumpData, state: ProjectImportState): ConnectionIO[DataImportEntityResult] = {
    val logEntryExists = state.logEntryIds.contains(logEntryDump.logEntry.id)
    for {
      _ <- if (logEntryExists) ().pure[ConnectionIO] else logEntryRepo.insertWithId(logEntryDump.logEntry).void
      added <- insertUnknown(logEntryDump.versions)(
        v => state.logEntryVersions.contains(v.version),
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
        summaryOutcomes <- milestoneDump.summaries.traverse(mergeMilestoneSummary)
        stepOutcomes <- milestoneDump.steps.traverse(mergeMilestoneStep)
        childOutcomes = dateOutcomes ++ summaryOutcomes ++ stepOutcomes
      } yield DataImportEntityResult.merged(
        DataExportEntityType.Milestone,
        milestoneDump.milestone.id,
        milestoneDump.versions.lastOption.map(_.name),
        rootInserted = !milestoneExists,
        versionsAdded = added + childOutcomes.map(_.versionsAdded).sum,
        childrenAdded = childOutcomes.count(_.rootInserted)
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

  private def mergeMilestoneSummary(summaryDump: MilestoneSummaryDumpData): ConnectionIO[ChildMergeOutcome] =
    exists(milestoneSummaryRepo.findById(summaryDump.milestoneSummary.id)).flatMap { summaryExists =>
      for {
        _ <-
          if (summaryExists) ().pure[ConnectionIO]
          else milestoneSummaryRepo.insertWithId(summaryDump.milestoneSummary).void
        added <- insertMissing(summaryDump.versions)(
          v => exists(milestoneSummaryVersionRepo.findByVersion(v.version)),
          milestoneSummaryVersionRepo.insertWithId
        )
      } yield ChildMergeOutcome(rootInserted = !summaryExists, versionsAdded = added)
    }

  private def mergeMilestoneStep(stepDump: MilestoneStepDumpData): ConnectionIO[ChildMergeOutcome] =
    exists(milestoneStepRepo.findById(stepDump.milestoneStep.id)).flatMap { stepExists =>
      for {
        _ <- if (stepExists) ().pure[ConnectionIO] else milestoneStepRepo.insertWithId(stepDump.milestoneStep).void
        added <- insertMissing(stepDump.versions)(
          v => exists(milestoneStepVersionRepo.findByVersion(v.version)),
          milestoneStepVersionRepo.insertWithId
        )
      } yield ChildMergeOutcome(rootInserted = !stepExists, versionsAdded = added)
    }

  /** The step templates of the dump that the project has under another id, by the name of their latest version, as
    * every project starts with the default ones: they map to the step template of the project. A step template the
    * project already has under the same id is not in the result.
    */
  def stepTemplateTargets(
    projectId: UUID,
    dumps: List[MilestoneStepTemplateDumpData]
  ): ConnectionIO[Map[UUID, UUID]] =
    TgPages.readAll(milestoneStepTemplateRepo.findPaginatedFullByProjectId(projectId, _)).map { existing =>
      val existingIds = existing.map(_.template.id).toSet
      val idByName = existing.map(full => full.latestVersion.name -> full.template.id).toMap
      dumps
        .filterNot(dump => existingIds.contains(dump.template.id))
        .flatMap(dump => dump.versions.lastOption.flatMap(v => idByName.get(v.name)).map(dump.template.id -> _))
        .toMap
    }

  /** A step template that the project has under another id is merged into that one: its versions are added to it
    * instead of inserting a second one.
    */
  def mergeMilestoneStepTemplate(
    templateDump: MilestoneStepTemplateDumpData,
    targetId: Option[UUID]
  ): ConnectionIO[DataImportEntityResult] =
    for {
      templateExists <- exists(milestoneStepTemplateRepo.findById(templateDump.template.id))
      target <-
        if (templateExists) Option.empty[MilestoneStepTemplate].pure[ConnectionIO]
        else targetId.traverse(milestoneStepTemplateRepo.findById)
      _ <-
        if (templateExists || target.isDefined) ().pure[ConnectionIO]
        else milestoneStepTemplateRepo.insertWithId(templateDump.template).void
      versions <- target.fold(templateDump.versions.pure[ConnectionIO])(existing =>
        milestoneStepTemplateVersionRepo
          .findLatestByTemplateId(existing.id)
          .map(latest =>
            templateDump.versions
              .map(_.copy(milestoneStepTemplateId = existing.id))
              .filterNot(_.hasSameContentAs(latest))
          )
      )
      added <- insertMissing(versions)(
        v => exists(milestoneStepTemplateVersionRepo.findByVersion(v.version)),
        milestoneStepTemplateVersionRepo.insertWithId
      )
    } yield DataImportEntityResult.merged(
      DataExportEntityType.MilestoneStepTemplate,
      templateDump.template.id,
      templateDump.versions.lastOption.map(_.name),
      rootInserted = !templateExists && target.isEmpty,
      versionsAdded = added,
      childrenAdded = 0
    )

  /** A template whose name the project already has under another id (e.g. a default one) is merged into that one: the
    * versions that differ from its latest are added to it instead of inserting a second template. The step templates of
    * the dump have to be mapped to those of the project already.
    */
  def mergeMilestoneTemplate(templateDump: MilestoneTemplateDumpData): ConnectionIO[DataImportEntityResult] =
    for {
      templateExists <- exists(milestoneTemplateRepo.findById(templateDump.template.id))
      sameName <-
        if (templateExists) Option.empty[MilestoneTemplateFull].pure[ConnectionIO]
        else
          TgPages
            .readAll(milestoneTemplateRepo.findPaginatedFullByProjectId(templateDump.template.projectId, _))
            .map(existing =>
              templateDump.versions.lastOption.flatMap(v => existing.find(_.latest.version.name == v.version.name))
            )
      _ <-
        if (templateExists || sameName.isDefined) ().pure[ConnectionIO]
        else milestoneTemplateRepo.insertWithId(templateDump.template).void
      versions = sameName.fold(templateDump.versions)(existing =>
        templateDump.versions
          .map(v => v.copy(version = v.version.copy(milestoneTemplateId = existing.template.id)))
          .filterNot(v => existing.latest.hasSameContentAs(v.version.name, v.version.description, v.stages))
      )
      added <- insertMissing(versions)(
        v => exists(milestoneTemplateVersionRepo.findByVersion(v.version.version)),
        v =>
          milestoneTemplateVersionRepo
            .insertWithId(v.version) *> milestoneTemplateVersionRepo.insertSteps(v.version.version, v.stages)
      )
    } yield DataImportEntityResult.merged(
      DataExportEntityType.MilestoneTemplate,
      templateDump.template.id,
      templateDump.versions.lastOption.map(_.version.name),
      rootInserted = !templateExists && sameName.isEmpty,
      versionsAdded = added,
      childrenAdded = 0
    )

  /** A label has at most one template, so a template whose label already has another one (e.g. a project's default
    * template) is merged into that one: its versions are added to it instead of inserting a second template.
    */
  def mergeTaskTemplate(templateDump: TaskTemplateDumpData): ConnectionIO[DataImportEntityResult] =
    for {
      templateExists <- exists(taskTemplateRepo.findById(templateDump.template.id))
      labelTemplate <-
        if (templateExists) Option.empty[TaskTemplate].pure[ConnectionIO]
        else templateDump.template.taskLabelId.flatTraverse(taskTemplateRepo.findByLabelId)
      target = labelTemplate.getOrElse(templateDump.template)
      _ <-
        if (templateExists || labelTemplate.isDefined) ().pure[ConnectionIO]
        else taskTemplateRepo.insertWithId(target).void
      versions <- labelTemplate.fold(templateDump.versions.pure[ConnectionIO])(existing =>
        withoutRepeatedContent(existing, templateDump.versions.map(_.copy(taskTemplateId = existing.id)))
      )
      added <- insertMissing(versions)(
        v => exists(taskTemplateVersionRepo.findByVersion(v.version)),
        taskTemplateVersionRepo.insertWithId
      )
    } yield DataImportEntityResult.merged(
      DataExportEntityType.TaskTemplate,
      templateDump.template.id,
      templateDump.versions.lastOption.map(_.name),
      rootInserted = !templateExists && labelTemplate.isEmpty,
      versionsAdded = added,
      childrenAdded = 0
    )

  /** The versions that differ from the latest one of the template, so merging identical default templates adds nothing.
    */
  private def withoutRepeatedContent(
    template: TaskTemplate,
    versions: List[TaskTemplateVersion]
  ): ConnectionIO[List[TaskTemplateVersion]] =
    taskTemplateVersionRepo
      .findLatestByTemplateId(template.id)
      .map(latest => versions.filterNot(_.hasSameContentAs(latest)))

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

  def mergeTgPromptTemplate(promptDump: TgPromptTemplateDumpData): ConnectionIO[DataImportEntityResult] =
    exists(tgPromptTemplateRepo.findById(promptDump.tgPromptTemplate.id)).flatMap { promptExists =>
      for {
        _ <-
          if (promptExists) ().pure[ConnectionIO]
          else tgPromptTemplateRepo.insertWithId(promptDump.tgPromptTemplate).void
        added <- insertMissing(promptDump.versions)(
          v => exists(tgPromptTemplateVersionRepo.findByVersion(v.version)),
          tgPromptTemplateVersionRepo.insertWithId
        )
      } yield DataImportEntityResult.merged(
        DataExportEntityType.TgPromptTemplate,
        promptDump.tgPromptTemplate.id,
        promptDump.versions.lastOption.map(_.title),
        rootInserted = !promptExists,
        versionsAdded = added,
        childrenAdded = 0
      )
    }

}
