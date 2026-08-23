# Project Mapping — Backend Implementation Plan

## Goal

Add a new `project_mapping` domain that records a mapping between a project id found in an imported
dump file (`project_id_origin`) and a project already existing in this database that the dump should be
loaded into (`project_id_loaded`). This is step one of the larger "load projects/tasks/log
entries/links from another dump" feature — **this plan covers only the CRUD backend slice for the
mapping table itself**. Nothing about actually reading dump files or importing tasks/log
entries/links is implemented here.

Scope: **backend only** (no frontend/UI, no ScalaJS calls). Plain CRUD, no import logic.

Template followed: `TaskDumpConfig` (`b-05-core/.../taskdumpconfig/`), the closest existing example of
a simple UUID-keyed config table with FK(s) to `project`.

## Confirmed decisions

- Timestamp column is `updated_at` (not `updated_t` — typo fix, consistent with every other table).
- `project_id_loaded` has a **unique constraint** — a given "loaded into" project can only be the
  target of one mapping at a time.
- One finder beyond plain CRUD: `findByProjectIdLoaded`.

## 1. Liquibase changeset

File: `b-05-database/src/main/resources/liquibase/changelog.yaml`

Append a new `createTable` entry to the existing single changeset (`id: 1`), following this repo's
existing convention of appending rather than adding a new changeset id:

```yaml
        - createTable:
            tableName: project_mapping
            columns:
              - column:
                  name: id
                  type: uuid
                  defaultValueComputed: uuidv7()
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: project_id_origin
                  type: uuid
                  constraints:
                    nullable: false
                    foreignKeyName: fk_project_mapping_project_origin
                    references: project(id)
              - column:
                  name: project_id_loaded
                  type: uuid
                  constraints:
                    nullable: false
                    unique: true
                    foreignKeyName: fk_project_mapping_project_loaded
                    references: project(id)
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

## 2. Domain model

`b-05-core/src/main/scala/com/togger/projectmapping/ProjectMapping.scala`

```scala
package com.togger.projectmapping

import java.time.Instant
import java.util.UUID

case class ProjectMapping(
  id: UUID,
  projectIdOrigin: UUID,
  projectIdLoaded: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
```

## 3. Shared DTOs

`c-01-common/shared/src/main/scala/com/togger/dto/projectmapping/ProjectMappingDto.scala`

```scala
package com.togger.dto.projectmapping

import java.time.Instant
import java.util.UUID

case class CreateProjectMappingReq(projectIdOrigin: UUID, projectIdLoaded: UUID)

case class UpdateProjectMappingReq(id: UUID, projectIdOrigin: UUID, projectIdLoaded: UUID)

case class ProjectMappingResp(
  id: UUID,
  projectIdOrigin: UUID,
  projectIdLoaded: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
```

## 4. Repo trait

`b-05-core/src/main/scala/com/togger/projectmapping/ProjectMappingRepo.scala`

```scala
package com.togger.projectmapping

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait ProjectMappingRepo {

  def findById(id: UUID): ConnectionIO[ProjectMapping]

  def insert(projectMapping: ProjectMapping): ConnectionIO[ProjectMapping]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(projectMapping: ProjectMapping, updatedAt: Instant): ConnectionIO[ProjectMapping]

  def findByProjectIdLoaded(projectIdLoaded: UUID): ConnectionIO[List[ProjectMapping]]

}
```

(`findByProjectIdLoaded` returns a `List` for symmetry with `TaskDumpConfigRepo`'s finder methods, even
though the unique constraint means it will hold at most one element in practice.)

## 5. Repo implementation

`b-05-database/src/main/scala/com/togger/projectmapping/ProjectMappingRepoImpl.scala`

Mirrors `TaskDumpConfigRepoImpl`:
- `PROJECT_MAPPING_TABLE: String = "project_mapping"` + `PROJECT_MAPPING_TABLE_FRAG` constants on the
  companion object.
- Uses `com.togger.db.Util._` helpers: `findByIdStatementUUID`, `insertStatement`,
  `deleteByIdStatementUUID`, `updateStatementUUID`.
- `insert`/`update` use `.update.withUniqueGeneratedKeys[ProjectMapping](returnFields: _*)` with
  `returnFields` listing columns in case-class order (`id`, `project_id_origin`, `project_id_loaded`,
  `created_at`, `updated_at`).
- `findByProjectIdLoaded` is a plain `WHERE project_id_loaded = ?` select.

## 6. Service

`b-05-core/src/main/scala/com/togger/projectmapping/ProjectMappingService.scala`

Thin service, mirrors `TaskDumpConfigService`:

```scala
package com.togger.projectmapping

import cats.effect.IO
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class ProjectMappingService(
  projectMappingRepo: ProjectMappingRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def create(projectMapping: ProjectMapping): IO[ProjectMapping] = {
    logger.info(s"Creating ${sp(projectMapping)}")
    projectMappingRepo.insert(projectMapping).transact(transactor)
  }

  def findById(id: UUID): IO[ProjectMapping] =
    projectMappingRepo.findById(id).transact(transactor)

  def findByProjectIdLoaded(projectIdLoaded: UUID): IO[List[ProjectMapping]] =
    projectMappingRepo.findByProjectIdLoaded(projectIdLoaded).transact(transactor)

  def update(projectMapping: ProjectMapping): IO[ProjectMapping] = {
    logger.info(s"Updating ${sp(projectMapping)}")
    val now = tgClock.currentInstant
    projectMappingRepo.update(projectMapping, now).transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    projectMappingRepo.deleteById(id).transact(transactor)

}
```

## 7. Tapir endpoints

`c-03-http-endpoints/shared/src/main/scala/com/togger/projectmapping/ProjectMappingEndpoints.scala`

Mirrors `TaskDumpConfigEndpoints`, `basePath = "project-mapping"`:
- `saveEndpoint`: `POST /project-mapping`, body `CreateProjectMappingReq` → `ProjectMappingResp`
- `findByIdEndpoint`: `GET /project-mapping/{id}` → `ProjectMappingResp`
- `updateEndpoint`: `PUT /project-mapping`, body `UpdateProjectMappingReq` → `ProjectMappingResp`
- `deleteByIdEndpoint`: `DELETE /project-mapping/{id}` → empty body
- `findByProjectIdLoadedEndpoint`: `GET /project-mapping/project-loaded/{id}` → `Seq[ProjectMappingResp]`

All built on `securedEndpoint.endpoint` (bearer auth), same as every other domain.

## 8. Controller

`b-06-delivery-http4s/src/main/scala/com/togger/controllers/projectmapping/ProjectMappingController.scala`

Mirrors `TaskDumpConfigController`:
- Constructor: `(projectMappingEndpoints: ProjectMappingEndpoints, projectMappingService: ProjectMappingService, tgClock: TgClock)`.
- `save`: generates `UUID.randomUUID()` for the new id, `tgClock.currentInstant` for both timestamps,
  builds a `ProjectMapping`, calls `service.create`, maps to `ProjectMappingResp` via `TgJsonSuccess.justBodyRight`.
- `findById`, `update`, `delete`, `findByProjectIdLoaded` follow the same pattern as their
  `TaskDumpConfigController` counterparts.
- No verification class needed — plain CRUD, no extra validation rules requested.

## 9. Wiring — `TgServer.DependenciesModule`

File: `b-07-main/src/main/scala/com/togger/TgServer.scala`

Add imports:
```scala
import com.togger.controllers.projectmapping.ProjectMappingController
import com.togger.projectmapping.{ProjectMapping, ProjectMappingEndpoints, ProjectMappingRepoImpl, ProjectMappingService}
```

Add `lazy val`s in `DependenciesModule`:
```scala
lazy val projectMappingRepo = new ProjectMappingRepoImpl
lazy val projectMappingService = new ProjectMappingService(projectMappingRepo, tgClock, transactor)
lazy val projectMappingEndpoints = new ProjectMappingEndpoints(securedEndpoint)
lazy val projectMappingController = new ProjectMappingController(projectMappingEndpoints, projectMappingService, tgClock)
```

Append to the endpoint `List(...)` passed to `httpModule.createServer`:
```scala
projectMappingController.findById,
projectMappingController.save,
projectMappingController.update,
projectMappingController.delete,
projectMappingController.findByProjectIdLoaded,
```

## 10. Repo test

`b-05-database/src/test/scala/com/togger/projectmapping/ProjectMappingRepoTest.scala`

- Extends `BusinessTgDbSpec with TgProject with TimeHelpers`.
- Add `ProjectMappingRepoImpl.PROJECT_MAPPING_TABLE` to `BusinessTgDbSpec.dropTablesQuery`'s table list
  (before `ProjectRepoImpl.PROJECT_TABLE`, since `project_mapping` depends on `project` via FK).
- Test setup creates **two** projects via `createOrgAndProject` (one to act as origin, one as loaded)
  since `project_id_origin` and `project_id_loaded` reference different rows.
- Covers: insert + findById round-trip, update, findByProjectIdLoaded, deleteById followed by findById
  failing (`.attempt.map(_.isLeft shouldBe true)`), and insert violating the unique constraint on
  `project_id_loaded` (second insert with the same `project_id_loaded` should fail).

## 11. Service test

`b-05-database/src/test/scala/com/togger/projectmapping/ProjectMappingServiceTest.scala`

- Mirrors `TaskDumpConfigServiceTest`: `AsyncWordSpec with AsyncMockFactory with Matchers with
  TestNameHelper with MockedTransactor with TimeHelpers with Checkpoints with TestLoggingJvm`.
- Mocks `ProjectMappingRepo` and `TgClock`, wires a real `ProjectMappingService` against them with the
  mocked transactor.
- Covers `create`, `findById`, `findByProjectIdLoaded`, `update` (asserting `tgClock.currentInstant` is
  used to stamp `updatedAt`), and `deleteById`.

## Explicitly out of scope (future steps, not part of this plan)

- Any dump-file reading/parsing.
- Actually importing tasks, log entries, or links using a `project_mapping` row.
- Frontend UI/backend-calls for this feature.
- Any endpoint/service beyond plain CRUD + `findByProjectIdLoaded`.
