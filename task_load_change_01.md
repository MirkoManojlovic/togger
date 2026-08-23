# Task Load Change 01: Project Mapping + Task Status Reconciliation

## Context

The task-load feature (`task_load_backend.md`, implemented in `b-05-disk-sync/.../TaskLoadService.scala` +
`TaskLoadRepo.scala`, wired through `TaskDumpEndpoints.loadEndpoint` / `TaskDumpController.loadTasks`, and now
exposed in the frontend via `TaskLoadComponent` + preconfigured paths in `TaskLoadConfig`/`TaskLoadConfigFeService`)
currently assumes the dump being loaded was produced **from the same project row** it's being loaded into: the
`{project-id}` path param is used both to authorize the request (`isAssigned`) and to compute the on-disk folder
name via `projectFolderName(project) = "<sanitizedName>_<project.id>"` (`TaskDumpDiskWriter.scala:43-46`), which
is then appended to the request's `dumpPath` before listing/parsing files.

That assumption breaks for the real use case this change targets: loading a dump that was produced by a
**different** Togger install/database, where the project id embedded in the dump (`project.json`'s
`project.id`, produced by `TaskDumpService.dumpProject` via `ProjectDumpManifest`) does not exist as a row in
this database at all. The existing `project_mapping` feature
(`b-05-core/.../projectmapping/ProjectMapping.scala`, table `project_mapping`, unique on `project_id_db`) already
models "this dump-side project id and this DB-side project id are the same logical project" but is not wired
into the load flow anywhere today (confirmed: no reference to `com.togger.projectmapping` from any of
`TaskLoadService`/`TaskLoadRepo`/`TaskDumpService`/`TaskDumpRepo`/`TaskDumpController`).

Separately, `TaskVersion.taskStatusId` (`b-05-core/.../task/Task.scala:17`) is a hard `UUID` FK into this
project's own `task_status` table. A dumped task version's `taskStatusId` refers to a `TaskStatus` row id that
only exists in the *origin* database. `ProjectDumpManifest.taskStatuses: List[TaskStatus]`
(`TaskDumpModels.scala:21-26`) is written into `project.json` at dump time precisely so the load side has the
full origin task status catalogue available, but nothing reads it back today (`readDumpedTasks` only reads the
per-task `<taskId>.json` files, never `project.json`).

This document plans the two closely-related fixes requested:
1. Before loading, resolve the dump's project id to a DB project id via `project_mapping`; if no mapping exists,
   stop and report enough information for the caller to create one (rather than silently failing or guessing).
2. Reconcile the dump's task statuses against the target project's task statuses by `(name, description)`
   equality, reusing matches and inserting new `TaskStatus` rows for anything genuinely new, then remap every
   loaded `TaskVersion.taskStatusId` (and `Task.projectId`) accordingly before merging.

## Decisions confirmed with the user

- **Dump path semantics change**: the `dumpPath` in `TaskLoadReq` now names the *exact* dump folder (the one
  directly containing `project.json` and the `<taskId>.json` files), not a base directory that gets
  `projectFolderName(dbProject)` appended. This is also effectively a bug fix: `TaskLoadComponent`'s existing
  "Dump path to load from" text field (`f-core/.../ui/taskload/TaskLoadComponent.scala:47-52`) already asks the
  user to paste/select a literal absolute path with no hint that a subfolder gets appended, and
  `TaskLoadConfig.dumpPath` (`b-05-core/.../taskloadconfig/TaskLoadConfig.scala`) is just a free-text field with
  no project-name/id awareness either — so today's actual UX already matches "this is the exact folder,"
  it's only the backend's `readDumpedTasks(basePath, project)` that (incorrectly, for the cross-DB case)
  recomputes a subfolder from the *target* project instead of trusting the given path.
- **No mapping found → reject with structured info, no auto-create.** The response tells the caller the dump's
  project id + name so the frontend (a later pass) can prompt "create a mapping?" and call the existing
  `POST /project-mapping` endpoint, then retry the load. The backend never creates a mapping on its own.
- **Existing mapping points elsewhere → hard error.** If the target DB project already has a `project_mapping`
  row (unique per `project_id_db`) whose `project_id_dump` doesn't match this dump's project id, refuse to load
  rather than silently overwriting the mapping.
- **Backend only for this pass** — no new frontend components/dialogs. `TaskLoadResp` gains an additive field
  (see below) that a later frontend pass can act on; existing `TaskLoadComponent` code keeps compiling and
  working for the already-mapped case since all changes are additive to the DTO.

## 1. `TaskDumpDiskWriter` — read a manifest/tasks directly from a given folder

File: `b-05-disk-sync/src/main/scala/com/togger/dump/TaskDumpDiskWriter.scala`

Add two methods alongside the existing `write`/`readDumpedTasks` (existing methods/behavior untouched — they're
still used by `TaskDumpService.checkStatus`, which is out of scope here):

```scala
def readManifest(folderPath: String): Either[String, ProjectDumpManifest] =
  Try(diskOps.readFileFromDisk(diskOps.resolvePath(folderPath, "project.json"))).toEither.left
    .map(_.getMessage)
    .flatMap(content => decode[ProjectDumpManifest](content).left.map(_.getMessage))

def readDumpedTasksAtPath(folderPath: String): Map[UUID, Either[String, TaskDumpData]] =
  diskOps
    .listFiles(diskOps.resolvePath(folderPath))
    .flatMap { file =>
      Try(UUID.fromString(file.baseName)).toOption.map(taskId => taskId -> parseTaskDumpFile(file))
    }
    .toMap
```

`readDumpedTasksAtPath` is identical to the existing `readDumpedTasks` body minus the `projectFolderName`
subfolder step — `parseTaskDumpFile` is already `private` and reused as-is. `project.json`'s base name
("project") never parses as a `UUID`, so it's already naturally excluded from `readDumpedTasksAtPath`'s result,
same as it is today in `readDumpedTasks`.

## 2. New `ProjectError` cases

File: `b-05-core/src/main/scala/com/togger/project/ProjectError.scala`

```scala
case object DumpManifestMissing extends ProjectError {
  override def message: String = "No project.json manifest found at the given dump path, or it could not be parsed"
}

case object ProjectMappingConflict extends ProjectError {
  override def message: String =
    "This project already has a project mapping pointing to a different dump project"
}
```

Note: "mapping required" is deliberately **not** modeled as a `ProjectError`. `TgError`'s wire representation
(`TgJsonError(message: String, type: String)` in `c-01-common/.../common/TgJsonSuccess.scala`) only carries a
message string — `TgError.toBadRequestLeft` discards any extra fields on a case class, so an error can't carry
the structured `(dumpProjectId, dumpProjectName)` payload the frontend needs to pre-fill a "create mapping" form.
It's carried in the success payload instead (§6).

## 3. `TaskLoadRepo` — mapping resolution + task status reconciliation

File: `b-05-disk-sync/src/main/scala/com/togger/dump/TaskLoadRepo.scala`

New constructor deps: `projectMappingRepo: ProjectMappingRepo`, `taskStatusRepo: TaskStatusRepo`,
`tgClock: TgClock` (existing 6 repo deps unchanged).

New result type (in `TaskLoadModels.scala`):

```scala
sealed trait MappingResolution
object MappingResolution {
  case object Required extends MappingResolution
  case object Conflict extends MappingResolution
  case class Resolved(taskStatusIdMap: Map[UUID, UUID]) extends MappingResolution
}
```

New method on `TaskLoadRepo`, called once per `loadProject` call (not per task):

```scala
def prepareLoad(projectIdDb: UUID, manifest: ProjectDumpManifest): ConnectionIO[MappingResolution] =
  projectMappingRepo.findByProjectIdDb(projectIdDb).map(_.headOption).flatMap {
    case None                                                        => MappingResolution.Required.pure[ConnectionIO].widen
    case Some(mapping) if mapping.projectIdDump != manifest.project.id => MappingResolution.Conflict.pure[ConnectionIO].widen
    case Some(_)                                                     => reconcileTaskStatuses(projectIdDb, manifest.taskStatuses).map(MappingResolution.Resolved)
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
          taskStatusRepo.insert(diskStatus.copy(projectId = projectIdDb, createdAt = now, updatedAt = now)).map { created =>
            (byKey + ((diskStatus.name, diskStatus.description) -> created.id), idMap + (diskStatus.id -> created.id))
          }
      }
    }
  } yield folded._2
```

Key details:
- Matching is by `(name, description)` only, scoped to the target project (`taskStatusRepo.findByProjectId`),
  per the user's stated rule ("same name and description → same status; otherwise insert new").
- The lookup map (`byKey`) is threaded through a left fold (not a static snapshot + `traverse`) so that if the
  disk manifest itself contains two different task-status ids that happen to share the same
  `(name, description)`, the second one reuses the row just inserted for the first rather than creating a
  duplicate.
- New rows get a fresh id (via `taskStatusRepo.insert`'s existing `uuidv7()` DB default — no `insertWithId`
  needed here, unlike tasks/versions/links/logEntries, since these are new rows in the target project's own
  namespace, not restorations of specific origin ids) and `createdAt`/`updatedAt` from `tgClock`, matching
  `TaskStatusRepoImpl.insert`'s existing column list (`TaskStatusRepoImpl.scala:18-37`).
- Result is a disk-status-id → db-status-id map, built fresh every call — this makes status reconciliation
  naturally idempotent across repeated loads (a re-run finds the same `(name, description)` match instead of
  re-inserting), with no separate persisted mapping table needed for statuses (unlike projects, which do need
  the persisted `project_mapping` table since project name/description aren't guaranteed unique/stable).

`mergeTask` itself (`TaskLoadRepo.scala:19-45`) is **not modified** — remapping happens in the service layer
before `mergeTask` is called (§4), keeping this method's existing behavior/tests intact.

## 4. `TaskLoadService` — orchestration changes

File: `b-05-disk-sync/src/main/scala/com/togger/dump/TaskLoadService.scala`

New constructor dep: none beyond what's already needed — `taskLoadRepo` already gains the new deps in §3;
`TaskLoadService`'s own constructor shape (`taskDumpRepo`, `taskLoadRepo`, `taskDumpDiskWriter`, `transactor`)
is unchanged.

Revised `loadProject`:

```scala
def loadProject(projectId: UUID, accountId: UUID, dumpPath: String): IO[Either[ProjectError, TaskLoadOutcome]] = {
  logger.info(s"Loading tasks for ${sp(projectId)} from ${sp(dumpPath)} requested by ${sp(accountId)}")
  taskDumpDiskWriter.readManifest(dumpPath) match {
    case Left(_) => IO.pure(Left(ProjectError.DumpManifestMissing))
    case Right(manifest) =>
      prepareLoad(projectId, accountId, manifest)
        .transact(transactor)
        .flatMap {
          case Left(error)                                  => IO.pure(Left(error))
          case Right(MappingResolution.Required)             =>
            IO.pure(Right(TaskLoadOutcome.MappingRequired(manifest.project.id, manifest.project.name)))
          case Right(MappingResolution.Conflict)             =>
            IO.pure(Left(ProjectError.ProjectMappingConflict))
          case Right(MappingResolution.Resolved(statusIdMap)) =>
            val diskTasksById = taskDumpDiskWriter.readDumpedTasksAtPath(dumpPath)
            diskTasksById.toList
              .traverse {
                case (taskId, Left(parseError)) =>
                  IO.pure(TaskLoadTaskResult(taskId, None, TaskLoadTaskStatus.Malformed, 0, 0, 0, Some(parseError)))
                case (taskId, Right(diskData)) =>
                  val remapped = remapForTarget(diskData, projectId, statusIdMap)
                  taskLoadRepo.mergeTask(remapped).transact(transactor).attempt.map {
                    case Right(result) => result
                    case Left(e) =>
                      TaskLoadTaskResult(taskId, diskData.taskVersions.headOption.map(_.name), TaskLoadTaskStatus.Failed, 0, 0, 0, Some(e.getMessage))
                  }
              }
              .map(results => Right(TaskLoadOutcome.Loaded(TaskLoadResult(dumpPath, results.size, results))))
        }
  }
}

private def prepareLoad(projectId: UUID, accountId: UUID, manifest: ProjectDumpManifest): ConnectionIO[Either[ProjectError, MappingResolution]] =
  taskDumpRepo.isAssigned(projectId, accountId).flatMap { assigned =>
    if (!assigned) (NotAssignedToProject: ProjectError).asLeft[MappingResolution].pure[ConnectionIO]
    else taskLoadRepo.prepareLoad(projectId, manifest).map(_.asRight[ProjectError])
  }

private def remapForTarget(diskData: TaskDumpData, projectIdDb: UUID, taskStatusIdMap: Map[UUID, UUID]): TaskDumpData =
  diskData.copy(
    task = diskData.task.copy(projectId = projectIdDb),
    taskVersions = diskData.taskVersions.map(v => v.copy(taskStatusId = taskStatusIdMap.getOrElse(v.taskStatusId, v.taskStatusId)))
  )
```

Notes:
- `taskDumpRepo.getProjectAndTaskStatuses` is no longer needed here (it existed only to obtain the `Project`
  row for `projectFolderName`, which §1 removes the need for) — drops that dependency's usage in this class,
  though `TaskDumpRepo` itself is unchanged (still used by `TaskDumpService`).
- If a `TaskVersion.taskStatusId` isn't found in `taskStatusIdMap` (shouldn't happen for a well-formed dump —
  every status referenced by a task version should also be listed in the manifest — but is possible for a
  malformed/hand-edited dump), `remapForTarget` leaves it as the original disk id via `getOrElse`; the
  subsequent insert then fails on the `task_status` FK constraint and that one task surfaces as `Failed` with
  the DB error message, exactly like any other per-task DB failure today (`task_load_backend.md`'s existing
  "Failed" path) — no special-case handling needed.
- `TaskLoadOutcome` (new, in `TaskLoadModels.scala`):
  ```scala
  sealed trait TaskLoadOutcome
  object TaskLoadOutcome {
    case class MappingRequired(dumpProjectId: UUID, dumpProjectName: String) extends TaskLoadOutcome
    case class Loaded(result: TaskLoadResult) extends TaskLoadOutcome
  }
  ```

## 5. Shared DTO changes

File: `c-01-common/shared/src/main/scala/com/togger/dto/TaskLoadResp.scala`

Add one new response type and one new field on the existing `TaskLoadResp` (additive — every existing named
field stays, so `TaskLoadComponent`'s `dto.results`/`dto.taskCount` accesses keep compiling unchanged):

```scala
case class TaskLoadMappingRequiredResp(dumpProjectId: UUID, dumpProjectName: String)

case class TaskLoadResp(
  projectId: UUID,
  dumpPath: String,
  mappingRequired: Option[TaskLoadMappingRequiredResp],
  taskCount: Int,
  results: List[TaskLoadTaskResultResp]
)
```

Convention: `mappingRequired = Some(...)` implies `taskCount = 0` and `results = Nil` (load did not proceed);
`None` means the load ran to completion (possibly with per-task `Malformed`/`Failed` entries in `results`, same
as today). A flat `Option` field is chosen over a sealed-trait response ADT to avoid a circe/tapir discriminator
change on the wire and to keep this a small, additive change to an existing shared DTO.

## 6. Controller changes

File: `b-06-delivery-http4s/src/main/scala/com/togger/controllers/project/TaskDumpController.scala`

```scala
val loadTasks = taskDumpEndpoints.loadEndpoint.serverLogic { auth =>
  { case (projectId, req) =>
    taskLoadService
      .loadProject(projectId, auth.body.get.id, req.dumpPath)
      .map {
        case Right(TaskLoadOutcome.MappingRequired(dumpProjectId, dumpProjectName)) =>
          TgJsonSuccess.justBodyRight(
            TaskLoadResp(projectId, req.dumpPath, Some(TaskLoadMappingRequiredResp(dumpProjectId, dumpProjectName)), 0, Nil)
          )
        case Right(TaskLoadOutcome.Loaded(result)) =>
          val results = result.taskResults.map(toResp)
          TgJsonSuccess.justBodyRight(TaskLoadResp(projectId, result.dumpPath, None, result.taskCount, results))
        case Left(error) => error.toBadRequestLeft[TaskLoadResp]
      }
  }
}
```

No endpoint/route signature change (`TaskDumpEndpoints.loadEndpoint`, `c-03-http-endpoints/.../TaskDumpEndpoints.scala:28-32`,
stays `POST /project/dump/load/{project-id}` with `TaskLoadReq` body) — only the response body's shape changes,
additively.

## 7. Wiring — `TgServer.DependenciesModule`

File: `b-07-main/src/main/scala/com/togger/TgServer.scala`

`projectMappingRepo`, `taskStatusRepo`, and `tgClock` `lazy val`s already exist (reused from the existing
project-mapping and task-status wiring). Update the existing `taskLoadRepo` construction:

```scala
lazy val taskLoadRepo = new TaskLoadRepo(
  taskRepo,
  taskVersionRepo,
  linkRepo,
  linkVersionRepo,
  logEntryRepo,
  logEntryVersionRepo,
  projectMappingRepo,
  taskStatusRepo,
  tgClock
)
```

`taskLoadService`'s own construction is unchanged. No new endpoints to register — `loadTasks` is already wired.

## 8. Tests

`b-07-main/src/test/scala/com/togger/dump/TaskLoadServiceTest.scala` already exists (from the original task-load
feature) and its existing tests call `taskLoadService.loadProject(savedProject.id, savedAccount.id, dumpBasePath.toString)`
using **the same project for dump and load** — every one of those tests will need a `project_mapping` row
inserted in setup (`ProjectMapping(id = UUID.randomUUID(), projectIdDb = savedProject.id, projectIdDump = savedProject.id, ...)`)
so `prepareLoad` resolves instead of returning `MappingRequired`, since these tests dump and reload into the
same project id. Also add `ProjectMappingRepoImpl.PROJECT_MAPPING_TABLE` to this test's cleanup if not already
covered by `BusinessTgDbSpec`'s shared table list.

New scenarios to add to `TaskLoadServiceTest`:
- No `project_mapping` row for the target project → `Right(TaskLoadOutcome.MappingRequired(dumpProjectId, dumpProjectName))`,
  matching the dumped project's own id/name, no DB writes (no task/version/link/logEntry rows created).
- A `project_mapping` row exists for the target project but its `projectIdDump` doesn't match the dump's
  `project.json` id → `Left(ProjectError.ProjectMappingConflict)`.
- Mapping resolves; dump's `taskStatuses` include one whose `(name, description)` already exists in the target
  project's task statuses (different id) → no new `TaskStatus` row inserted, loaded `TaskVersion.taskStatusId`
  points at the *existing* target-project status id, not the disk one.
- Mapping resolves; dump's `taskStatuses` include one with a `(name, description)` not present in the target
  project → a new `TaskStatus` row is inserted (`projectId` = target project), and loaded
  `TaskVersion.taskStatusId` points at the newly inserted row's id.
- `Task.projectId` on every loaded/merged task is the *target* DB project id, not the dump's original project id.
- Missing/unparseable `project.json` at `dumpPath` → `Left(ProjectError.DumpManifestMissing)`.
- Re-running the same load twice stays idempotent for task statuses too (second run reconciles to the same
  existing rows rather than inserting duplicates) — extend the existing "merge... and be idempotent on reload"
  test rather than duplicating its setup.

New test for `TaskLoadRepo.prepareLoad`/`reconcileTaskStatuses` directly (real Postgres transactor, extends
`BusinessTgDbSpec with TgProject with TgTaskStatus`) covering the three `MappingResolution` branches and the
fold-based dedup behavior (two disk statuses sharing one `(name, description)` collapse to one DB row).

Run via `sbt "backendMain/testOnly com.togger.dump.TaskLoadServiceTest"` (Postgres must be running per
`CLAUDE.md`).

## Explicitly out of scope

- Any frontend changes (`TaskLoadComponent`, a "create mapping" prompt/dialog, retry-after-creating-mapping
  flow) — a later pass, once this backend contract is in place.
- Changing `TaskDumpService.dumpProject`/`checkStatus` or `TaskDumpConfig` — dump-side behavior and the
  read-only status-diff feature are untouched.
- A reverse `ProjectMappingRepo.findByProjectIdDump` finder — not needed since lookup here is always keyed by
  the (already-known, from the URL path param) target DB project id via the existing `findByProjectIdDb`.
- Auto-creating or updating `project_mapping` rows from the load flow itself, in either the "no mapping" or
  "conflicting mapping" case — both are surfaced to the caller to resolve explicitly via the existing
  project-mapping CRUD endpoints.
- Any change to `TaskLoadConfig`/`TaskLoadConfigService`/`TaskLoadConfigFeService` (the preconfigured-paths
  feature) — it only stores/lists path strings and is unaffected by how those paths get interpreted downstream.
