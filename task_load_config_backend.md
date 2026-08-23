# Task Load Configuration (backend)

## Context

We're adding a new `task_load_config` DB table + full backend stack (Repo, Service, Controller, Endpoint,
tests). This is a sibling/counterpart configuration to the existing `task_dump_config` feature
(`b-05-core/.../taskdumpconfig`, `b-05-database/.../taskdumpconfig`, `b-06-delivery-http4s/.../taskdumpconfig`,
`c-01-common/.../dto/taskdumpconfig`, `c-03-http-endpoints/.../taskdumpconfig`), which is a complete,
already-shipped reference implementation for exactly this shape of feature (a per-project/per-account path
config with CRUD + find-by-project/find-by-account). `task_load_config` is structurally identical except it
adds a `name` field.

Note: this is unrelated to the already-implemented `TaskLoadService`/`TaskLoadRepo`/`TaskLoadModels` in
`b-05-disk-sync/.../dump` (the disk→DB import feature from `task_load_backend.md`, WIP on this branch) — that
feature explicitly takes its dump path from the request body and does *not* use any config table. To avoid
any naming collision with those classes, all new types use the `TaskLoadConfig*` name (with "Config"), never
bare `TaskLoad*`.

This is backend-only, per the user's instruction — no `f-core` changes.

Decisions confirmed with the user:
- `account_id` on create/update comes from the authenticated session (`auth.body.get.id`), exactly like
  `TaskDumpConfigController` — not from the request DTO.
- `TaskLoadConfigVerification` mirrors `TaskDumpConfigVerification`'s absolute-path check for `dump_path`, plus
  a new non-blank check for `name`.

## 1. DB migration

Add a `createTable` block for `task_load_config` to the single existing changeset (`id: 1`) in
`b-05-database/src/main/resources/liquibase/changelog.yaml`, right after the `task_dump_config` table
definition — same shape, with `name` added:

```yaml
- createTable:
    tableName: task_load_config
    columns:
      - column:
          name: id
          type: uuid
          defaultValueComputed: uuidv7()
          constraints:
            primaryKey: true
            nullable: false
      - column:
          name: name
          type: varchar(255)
          constraints:
            nullable: false
            unique: false
      - column:
          name: dump_path
          type: text
          constraints:
            nullable: false
            unique: false
      - column:
          name: project_id
          type: uuid
          constraints:
            nullable: false
            foreignKeyName: fk_task_load_config_project
            references: project(id)
      - column:
          name: account_id
          type: uuid
          constraints:
            nullable: false
            foreignKeyName: fk_task_load_config_account
            references: account(id)
      - column:
          name: created_at
          type: timestamp with time zone
          defaultValueComputed: now()
          constraints:
            nullable: false
      - column:
          name: updated_at
          type: timestamp with time zone
          defaultValueComputed: now()
          constraints:
            nullable: false
```

**Gotcha (learned while implementing):** the table's physical column order must match the `TaskLoadConfig`
case class field order exactly, because `TaskLoadConfigRepoImpl.findById`/`findByProjectId`/`findByAccountId`
use `select * from task_load_config` (`selectAllStatement`), and doobie's derived `Read[TaskLoadConfig]` maps
result-set columns to case class fields *positionally*. `insert`/`update` are unaffected (they use
`withUniqueGeneratedKeys` with an explicit `returnFields` list), but a `dump_path` column placed after
`project_id`/`account_id` in the table — while the case class has `dumpPath` before `projectId`/`accountId` —
causes a `doobie.util.invariant$InvalidObjectMapping` at runtime on any `select *` read. Column order above is
`id, name, dump_path, project_id, account_id, created_at, updated_at`, matching the case class.

Since this repo is pre-release (single "creates all tables" changeset, no released changesets to preserve),
add the table directly into changeset `id: 1` rather than appending a new changeset — matches how
`task_dump_config` itself was added.

## 2. Domain model + repo trait — `b-05-core/src/main/scala/com/togger/taskloadconfig/`

Mirrors `b-05-core/.../taskdumpconfig/TaskDumpConfig.scala` + `TaskDumpConfigRepo.scala` exactly, field order
per the user's spec (`id, name, dump_path, project_id, account_id, created_at, updated_at`):

**`TaskLoadConfig.scala`**
```scala
case class TaskLoadConfig(
  id: UUID,
  name: String,
  dumpPath: String,
  projectId: UUID,
  accountId: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
```

**`TaskLoadConfigRepo.scala`** — same method set as `TaskDumpConfigRepo`:
```scala
trait TaskLoadConfigRepo {
  def findById(id: UUID): ConnectionIO[TaskLoadConfig]
  def insert(taskLoadConfig: TaskLoadConfig): ConnectionIO[TaskLoadConfig]
  def deleteById(id: UUID): ConnectionIO[Int]
  def update(taskLoadConfig: TaskLoadConfig, updatedAt: Instant): ConnectionIO[TaskLoadConfig]
  def findByProjectId(projectId: UUID): ConnectionIO[List[TaskLoadConfig]]
  def findByAccountId(accountId: UUID): ConnectionIO[List[TaskLoadConfig]]
}
```

**`TaskLoadConfigService.scala`** — identical shape to `TaskDumpConfigService` (constructor:
`taskLoadConfigRepo`, `tgClock: TgClock`, `transactor: Transactor[IO]`), same `create`/`findById`/
`findByProjectId`/`findByAccountId`/`update` (stamps `updatedAt` from `tgClock.currentInstant`)/`deleteById`
methods, same `TgLogging` + `SplunkMacro.sp` logging style.

## 3. Repo implementation — `b-05-database/src/main/scala/com/togger/taskloadconfig/TaskLoadConfigRepoImpl.scala`

Mirrors `TaskDumpConfigRepoImpl` exactly, reusing `com.togger.db.Util._` helpers
(`findByIdStatementUUID`, `insertStatement`, `deleteByIdStatementUUID`, `updateStatementUUID`,
`selectAllStatement`, `logAroundInfoQuery`):

- `returnFields = List("id", "name", "dump_path", "project_id", "account_id", "created_at", "updated_at")`
  (column order must match the `TaskLoadConfig` case class field order for `.query[TaskLoadConfig]` /
  `withUniqueGeneratedKeys` to map correctly).
- `insert`: `insert into task_load_config (name, project_id, account_id, dump_path, created_at, updated_at) values (...)`.
- `update`: sets `name`, `project_id`, `account_id`, `dump_path`, `updated_at`.
- `findByProjectId`/`findByAccountId`: `selectAllStatement(...) ++ fr"where project_id = $projectId"` /
  `account_id = $accountId`.
- Companion object: `TASK_LOAD_CONFIG_TABLE = "task_load_config"`, `TASK_LOAD_CONFIG_TABLE_FRAG`.

## 4. Shared DTOs — `c-01-common/shared/src/main/scala/com/togger/dto/taskloadconfig/TaskLoadConfigDto.scala`

Mirrors `TaskDumpConfigDto.scala`, with `name` added and (per the account_id decision) no `accountId` field
on the request DTOs:

```scala
case class CreateTaskLoadConfigReq(name: String, projectId: UUID, dumpPath: String)
case class UpdateTaskLoadConfigReq(id: UUID, name: String, projectId: UUID, dumpPath: String)
case class TaskLoadConfigResp(
  id: UUID, name: String, projectId: UUID, accountId: UUID, dumpPath: String,
  createdAt: Instant, updatedAt: Instant
)
```

## 5. Shared endpoints — `c-03-http-endpoints/shared/src/main/scala/com/togger/taskloadconfig/TaskLoadConfigEndpoints.scala`

Mirrors `TaskDumpConfigEndpoints` exactly, `basePath = "task-load-config"`, same 6 endpoints
(`saveEndpoint` POST, `findByIdEndpoint` GET `/{id}`, `updateEndpoint` PUT, `deleteByIdEndpoint` DELETE
`/{id}`, `findByProjectIdEndpoint` GET `/project/{id}`, `findByAccountIdEndpoint` GET `/account/{id}`), using
the new `CreateTaskLoadConfigReq`/`UpdateTaskLoadConfigReq`/`TaskLoadConfigResp` DTOs.

## 6. Controller + verification — `b-06-delivery-http4s/src/main/scala/com/togger/controllers/taskloadconfig/`

**`TaskLoadConfigVerification.scala`** — mirrors `TaskDumpConfigVerification`, extended per the user's answer:
```scala
sealed trait TaskLoadConfigError { def message: String }
case class InvalidPath(dumpPath: String) extends TaskLoadConfigError { ... }        // same message/logic as today
case class BlankName(name: String) extends TaskLoadConfigError {
  override def message: String = "Name must not be blank."
}

class TaskLoadConfigVerification extends TgLogging {
  def verify(name: String, dumpPath: String): Either[TaskLoadConfigError, (String, String)] =
    for {
      validName <- Either.cond(name.trim.nonEmpty, name, BlankName(name))
      validPath <- Try(os.Path(dumpPath)).toEither.left.map(_ => InvalidPath(dumpPath)).map(_ => dumpPath)
    } yield (validName, validPath)
}
```
(Exact combinator style to match `TaskDumpConfigVerification`'s `Try(...).fold(...)` idiom rather than
`cats.implicits` — keep dependencies the same as the file being mirrored.)

**`TaskLoadConfigController.scala`** — mirrors `TaskDumpConfigController`:
- `toResp` maps `TaskLoadConfig` → `TaskLoadConfigResp` (includes `name`).
- `save`/`update` call `taskLoadConfigVerification.verify(dto.name, dto.dumpPath)`, fold to
  `TgJsonFailure.badRequestStringLeft(error.message)` on `Left`, else build `TaskLoadConfig` with
  `accountId = auth.body.get.id`, `id = UUID.randomUUID()` (save) or `dto.id` (update), timestamps from
  `tgClock.currentInstant`.
- `findById`, `delete`, `findByProjectId`, `findByAccountId` — same pass-through shape as
  `TaskDumpConfigController`.

## 7. Wiring — `b-07-main/src/main/scala/com/togger/TgServer.scala`

Add imports for the new `taskloadconfig`/`controllers.taskloadconfig` packages, and near the existing
`taskDumpConfig*` `lazy val`s (~line 165-170):
```scala
lazy val taskLoadConfigRepo = new TaskLoadConfigRepoImpl
lazy val taskLoadConfigService = new TaskLoadConfigService(taskLoadConfigRepo, tgClock, transactor)
lazy val taskLoadConfigVerification = new TaskLoadConfigVerification()
lazy val taskLoadConfigEndpoints = new TaskLoadConfigEndpoints(securedEndpoint)
lazy val taskLoadConfigController =
  new TaskLoadConfigController(taskLoadConfigEndpoints, taskLoadConfigService, taskLoadConfigVerification, tgClock)
```
Register the 6 new server-logic vals (`findById`, `save`, `update`, `delete`, `findByProjectId`,
`findByAccountId`) in the endpoint list passed to `HttpModule.createServer`, alongside the existing
`taskDumpConfigController.*` entries (~line 89-94).

## 8. Tests

**`b-05-database/src/test/scala/com/togger/common/BusinessTgDbSpec.scala`** — add
`TaskLoadConfigRepoImpl.TASK_LOAD_CONFIG_TABLE` to the `dropTablesQuery` cleanup list (before `account`/
`project`, since `task_load_config` FKs into both), the same way `TaskDumpConfigRepoImpl.TASK_DUMP_CONFIG_TABLE`
is already registered there. Without this, `TaskLoadConfigRepoTest`'s per-test cleanup fails with a foreign key
violation when truncating `account`/`project` while `task_load_config` rows still reference them.

**`b-05-database/src/test/scala/com/togger/taskloadconfig/TaskLoadConfigRepoTest.scala`** — mirrors
`TaskDumpConfigRepoTest` (extends `BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers`, real
Postgres transactor): basic CRUD round-trip (insert → findById → update → findById → deleteById → findById
fails), `findByProjectId` scoping test, `findByAccountId` scoping test — same structure, adding `name` to the
config builder and asserting it round-trips.

**`b-05-database/src/test/scala/com/togger/taskloadconfig/TaskLoadConfigServiceTest.scala`** — mirrors
`TaskDumpConfigServiceTest` (`AsyncWordSpec with AsyncMockFactory ... MockedTransactor`, mocked
`TaskLoadConfigRepo` + `TgClock`): create, findById, findByProjectId, findByAccountId, update (stamps
`updatedAt` from clock), deleteById.

**`b-06-delivery-http4s/src/test/scala/com/togger/controllers/taskloadconfig/TaskLoadConfigVerificationTest.scala`**
— mirrors `TaskDumpConfigVerificationTest` (`TgWordSpecJvm with EitherValues`): valid absolute path + non-blank
name → `Right`; relative path → `Left(InvalidPath)`; `~/...` path → `Left(InvalidPath)`; blank path →
`Left(InvalidPath)`; blank/whitespace-only name → `Left(BlankName)`.

Run via:
```
sbt "backendDatabase/testOnly com.togger.taskloadconfig.*"
sbt "backendDeliveryHttp4s/testOnly com.togger.controllers.taskloadconfig.TaskLoadConfigVerificationTest"
```
(Postgres `togger-test` must be running per `CLAUDE.md` for the DB-backed specs.)

## 9. Plan document

Write this plan to `task_load_config_backend.md` at the repo root (matching the existing sibling
`task_load_backend.md` convention), as the first implementation step.

## Verification

- `sbt backendCore/compile backendDatabase/compile backendDeliveryHttp4s/compile backendMain/compile
  tgCommonJVM/compile httpEndpointsJVM/compile` to confirm the new module compiles end-to-end.
- Run the three new test suites above against local Postgres (`togger-test`), all green.
- `sbt scalafmtAll` before finishing, per repo convention.

## Status: implemented

All of the above was implemented and verified:
- `changelog.yaml` updated; `liquibase --defaults-file=liquibase-test.properties dropAll && ... update` re-run
  against the local `togger-test` DB (required since this repo's single "creates all tables" changeset changes
  checksum on every schema edit — same `dropAll`/`update` cycle as the `reset-pg` devenv script).
- All 9 `TaskLoadConfigRepoTest`/`TaskLoadConfigServiceTest` tests pass, all 6
  `TaskLoadConfigVerificationTest` tests pass.
- Full compile of `backendCore`, `backendDatabase` (main + test), `backendDeliveryHttp4s` (main + test), and
  `backendMain` succeeds with `TgServer` wiring in place.
- `sbt scalafmtAll` could not be run in this environment (no `sbt-scalafmt` plugin loaded outside the devenv
  shell) — new files were hand-matched to the formatting of the `TaskDumpConfig*` files they mirror; run
  `sbt scalafmtAll` from the devenv shell to confirm/fix formatting.
