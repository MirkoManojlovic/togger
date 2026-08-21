package com.togger.dump

import cats.implicits._
import com.togger.logentry.{LogEntryRepo, LogEntryVersion, LogEntryVersionRepo}
import com.togger.task._
import doobie.ConnectionIO

class TaskLoadRepo(
  taskRepo: TaskRepo,
  taskVersionRepo: TaskVersionRepo,
  linkRepo: LinkRepo,
  linkVersionRepo: LinkVersionRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo
) {

  private case class ChildMergeOutcome(rootInserted: Boolean, versionsAdded: Int)

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
