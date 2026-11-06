package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.account.AccountRepoImpl
import com.togger.account.TgAccount
import com.togger.accountmapping.AccountMappingRepoImpl
import com.togger.common.BusinessTgDbSpec
import com.togger.common.TgClockImpl
import com.togger.common.TimeHelpers
import com.togger.dataexportconfig.DataExportConfig
import com.togger.dataexportconfig.DataExportConfigRepoImpl
import com.togger.dataexportconfig.DataExportConfigService
import com.togger.deletedentity.DeletedEntityRepoImpl
import com.togger.dump.DayOfWeekCodec._
import com.togger.logentry._
import com.togger.milestone._
import com.togger.milestonesteptemplate.MilestoneStepTemplate
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepoImpl
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersion
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersionRepoImpl
import com.togger.milestonetemplate.MilestoneTemplate
import com.togger.milestonetemplate.MilestoneTemplateRepoImpl
import com.togger.milestonetemplate.MilestoneTemplateVersion
import com.togger.milestonetemplate.MilestoneTemplateVersionRepoImpl
import com.togger.organization.TgProject
import com.togger.organization.TgTaskStatus
import com.togger.project.Project
import com.togger.project.ProjectAccount
import com.togger.project.ProjectAccountRepoImpl
import com.togger.project.ProjectLabelAssignmentRepoImpl
import com.togger.projectlabel.ProjectLabelCategoryRepoImpl
import com.togger.projectlabel.ProjectLabelRepoImpl
import com.togger.projectmapping.ProjectMappingRepoImpl
import com.togger.task._
import com.togger.taskStatus.TaskStatusRepoImpl
import com.togger.tasktemplate.TaskTemplateRepoImpl
import com.togger.tasktemplate.TaskTemplateVersionRepoImpl
import com.togger.tgprompttemplate.TgPromptTemplateRepoImpl
import com.togger.tgprompttemplate.TgPromptTemplateVersionRepoImpl
import com.togger.timebox.TimeboxRepoImpl
import com.togger.timebox.TimeboxVersionRepoImpl
import doobie.ConnectionIO
import doobie.Transactor
import doobie.implicits._
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

import java.time.LocalDate
import java.util.UUID

trait ExportImportTestSupport
    extends BusinessTgDbSpec
    with TgAccount
    with TgProject
    with TgTaskStatus
    with TimeHelpers {

  val projectAccountRepo = new ProjectAccountRepoImpl
  val accountRepo = new AccountRepoImpl
  val taskRepo = new TaskRepoImpl
  val taskVersionRepo = new TaskVersionRepoImpl
  val taskLabelRepo = new TaskLabelRepoImpl
  val taskLabelAssignmentRepo = new TaskLabelAssignmentRepoImpl
  val linkRepo = new LinkRepoImpl
  val linkVersionRepo = new LinkVersionRepoImpl
  val logEntryRepo = new LogEntryRepoImpl
  val logEntryVersionRepo = new LogEntryVersionRepoImpl
  val milestoneRepo = new MilestoneRepoImpl
  val milestoneVersionRepo = new MilestoneVersionRepoImpl
  val milestoneDateRepo = new MilestoneDateRepoImpl
  val milestoneDateVersionRepo = new MilestoneDateVersionRepoImpl
  val milestoneSummaryRepo = new MilestoneSummaryRepoImpl
  val milestoneSummaryVersionRepo = new MilestoneSummaryVersionRepoImpl
  val milestoneStepRepo = new MilestoneStepRepoImpl
  val milestoneStepVersionRepo = new MilestoneStepVersionRepoImpl
  val milestoneStepTemplateRepo = new MilestoneStepTemplateRepoImpl
  val milestoneStepTemplateVersionRepo = new MilestoneStepTemplateVersionRepoImpl
  val milestoneTemplateVersionRepo = new MilestoneTemplateVersionRepoImpl
  val milestoneTemplateRepo = new MilestoneTemplateRepoImpl(milestoneTemplateVersionRepo)
  val taskTemplateRepo = new TaskTemplateRepoImpl
  val taskTemplateVersionRepo = new TaskTemplateVersionRepoImpl
  val timeboxRepo = new TimeboxRepoImpl
  val timeboxVersionRepo = new TimeboxVersionRepoImpl
  val tgPromptTemplateRepo = new TgPromptTemplateRepoImpl
  val tgPromptTemplateVersionRepo = new TgPromptTemplateVersionRepoImpl
  val projectLabelRepo = new ProjectLabelRepoImpl
  val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl
  val projectLabelAssignmentRepo = new ProjectLabelAssignmentRepoImpl
  val projectMappingRepo = new ProjectMappingRepoImpl
  val deletedEntityRepo = new DeletedEntityRepoImpl
  val accountMappingRepo = new AccountMappingRepoImpl
  val dataExportConfigRepo = new DataExportConfigRepoImpl

  val diskOps = new DiskOps
  val treeStore = new DataExportTreeStore(diskOps)

  val dataExportRepo = new DataExportRepo(
    projectRepo,
    projectAccountRepo,
    projectLabelAssignmentRepo,
    projectLabelCategoryRepo,
    accountRepo,
    taskRepo,
    taskVersionRepo,
    taskStatusRepo,
    taskLabelRepo,
    taskLabelAssignmentRepo,
    linkRepo,
    linkVersionRepo,
    logEntryRepo,
    logEntryVersionRepo,
    milestoneRepo,
    milestoneVersionRepo,
    milestoneDateRepo,
    milestoneDateVersionRepo,
    milestoneSummaryRepo,
    milestoneSummaryVersionRepo,
    milestoneStepRepo,
    milestoneStepVersionRepo,
    milestoneStepTemplateRepo,
    milestoneStepTemplateVersionRepo,
    milestoneTemplateRepo,
    milestoneTemplateVersionRepo,
    taskTemplateRepo,
    taskTemplateVersionRepo,
    timeboxRepo,
    timeboxVersionRepo,
    tgPromptTemplateRepo,
    tgPromptTemplateVersionRepo,
    deletedEntityRepo
  )

  val dataImportRepo = new DataImportRepo(
    taskRepo,
    taskVersionRepo,
    linkRepo,
    linkVersionRepo,
    logEntryRepo,
    logEntryVersionRepo,
    projectMappingRepo,
    taskStatusRepo,
    new TgClockImpl,
    accountRepo,
    accountTypeRepo,
    accountMappingRepo,
    taskLabelRepo,
    taskLabelAssignmentRepo,
    projectLabelRepo,
    projectLabelCategoryRepo,
    projectLabelAssignmentRepo,
    projectAccountRepo,
    milestoneRepo,
    milestoneVersionRepo,
    milestoneDateRepo,
    milestoneDateVersionRepo,
    milestoneSummaryRepo,
    milestoneSummaryVersionRepo,
    milestoneStepRepo,
    milestoneStepVersionRepo,
    milestoneStepTemplateRepo,
    milestoneStepTemplateVersionRepo,
    milestoneTemplateRepo,
    milestoneTemplateVersionRepo,
    taskTemplateRepo,
    taskTemplateVersionRepo,
    timeboxRepo,
    timeboxVersionRepo,
    tgPromptTemplateRepo,
    tgPromptTemplateVersionRepo,
    projectRepo,
    deletedEntityRepo
  )

  def newTaskService(transactor: Transactor[IO]) =
    new TaskService(
      taskRepo,
      taskVersionRepo,
      linkRepo,
      linkVersionRepo,
      logEntryRepo,
      logEntryVersionRepo,
      taskLabelAssignmentRepo,
      deletedEntityRepo,
      new TgClockImpl,
      transactor
    )

  def newLogEntryService(transactor: Transactor[IO]) =
    new LogEntryService(logEntryRepo, logEntryVersionRepo, taskRepo, deletedEntityRepo, new TgClockImpl, transactor)

  def newExportService(transactor: Transactor[IO]) =
    new DataExportService(
      dataExportRepo,
      treeStore,
      new DataExportConfigService(dataExportConfigRepo, new TgClockImpl, transactor),
      new TgClockImpl,
      transactor
    )

  def newImportService(transactor: Transactor[IO]) =
    new DataImportService(dataExportRepo, dataImportRepo, treeStore, transactor)

  def newExportPath(): os.Path = os.pwd / "target" / s"test-data-export-${UUID.randomUUID()}"

  def insertExportConfig(projectId: UUID, accountId: UUID, exportPath: os.Path): ConnectionIO[DataExportConfig] =
    dataExportConfigRepo.insert(
      DataExportConfig(
        id = UUID.randomUUID(),
        projectId = projectId,
        accountId = accountId,
        name = "Export config",
        exportPath = exportPath.toString,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  /** An account assigned to a new project with an export config pointing at `exportPath`. */
  def createExportableProject(exportPath: os.Path) =
    for {
      account <- createAccount
      orgAndProject <- createOrgAndProject
      (_, savedProject) = orgAndProject
      _ <- projectAccountRepo.insert(ProjectAccount(savedProject.id, account.id, nowAsInstant))
      _ <- insertExportConfig(savedProject.id, account.id, exportPath)
    } yield (account, savedProject)

  def createTaskWithFullHistory(
    projectId: UUID,
    createdBy: UUID,
    taskStatusId: UUID,
    milestoneId: Option[UUID] = None
  ): ConnectionIO[UUID] =
    for {
      // the task is in the first step of the milestone, as a task belongs to a step
      stepId <- milestoneId.traverse(id =>
        milestoneStepVersionRepo.findLatestByMilestoneId(id).map(_.minBy(_.position).milestoneStepId)
      )
      savedTask <- taskRepo.insert(
        Task(id = UUID.randomUUID(), projectId = projectId, createdBy = createdBy, createdAt = nowAsInstant)
      )
      firstVersion <- taskVersionRepo.insert(
        TaskVersion(
          version = UUID.randomUUID(),
          taskId = savedTask.id,
          name = "Task name",
          description = "Task description",
          assignedTo = None,
          closedBy = None,
          taskStatusId = taskStatusId,
          milestoneStepId = stepId,
          startDate = LocalDate.now(),
          dueDate = LocalDate.now().plusDays(7),
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      _ <- taskVersionRepo.insert(firstVersion.copy(name = "Task name v2", description = "Task description v2"))
      savedLink <- linkRepo.insert(Link(id = UUID.randomUUID(), taskId = savedTask.id, createdAt = nowAsInstant))
      firstLinkVersion <- linkVersionRepo.insert(
        LinkVersion(
          version = UUID.randomUUID(),
          linkId = savedLink.id,
          name = "Link",
          url = "https://example.com",
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      _ <- linkVersionRepo.insert(firstLinkVersion.copy(name = "Link v2"))
      savedLogEntry <- logEntryRepo.insert(
        LogEntry(id = UUID.randomUUID(), taskId = savedTask.id, createdBy = createdBy, createdAt = nowAsInstant)
      )
      firstLogEntryVersion <- logEntryVersionRepo.insert(
        LogEntryVersion(
          version = UUID.randomUUID(),
          logEntryId = savedLogEntry.id,
          text = "external",
          status = LogEntryStatus.InProgress,
          createdBy = createdBy,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      _ <- logEntryVersionRepo.insert(firstLogEntryVersion.copy(text = "external v2"))
    } yield savedTask.id

  def createTaskLabel(projectId: UUID, name: String = "Label"): ConnectionIO[TaskLabel] =
    taskLabelRepo.insert(
      TaskLabel(UUID.randomUUID(), projectId, name, "Label description", "🏷️", nowAsInstant, nowAsInstant)
    )

  def createMilestoneWithDate(projectId: UUID): ConnectionIO[UUID] =
    for {
      milestone <- milestoneRepo.insert(Milestone(UUID.randomUUID(), projectId, nowAsInstant))
      _ <- milestoneVersionRepo.insert(
        MilestoneVersion(
          version = UUID.randomUUID(),
          milestoneId = milestone.id,
          name = "Milestone",
          description = "Milestone description",
          startDate = LocalDate.now(),
          dueDate = LocalDate.now().plusDays(30),
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      date <- milestoneDateRepo.insert(MilestoneDate(UUID.randomUUID(), milestone.id, nowAsInstant))
      _ <- milestoneDateVersionRepo.insert(
        MilestoneDateVersion(
          version = UUID.randomUUID(),
          milestoneDateId = date.id,
          name = "Milestone date",
          description = "Date description",
          date = nowAsInstant,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      summary <- milestoneSummaryRepo.insert(MilestoneSummary(UUID.randomUUID(), milestone.id, nowAsInstant))
      _ <- milestoneSummaryVersionRepo.insert(
        MilestoneSummaryVersion(
          version = UUID.randomUUID(),
          milestoneSummaryId = summary.id,
          summary = "Milestone summary",
          startDate = LocalDate.of(2026, 1, 5),
          endDate = LocalDate.of(2026, 1, 9),
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      step <- milestoneStepRepo.insert(MilestoneStep(UUID.randomUUID(), milestone.id, nowAsInstant))
      firstStepVersion <- milestoneStepVersionRepo.insert(
        MilestoneStepVersion(
          version = UUID.randomUUID(),
          milestoneStepId = step.id,
          name = "Milestone step",
          description = "Step description",
          status = MilestoneStepStatus.Planned,
          position = 0,
          startDate = LocalDate.now(),
          dueDate = LocalDate.now().plusDays(10),
          endDate = None,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant,
          changeComment = ""
        )
      )
      _ <- milestoneStepVersionRepo.insert(
        firstStepVersion.copy(status = MilestoneStepStatus.InProgress, changeComment = "Started")
      )
    } yield milestone.id

  /** A step of the milestone at the given position (stage); steps at the same position run in parallel. */
  def createMilestoneStepAt(milestoneId: UUID, name: String, position: Int): ConnectionIO[UUID] =
    for {
      step <- milestoneStepRepo.insert(MilestoneStep(UUID.randomUUID(), milestoneId, nowAsInstant))
      _ <- milestoneStepVersionRepo.insert(
        MilestoneStepVersion(
          version = UUID.randomUUID(),
          milestoneStepId = step.id,
          name = name,
          description = "",
          status = MilestoneStepStatus.Planned,
          position = position,
          startDate = LocalDate.now(),
          dueDate = LocalDate.now().plusDays(10),
          endDate = None,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant,
          changeComment = ""
        )
      )
    } yield step.id

  /** A step template with its first version. */
  def createMilestoneStepTemplate(
    projectId: UUID,
    createdBy: UUID,
    name: String,
    description: String
  ): ConnectionIO[UUID] =
    for {
      template <- milestoneStepTemplateRepo.insert(
        MilestoneStepTemplate(UUID.randomUUID(), projectId, createdBy, nowAsInstant)
      )
      _ <- milestoneStepTemplateVersionRepo.insert(
        MilestoneStepTemplateVersion(UUID.randomUUID(), template.id, name, description, nowAsInstant)
      )
    } yield template.id

  /** A milestone template with its first version and the stages of its steps; the steps of a stage run in parallel. */
  def createMilestoneTemplate(
    projectId: UUID,
    createdBy: UUID,
    name: String,
    description: String,
    stages: List[List[UUID]]
  ): ConnectionIO[UUID] =
    for {
      template <- milestoneTemplateRepo.insert(MilestoneTemplate(UUID.randomUUID(), projectId, createdBy, nowAsInstant))
      version <- milestoneTemplateVersionRepo.insert(
        MilestoneTemplateVersion(UUID.randomUUID(), template.id, name, description, nowAsInstant)
      )
      _ <- milestoneTemplateVersionRepo.insertSteps(version.version, stages)
    } yield template.id

  /** Removes everything an import restores, keeping accounts, the project and its memberships. */
  def deleteProjectContent: ConnectionIO[Unit] =
    List(
      DeletedEntityRepoImpl.DELETED_ENTITY_TABLE,
      TimeboxVersionRepoImpl.TIMEBOX_VERSION_TASK_TABLE,
      TimeboxVersionRepoImpl.TIMEBOX_VERSION_TABLE,
      TimeboxRepoImpl.TIMEBOX_TABLE,
      LogEntryVersionRepoImpl.LOG_ENTRY_VERSION_TABLE,
      LogEntryRepoImpl.LOG_ENTRY_TABLE,
      LinkVersionRepoImpl.LINK_VERSION_TABLE,
      LinkRepoImpl.LINK_TABLE,
      TaskLabelAssignmentRepoImpl.TASK_LABEL_ASSIGNMENT_TABLE,
      TaskVersionRepoImpl.TASK_VERSION_TABLE,
      TaskRepoImpl.TASK_TABLE,
      MilestoneTemplateVersionRepoImpl.MILESTONE_TEMPLATE_VERSION_STEP_TABLE,
      MilestoneTemplateVersionRepoImpl.MILESTONE_TEMPLATE_VERSION_TABLE,
      MilestoneTemplateRepoImpl.MILESTONE_TEMPLATE_TABLE,
      MilestoneStepTemplateVersionRepoImpl.MILESTONE_STEP_TEMPLATE_VERSION_TABLE,
      MilestoneStepTemplateRepoImpl.MILESTONE_STEP_TEMPLATE_TABLE,
      MilestoneStepVersionRepoImpl.MILESTONE_STEP_VERSION_TABLE,
      MilestoneStepRepoImpl.MILESTONE_STEP_TABLE,
      MilestoneSummaryVersionRepoImpl.MILESTONE_SUMMARY_VERSION_TABLE,
      MilestoneSummaryRepoImpl.MILESTONE_SUMMARY_TABLE,
      MilestoneDateVersionRepoImpl.MILESTONE_DATE_VERSION_TABLE,
      MilestoneDateRepoImpl.MILESTONE_DATE_TABLE,
      MilestoneVersionRepoImpl.MILESTONE_VERSION_TABLE,
      MilestoneRepoImpl.MILESTONE_TABLE,
      TaskTemplateVersionRepoImpl.TASK_TEMPLATE_VERSION_TABLE,
      TaskTemplateRepoImpl.TASK_TEMPLATE_TABLE,
      TgPromptTemplateVersionRepoImpl.TG_PROMPT_TEMPLATE_VERSION_TABLE,
      TgPromptTemplateRepoImpl.TG_PROMPT_TEMPLATE_TABLE,
      TaskLabelRepoImpl.TASK_LABEL_TABLE,
      TaskStatusRepoImpl.TASK_STATUS_TABLE
    ).traverse_(table => doobie.Fragment.const(s"delete from $table").update.run.void)

  def createSecondProject(organizationId: UUID): ConnectionIO[Project] =
    projectRepo.insert(project.copy(id = UUID.randomUUID(), organizationId = organizationId, name = "Second project"))

  def exportToFolder(transactor: Transactor[IO], projectId: UUID, accountId: UUID): IO[os.Path] =
    newExportService(transactor)
      .exportProject(projectId, accountId)
      .map(result => os.Path(result.toOption.get.pathResults.head.exportPath))

  def readIndex(root: os.Path): ExportIndex = treeStore.readIndexAtPath(root.toString).toOption.get

  /** Rewrites `index.json`, keeping its root hash consistent with the entries. */
  def editIndex(root: os.Path)(change: ExportIndex => ExportIndex): Unit = {
    val updated = change(readIndex(root))
    val withRootHash = updated.copy(rootHash = ExportHasher.rootHash(updated.entities, updated.deleted))
    os.write.over(root / "index.json", withRootHash.asJson.spaces2)
  }

  /** Writes an entity file by hand and records its hash in the index, as an export would have. */
  def putEntity[A: Encoder](
    root: os.Path,
    entityType: DataExportEntityType,
    id: UUID,
    name: Option[String],
    data: A
  ): Unit = {
    val json = data.asJson
    val path = if (entityType.singleton) root / s"${entityType.folder}.json" else root / entityType.folder / s"$id.json"
    os.write.over(path, json.spaces2, createFolders = true)
    editIndex(root) { index =>
      val entries = index.entities.getOrElse(entityType.folder, Map.empty) +
        (id.toString -> ExportIndexEntry(ExportHasher.hashJson(json), name, ExportHasher.latestTimestamp(json)))
      index.copy(entities = index.entities + (entityType.folder -> entries))
    }
  }

  def editMembers(root: os.Path, change: List[UUID] => List[UUID]): Unit = {
    val current = decode[MembersDumpData](os.read(root / "members.json")).toOption.get
    val id = readIndex(root).entriesOf(DataExportEntityType.Members).keys.head
    putEntity(root, DataExportEntityType.Members, id, None, MembersDumpData(change(current.accountIds)))
  }

  def editProjectDump(root: os.Path)(change: ProjectDumpData => ProjectDumpData): Unit = {
    val current = decode[ProjectDumpData](os.read(root / "project.json")).toOption.get
    putEntity(root, DataExportEntityType.Project, current.project.id, Some(current.project.name), change(current))
  }

  def handWrittenTask(
    taskId: UUID,
    projectId: UUID,
    createdBy: UUID,
    taskStatusId: UUID,
    assignedTo: Option[UUID] = None,
    closedBy: Option[UUID] = None
  ): TaskDumpData =
    TaskDumpData(
      task = Task(id = taskId, projectId = projectId, createdBy = createdBy, createdAt = nowAsInstant),
      taskVersions = List(
        TaskVersion(
          version = UUID.randomUUID(),
          taskId = taskId,
          name = "Disk task",
          description = "Disk task description",
          assignedTo = assignedTo,
          closedBy = closedBy,
          taskStatusId = taskStatusId,
          startDate = LocalDate.now(),
          dueDate = LocalDate.now().plusDays(7),
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      ),
      links = Nil,
      labelIds = Nil
    )

  def handWrittenLogEntry(logEntryId: UUID, taskId: UUID, createdBy: UUID): LogEntryDumpData =
    LogEntryDumpData(
      logEntry = LogEntry(id = logEntryId, taskId = taskId, createdBy = createdBy, createdAt = nowAsInstant),
      versions = List(
        LogEntryVersion(
          version = UUID.randomUUID(),
          logEntryId = logEntryId,
          text = "external",
          status = LogEntryStatus.InProgress,
          createdBy = createdBy,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
    )

}
