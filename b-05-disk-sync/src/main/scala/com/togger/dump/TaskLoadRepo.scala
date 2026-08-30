package com.togger.dump

import cats.implicits._
import com.togger.account.{Account, AccountRepo, AccountTypeRepo, HashedPasswordImpl}
import com.togger.accountmapping.{AccountMapping, AccountMappingRepo}
import com.togger.common.{Password, TgClock, TgIds}
import com.togger.logentry.{LogEntryRepo, LogEntryVersion, LogEntryVersionRepo}
import com.togger.project.ProjectError.{AccountTypeMissing, ProjectError}
import com.togger.projectmapping.{ProjectMapping, ProjectMappingRepo}
import com.togger.task._
import doobie.ConnectionIO

import java.security.SecureRandom
import java.util.UUID

class TaskLoadRepo(
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
  accountMappingRepo: AccountMappingRepo
) {

  private case class ChildMergeOutcome(rootInserted: Boolean, versionsAdded: Int)

  def resolveAccountMapping(accountDumpData: AccountDumpData): ConnectionIO[Either[ProjectError, UUID]] =
    accountMappingRepo.findByAccountIdDump(accountDumpData.id).map(_.headOption).flatMap {
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

  def prepareLoad(projectIdDb: UUID, manifest: ProjectDumpManifest): ConnectionIO[Map[UUID, UUID]] =
    projectMappingRepo.findByProjectIdDb(projectIdDb).flatMap { mappings =>
      val ensureMapping: ConnectionIO[Unit] =
        if (mappings.exists(_.projectIdDump == manifest.project.id)) ().pure[ConnectionIO]
        else {
          val now = tgClock.currentInstant
          projectMappingRepo
            .insert(ProjectMapping(projectIdDb, manifest.project.id, now, now))
            .void
        }
      ensureMapping *> reconcileTaskStatuses(projectIdDb, manifest.taskStatuses)
    }

  private def reconcileTaskStatuses(projectIdDb: UUID, diskStatuses: List[TaskStatus]): ConnectionIO[Map[UUID, UUID]] =
    for {
      dbStatuses <- taskStatusRepo.findByProjectId(projectIdDb)
      now = tgClock.currentInstant
      initialByKey = dbStatuses.map(s => (s.name, s.description) -> s.id).toMap
      folded <- diskStatuses.foldLeftM((initialByKey, Map.empty[UUID, UUID])) { case ((byKey, idMap), diskStatus) =>
        byKey.get((diskStatus.name, diskStatus.description)) match {
          case Some(dbId) =>
            (byKey, idMap + (diskStatus.id -> dbId)).pure[ConnectionIO]
          case None =>
            taskStatusRepo.insert(diskStatus.copy(projectId = projectIdDb, createdAt = now, updatedAt = now)).map {
              created =>
                (byKey + ((diskStatus.name, diskStatus.description) -> created.id), idMap + (diskStatus.id -> created.id))
            }
        }
      }
    } yield folded._2

  def mergeTask(diskData: TaskDumpData): ConnectionIO[TaskLoadTaskResult] =
    for {
      taskExists <- taskRepo.findById(diskData.task.id).attempt.map(_.isRight)
      _ <- if (taskExists) ().pure[ConnectionIO] else taskRepo.insertWithId(diskData.task).void
      taskVersionsAdded <- diskData.taskVersions.traverse(insertTaskVersionIfMissing).map(_.count(identity))
      linkOutcomes <- diskData.links.traverse(mergeLink)
      logEntryOutcomes <- diskData.logEntries.traverse(mergeLogEntry)
    } yield {
      val linksAdded = linkOutcomes.count(_.rootInserted)
      val logEntriesAdded = logEntryOutcomes.count(_.rootInserted)
      val versionsAdded =
        taskVersionsAdded + linkOutcomes.map(_.versionsAdded).sum + logEntryOutcomes.map(_.versionsAdded).sum
      val status =
        if (!taskExists) TaskLoadTaskStatus.Inserted
        else if (versionsAdded > 0 || linksAdded > 0 || logEntriesAdded > 0) TaskLoadTaskStatus.Merged
        else TaskLoadTaskStatus.Unchanged

      TaskLoadTaskResult(
        taskId = diskData.task.id,
        taskName = diskData.taskVersions.headOption.map(_.name),
        status = status,
        versionsAdded = versionsAdded,
        linksAdded = linksAdded,
        logEntriesAdded = logEntriesAdded,
        error = None
      )
    }

  private def insertTaskVersionIfMissing(taskVersion: TaskVersion): ConnectionIO[Boolean] =
    taskVersionRepo.findByVersion(taskVersion.version).attempt.flatMap {
      case Right(_) => false.pure[ConnectionIO]
      case Left(_)  => taskVersionRepo.insertWithId(taskVersion).as(true)
    }

  private def mergeLink(linkDump: LinkDumpData): ConnectionIO[ChildMergeOutcome] =
    linkRepo.findById(linkDump.link.id).attempt.flatMap {
      case Left(_) =>
        for {
          _ <- linkRepo.insertWithId(linkDump.link)
          _ <- linkDump.versions.traverse(linkVersionRepo.insertWithId)
        } yield ChildMergeOutcome(rootInserted = true, versionsAdded = linkDump.versions.size)
      case Right(_) =>
        linkDump.versions
          .traverse(insertLinkVersionIfMissing)
          .map(added => ChildMergeOutcome(rootInserted = false, versionsAdded = added.count(identity)))
    }

  private def insertLinkVersionIfMissing(linkVersion: LinkVersion): ConnectionIO[Boolean] =
    linkVersionRepo.findByVersion(linkVersion.version).attempt.flatMap {
      case Right(_) => false.pure[ConnectionIO]
      case Left(_)  => linkVersionRepo.insertWithId(linkVersion).as(true)
    }

  private def mergeLogEntry(logEntryDump: LogEntryDumpData): ConnectionIO[ChildMergeOutcome] =
    logEntryRepo.findById(logEntryDump.logEntry.id).attempt.flatMap {
      case Left(_) =>
        for {
          _ <- logEntryRepo.insertWithId(logEntryDump.logEntry)
          _ <- logEntryDump.versions.traverse(logEntryVersionRepo.insertWithId)
        } yield ChildMergeOutcome(rootInserted = true, versionsAdded = logEntryDump.versions.size)
      case Right(_) =>
        logEntryDump.versions
          .traverse(insertLogEntryVersionIfMissing)
          .map(added => ChildMergeOutcome(rootInserted = false, versionsAdded = added.count(identity)))
    }

  private def insertLogEntryVersionIfMissing(logEntryVersion: LogEntryVersion): ConnectionIO[Boolean] =
    logEntryVersionRepo.findByVersion(logEntryVersion.version).attempt.flatMap {
      case Right(_) => false.pure[ConnectionIO]
      case Left(_)  => logEntryVersionRepo.insertWithId(logEntryVersion).as(true)
    }

}
