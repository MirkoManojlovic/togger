package com.togger.preview

import com.togger.common.TgClock
import com.togger.ui.auth.LoginRepo
import com.togger.ui.localstorage.LocalStorage

/** Entry point of the frontend preview mode: the storage, the shared context and the demo data seeding.
  *
  * `storage` must be the build id prefixed one. Every domain registers its seeder in [[PreviewModule.seeders]].
  */
class PreviewModule(storage: LocalStorage, loginRepo: LoginRepo, clock: TgClock) {

  val db = new PreviewDb(storage)

  val context = new PreviewContext(db, loginRepo, clock)

  /** Seeds the demo data once per build id (the marker key lives under the build id prefix). */
  def ensureSeeded(): Unit =
    if (!db.isSeeded) {
      seeders.foreach(_.seed())
      db.markSeeded()
    }

  /** Throws every stored preview row away and seeds the demo data again. */
  def resetAndReseed(): Unit = {
    allHolders
    db.reset()
    ensureSeeded()
  }

  lazy val accounts = new AccountsPreviewData(db)

  lazy val organizations = new OrganizationsPreviewData(db)

  lazy val projects = new ProjectsPreviewData(db)

  lazy val projectLabels = new ProjectLabelsPreviewData(db, projects)

  lazy val taskConfig = new TaskConfigPreviewData(db, projects)

  /** The tasks domain sets `taskStats.source`, milestone summaries and status deletion read through it. */
  lazy val taskStats = new TaskStatsPreviewData

  lazy val milestones = new MilestonesPreviewData(db, projects)

  lazy val ai = new AiPreviewData(db, projects)

  lazy val dataSync = new DataSyncPreviewData(db, projects)

  lazy val tasks = new TasksPreviewData(db, projects, taskConfig, milestones, accounts, taskStats)

  private[preview] lazy val seeders: List[PreviewSeeder] = List(
    new AccountsPreviewSeed(accounts),
    new OrganizationsPreviewSeed(organizations, projects),
    new ProjectLabelsPreviewSeed(projectLabels, projects),
    new TaskConfigPreviewSeed(taskConfig, clock),
    new MilestonesPreviewSeed(milestones, projects, clock),
    new AiPreviewSeed(ai, clock),
    new TasksPreviewSeed(tasks, projects, clock),
    new DataSyncPreviewSeed(dataSync, clock)
  )

  /** Holders register project creation/deletion hooks in their constructors, so none may be created lazily later. */
  private val hookRegistrations: List[AnyRef] = List(taskConfig, milestones, ai, tasks, dataSync)

  /** Creating every holder registers every collection name in the db, which the reset needs. */
  private lazy val allHolders: List[AnyRef] =
    hookRegistrations ++ List(accounts, organizations, projects, projectLabels, taskStats)
}

trait PreviewSeeder {
  def seed(): Unit
}
