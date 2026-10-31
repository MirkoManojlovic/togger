package com.togger.preview

import com.togger.demo.{DemoTask, DemoTimebox}

/** Seeds tasks with their versions, links, log entries and the timeboxes. Needs the accounts, projects, task config
  * and milestones to be seeded first.
  */
class TasksPreviewSeed(data: TasksPreviewData, tasks: List[DemoTask], timeboxes: List[DemoTimebox])
    extends PreviewSeeder {

  override def seed(): Unit = {
    data.tasks.replaceAll(tasks.map(t => PreviewTask(t.id, t.projectId, t.createdBy, t.createdAt, t.archived, t.labelIds)))
    data.taskVersions.replaceAll(tasks.flatMap(_.versions))
    data.links.replaceAll(tasks.flatMap(t => t.links.map(l => PreviewLink(l.id, t.id, l.createdAt))))
    data.linkVersions.replaceAll(tasks.flatMap(_.links).flatMap(_.versions))
    data.logEntries.replaceAll(
      tasks.flatMap(t => t.logEntries.map(e => PreviewLogEntry(e.id, t.id, e.createdBy, e.createdAt)))
    )
    data.logEntryVersions.replaceAll(tasks.flatMap(_.logEntries).flatMap(_.versions))
    timeboxes.foreach { timebox =>
      data.timeboxes.upsert(PreviewTimebox(timebox.id, timebox.projectId, timebox.createdAt))
      timebox.versions.foreach { v =>
        data.timeboxVersions.upsert(
          PreviewTimeboxVersion(
            v.version,
            timebox.id,
            v.startDate,
            v.endDate,
            v.description,
            v.createdAt,
            v.changeComment,
            v.taskVersionIds
          )
        )
      }
    }
  }
}
