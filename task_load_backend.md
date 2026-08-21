# Task Load feature

## Context

Togger already has a "task dump" feature (`b-05-disk-sync`, wired through `c-03-http-endpoints`,
`b-06-delivery-http4s`, `b-07-main`) that writes every task of a project (task + its versions, links + link
versions, log entries + log entry versions) to `<dumpPath>/<projectName>_<projectId>/<taskId>.json`, plus a
`project.json` manifest. There's also a `checkStatus` endpoint that reads those same files back off disk and
diffs them against the DB (`InSync`/`OutOfSync`/`MissingFromDump`/`MissingFromDb`/`Malformed`), but it never
writes anything — it's read-only reporting.

We need the true reverse of dump: a **task load** feature that actually reads `<taskId>.json` files from a
disk location and imports them into the database, so a project's task history can be restored/repopulated
from a dump. This is the write-back counterpart the dump/status pair doesn't provide today.

Decisions already made with the user:
- **Import into DB** (not just a read-only API response).
- The dump path is passed **explicitly in the request** (not looked up via `TaskDumpConfig`, since that's
  keyed per-account and the request already carries project + acting user).
- For a task whose ID **already exists** in the DB, **merge new versions in**: insert any task/link/logEntry
  version rows (and any whole link/logEntry) from disk that aren't already present by ID, without touching or
  overwriting existing root rows (`task`/`link`/`log_entry` have no mutable content columns — only their
  `*_version` tables do).
- **Per-task transactions**: each task is imported in its own `.transact(transactor)` call so one bad/corrupt
  task file doesn't block the rest of the batch.
- **Backend only** — no frontend UI in this pass (mirrors how `checkStatus`/`dumpProject` were built first,
  UI wiring can follow later).

## Key finding: repo `insert` methods don't accept the primary key

`TaskRepo.insert`, `TaskVersionRepo.insert`, `LinkRepo.insert`, `LogEntryRepo.insert` (and their version
counterparts) never include `id`/`version` in their `INSERT` column list — they always rely on the column's
`uuidv7()` DB default (see `b-05-database/.../TaskRepoImpl.scala`, `TaskVersionRepoImpl.scala`, and the
`uuidv7()` defaults in `b-05-database/src/main/resources/liquibase/changelog.yaml`). Since the columns are
plain `uuid` with no trigger enforcing the default, an explicit `INSERT ... (id, ...) VALUES ($id, ...)` works
fine at the DB level — the gap is purely in the Scala repo layer. **Load needs new `insertWithId`-style repo
methods** on the six repos below so a disk-dumped task can be re-inserted with its *original* IDs (required
for idempotency — re-running load must recognize what it already imported — and for foreign key consistency
across task/version/link/logEntry).

## New repo methods (additive, existing `insert` methods untouched)

In each of these trait + impl pairs, add a method that explicitly includes the PK column in the insert
statement, built the same way the existing `insert` is (`insertStatement(TABLE_FRAG)(...)`,
`.withUniqueGeneratedKeys[...]`), just with the id/version column added to both the column list and the
`values(...)` fragment:

- `b-05-core/.../task/TaskRepo.scala` + `b-05-database/.../task/TaskRepoImpl.scala`: `insertWithId(task: Task)`
- `TaskVersionRepo`/`TaskVersionRepoImpl` (same file pair): `insertWithId(taskVersion: TaskVersion)`
- `b-05-core/.../task/LinkRepo.scala` + `LinkRepoImpl.scala`: `insertWithId(link: Link)`
- `b-05-core/.../task/LinkVersionRepo.scala` + `LinkVersionRepoImpl.scala`: `insertWithId(linkVersion: LinkVersion)`
- `b-05-core/.../logentry/LogEntryRepo.scala` + `LogEntryRepoImpl.scala`: `insertWithId(logEntry: LogEntry)`
- `b-05-core/.../logentry/LogEntryVersionRepo.scala` + `LogEntryVersionRepoImpl.scala`: `insertWithId(logEntryVersion: LogEntryVersion)`

## New module code (package `com.togger.dump` in `b-05-disk-sync`, alongside the dump feature)

**`TaskLoadModels.scala`**
```scala
sealed trait TaskLoadTaskStatus
object TaskLoadTaskStatus {
  case object Inserted extends TaskLoadTaskStatus   // task didn't exist in DB, fully imported
  case object Merged extends TaskLoadTaskStatus     // task existed, at least one new version/link/logEntry added
  case object Unchanged extends TaskLoadTaskStatus  // task existed, nothing new found on disk
  case object Malformed extends TaskLoadTaskStatus  // JSON failed to parse (from readDumpedTasks' Left)
  case object Failed extends TaskLoadTaskStatus     // DB error while importing this task
}

case class TaskLoadTaskResult(
  taskId: UUID,
  taskName: Option[String],
  status: TaskLoadTaskStatus,
  versionsAdded: Int,
  linksAdded: Int,
  logEntriesAdded: Int,
  error: Option[String]
)

case class TaskLoadResult(dumpPath: String, taskCount: Int, taskResults: List[TaskLoadTaskResult])
```

**`TaskLoadRepo.scala`** — the write/merge side, mirrors `TaskDumpRepo`'s constructor shape but for imports:
```scala
class TaskLoadRepo(
  taskRepo: TaskRepo,
  taskVersionRepo: TaskVersionRepo,
  linkRepo: LinkRepo,
  linkVersionRepo: LinkVersionRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo
) {
  def mergeTask(diskData: TaskDumpData): ConnectionIO[TaskLoadTaskResult] = ...
}
```
Algorithm for `mergeTask`, all inside one `ConnectionIO` (so the per-task `.transact` call is atomic per task):
1. `taskRepo.findById(diskData.task.id).attempt.map(_.toOption)` → `existingTask: Option[Task]`.
2. If `None`: `taskRepo.insertWithId(diskData.task)`, then insert every element of `diskData.taskVersions` via
   `taskVersionRepo.insertWithId`, every link via `linkRepo.insertWithId` + its versions via
   `linkVersionRepo.insertWithId`, every log entry via `logEntryRepo.insertWithId` + its versions via
   `logEntryVersionRepo.insertWithId`. Everything counts as "added". Status = `Inserted`.
3. If `Some(_)` (task already present): for each child collection, only insert rows whose ID is missing:
   - Task versions: for each `TaskVersion` in `diskData.taskVersions`, check
     `taskVersionRepo.findByVersion(v.version).attempt` — insert via `insertWithId` only if not found.
   - Links: for each `LinkDumpData`, check `linkRepo.findById(link.id).attempt`; if missing, `insertWithId`
     the link itself, then insert **all** its versions (all are new since the link is new). If the link
     already exists, only insert its versions that are missing (same `findByVersion` check as task versions).
   - Log entries: same pattern as links, via `logEntryRepo`/`logEntryVersionRepo`.
   - Tally `versionsAdded`/`linksAdded`/`logEntriesAdded`; status = `Merged` if any tally > 0, else `Unchanged`.
4. `taskName` = `diskData.taskVersions.headOption.map(_.name)` (versions are stored/returned newest-first, same
   convention `TaskDumpService.buildPathStatus` already uses).

This reuses `TaskDumpDiskWriter.readDumpedTasks(basePath, project)` as-is for reading+parsing the files (no
changes needed there) — it already returns `Map[UUID, Either[String, TaskDumpData]]`, where the `Left` case
becomes a `Malformed` result directly, no DB work attempted for it.

**`TaskLoadService.scala`** — orchestration, mirrors `TaskDumpService.checkStatus`'s shape:
```scala
class TaskLoadService(
  taskDumpRepo: TaskDumpRepo,          // reused as-is: isAssigned + getProjectAndTaskStatuses (for the Project)
  taskLoadRepo: TaskLoadRepo,
  taskDumpDiskWriter: TaskDumpDiskWriter, // reused as-is: readDumpedTasks
  transactor: Transactor[IO]
) extends TgLogging {

  def loadProject(projectId: UUID, accountId: UUID, dumpPath: String): IO[Either[ProjectError, TaskLoadResult]] =
    taskDumpRepo.isAssigned(projectId, accountId).flatMap { assigned =>
      if (!assigned) (NotAssignedToProject: ProjectError).asLeft[Project].pure[ConnectionIO]
      else taskDumpRepo.getProjectAndTaskStatuses(projectId).map { case (project, _) => project.asRight[ProjectError] }
    }.transact(transactor).flatMap {
      case Left(error) => IO.pure(Left(error))
      case Right(project) =>
        val diskTasksById = taskDumpDiskWriter.readDumpedTasks(dumpPath, project)
        diskTasksById.toList.traverse {
          case (taskId, Left(parseError)) =>
            IO.pure(TaskLoadTaskResult(taskId, None, TaskLoadTaskStatus.Malformed, 0, 0, 0, Some(parseError)))
          case (taskId, Right(diskData)) =>
            taskLoadRepo.mergeTask(diskData).transact(transactor).attempt.map {
              case Right(result) => result
              case Left(e) =>
                TaskLoadTaskResult(taskId, diskData.taskVersions.headOption.map(_.name),
                  TaskLoadTaskStatus.Failed, 0, 0, 0, Some(e.getMessage))
            }
        }.map(results => Right(TaskLoadResult(dumpPath, results.size, results)))
    }
}
```
No new `ProjectError` case is needed — `NotAssignedToProject` already covers the authorization failure. (If
`dumpPath` doesn't exist on disk, `readDumpedTasks`/`DiskOps.listFiles` already returns an empty list rather
than throwing — the result is simply zero tasks, same behavior `checkStatus` has today.)

## Shared endpoint + DTOs

**`c-01-common/shared/src/main/scala/com/togger/dto/TaskLoadResp.scala`** (new file, same shape/style as
`TaskDumpStatusResp.scala`):
```scala
case class TaskLoadReq(dumpPath: String)

sealed trait TaskLoadStatusResp extends EnumEntry
object TaskLoadStatusResp extends Enum[TaskLoadStatusResp] {
  override def values = findValues
  case object Inserted extends TaskLoadStatusResp
  case object Merged extends TaskLoadStatusResp
  case object Unchanged extends TaskLoadStatusResp
  case object Malformed extends TaskLoadStatusResp
  case object Failed extends TaskLoadStatusResp
}

case class TaskLoadTaskResultResp(
  taskId: UUID, taskName: Option[String], status: TaskLoadStatusResp,
  versionsAdded: Int, linksAdded: Int, logEntriesAdded: Int, error: Option[String]
)

case class TaskLoadResp(projectId: UUID, dumpPath: String, taskCount: Int, results: List[TaskLoadTaskResultResp])
```

**`c-03-http-endpoints/shared/.../dump/TaskDumpEndpoints.scala`** — add a third endpoint alongside
`dumpEndpoint`/`checkStatusEndpoint`, same `basePath`, combining a path param with a JSON body (same
tuple-input pattern `TaskDumpConfigEndpoints` doesn't need but Tapir supports natively via chained `.in(...)`):
```scala
val loadEndpoint: TgSecuredEndpoint[(UUID, TaskLoadReq), TaskLoadResp] =
  securedEndpoint.endpoint.post
    .in(basePath / "load" / path[UUID]("project-id"))
    .in(jsonBody[TaskLoadReq])
    .out(jsonBody[TgJsonSuccess[TaskLoadResp]])
```
→ `POST /project/dump/load/{project-id}` with body `{"dumpPath": "..."}`.

**`b-06-delivery-http4s/.../controllers/project/TaskDumpController.scala`** — add a `loadTasks` server-logic
method next to `dumpTasks`/`checkStatus`, following the identical `serverLogic { auth => { case (projectId, req) => ... } }`
shape, mapping `TaskLoadResult`/`TaskLoadTaskResult` → `TaskLoadResp`/`TaskLoadTaskResultResp` (straight status
enum mapping like `toRespStatus` already does for dump).

## Wiring (`b-07-main/src/main/scala/com/togger/TgServer.scala`)

Add, near the existing `taskDump*` `lazy val`s (~line 149-174):
```scala
lazy val taskLoadRepo = new TaskLoadRepo(taskRepo, taskVersionRepo, linkRepo, linkVersionRepo, logEntryRepo, logEntryVersionRepo)
lazy val taskLoadService = new TaskLoadService(taskDumpRepo, taskLoadRepo, taskDumpDiskWriter, transactor)
```
(All six repo `lazy val`s already exist, reused from `taskDumpRepo`'s own construction.) Add `taskLoadService`
to `TaskDumpController`'s constructor args, and register the new `loadTasks` server endpoint alongside
`dumpTasks`/`checkStatus` in the endpoint list passed to `HttpModule.createServer`.

## Tests

Follow `b-07-main/src/test/scala/com/togger/dump/TaskDumpStatusServiceTest.scala`'s structure (extends
`BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus`, uses a real Postgres transactor, builds
dump files on disk via a temp `os.Path`, uses the existing test fixtures for account/project/task creation).
New `TaskLoadServiceTest` covering:
- Not assigned → `Left(NotAssignedToProject)`.
- Dump path with a task JSON whose ID isn't in the DB → task/versions/links/logEntries all inserted, status
  `Inserted`, counts match the dumped data.
- Dump path with a task ID that already exists but has extra versions/links/logEntries in the disk file →
  only the missing rows get inserted, status `Merged`, existing rows untouched.
- Dump path with a task ID that already exists and disk file has nothing new → status `Unchanged`, no writes.
- Malformed JSON file → status `Malformed`, no DB error propagated, rest of the batch still processes.
- Running load twice on the same dump path is idempotent (second run reports `Unchanged` for everything).

Run via `sbt "backendMain/testOnly com.togger.dump.TaskLoadServiceTest"` (Postgres must be running — `togger-test`
DB per `CLAUDE.md`). Also add/extend repo-level tests for the new `insertWithId` methods
(`b-05-database/src/test/scala/com/togger/task/TaskRepoTest.scala` etc.) verifying the caller-supplied ID is
persisted as given at least once.

## Explicitly out of scope

- No frontend (`f-core`) changes — no `TaskLoadFeService`, no UI button/component.
- No `TaskDumpConfig` lookup for the load path — path comes from the request body only.
- No deletion/reconciliation of DB rows absent from disk — load is strictly additive.
- No changes to the existing `dumpProject`/`checkStatus` behavior or their existing `insert` repo methods.
