# Project Mapping — Frontend Implementation Plan

## Goal

Add a frontend UI for the `project_mapping` feature (backend already implemented — see
`project_mapping_backend.md`): for each project, allow creating a mapping to an "origin" project id
found in an imported dump file, editing that mapping, viewing it, and deleting it.

Scope: **frontend only**, plain CRUD UI wired to the existing backend endpoints. No dump-file
reading/import UI.

Template followed: `TaskDumpConfig`'s frontend (`f-core/src/main/scala/com/togger/ui/taskdumpconfig/`)
— a near-identical sibling feature (simple per-project config, CRUD + one project-scoped finder) that
already has a complete `FeService` + Show/Create/Edit component set, routing entries, and a sidebar
icon slot. This is the direct 1:1 template.

## Confirmed decisions

- **Create form**: `projectIdOrigin` is a raw UUID text input (it references a project id from an
  external dump file — not a project that exists locally, so it can't be looked up/selected).
  `projectIdLoaded` is pre-filled/locked to the project the user navigated from (via the sidebar) and is
  not an editable field on the create form.
- **Edit form**: only `projectIdOrigin` is editable. `projectIdLoaded` is shown read-only — it
  identifies which project the mapping belongs to and is unique per project, so it stays fixed after
  creation.
- **Show view**: since `projectIdLoaded` is unique, a given project has at most one mapping. The "show"
  view is a single mapping detail/empty-state view (not a table/list) — it shows the one mapping with
  Edit/Delete actions if it exists, or an empty state with a "Create mapping" call-to-action if it
  doesn't.
- **Delete**: fires immediately on click, no confirmation dialog — matches the existing house
  convention (no confirm-before-delete pattern exists anywhere in this codebase today).

## 1. Routing

`f-core/src/main/scala/com/togger/routes/Router.scala` — add `Pages` cases:

```scala
case class ShowProjectMappingPage(projectId: UUID) extends PrivatePage
case class CreateProjectMappingPage(projectId: UUID) extends PrivatePage
case class EditProjectMappingPage(id: UUID) extends PrivatePage
```

`f-core/src/main/scala/com/togger/routes/TgRoutes.scala` — add three `Route(...)` entries, mirroring
the `TaskDumpConfig` routes exactly:

```scala
Route(
  encode = (page: ShowProjectMappingPage) => page.projectId.toString,
  decode = (projectId: String) => ShowProjectMappingPage(UUID.fromString(projectId)),
  pattern = root / "project-mapping" / "project" / segment[String] / endOfSegments
),
Route(
  encode = (page: CreateProjectMappingPage) => page.projectId.toString,
  decode = (projectId: String) => CreateProjectMappingPage(UUID.fromString(projectId)),
  pattern = root / "project-mapping" / "create" / segment[String] / endOfSegments
),
Route(
  encode = (page: EditProjectMappingPage) => page.id.toString,
  decode = (id: String) => EditProjectMappingPage(UUID.fromString(id)),
  pattern = root / "project-mapping" / "edit" / segment[String] / endOfSegments
)
```

`f-core/src/main/scala/com/togger/TgSplitRender.scala` — add match cases in the page-to-component
mapping (`collectSignal`), each constructing the corresponding component and calling `.element`:

```scala
case page: ShowProjectMappingPage => ShowProjectMappingComponent(page.projectId, ...).element
case page: CreateProjectMappingPage => CreateProjectMappingComponent(page.projectId, ...).element
case page: EditProjectMappingPage => EditProjectMappingComponent(page.id, ...).element
```

## 2. Frontend service (backendcalls)

`f-core/src/main/scala/com/togger/ui/projectmapping/ProjectMappingFeService.scala` — trait + `Impl`,
mirroring `TaskDumpConfigFeService`:

```scala
package com.togger.ui.projectmapping

import com.togger.backendcalls.ProtectedBackendClient
import com.togger.dto.projectmapping.{CreateProjectMappingReq, ProjectMappingResp, UpdateProjectMappingReq}
import com.togger.projectmapping.ProjectMappingEndpoints

import java.util.UUID

trait ProjectMappingFeService {
  def create(req: CreateProjectMappingReq): TgRespIO[ProjectMappingResp]
  def findById(id: UUID): TgRespIO[ProjectMappingResp]
  def update(req: UpdateProjectMappingReq): TgRespIO[ProjectMappingResp]
  def deleteById(id: UUID): TgRespIO[TgEmptyBody]
  def findByProjectIdLoaded(projectIdLoaded: UUID): TgRespIO[Seq[ProjectMappingResp]]
}

class ProjectMappingFeServiceImpl(
  endpoints: ProjectMappingEndpoints,
  client: ProtectedBackendClient
) extends ProjectMappingFeService {

  def create(req: CreateProjectMappingReq) = client.protectedHttpCall(endpoints.saveEndpoint)(req)

  def findById(id: UUID) = client.protectedHttpCall(endpoints.findByIdEndpoint)(id)

  def update(req: UpdateProjectMappingReq) = client.protectedHttpCall(endpoints.updateEndpoint)(req)

  def deleteById(id: UUID) = client.protectedHttpCall(endpoints.deleteByIdEndpoint)(id)

  def findByProjectIdLoaded(projectIdLoaded: UUID) =
    client.protectedHttpCall(endpoints.findByProjectIdLoadedEndpoint)(projectIdLoaded)

}
```

(`TgRespIO[X]` / bearer-token attachment / failure mapping are handled by `ProtectedBackendClient`,
exactly as for every other domain's `FeService`.)

## 3. Show component (single mapping / empty state)

`f-core/src/main/scala/com/togger/ui/projectmapping/ShowProjectMappingComponent.scala` — scoped by
`projectId` (the *loaded* project, i.e. the project the user is currently viewing).

- On mount: `spinnerService.runWithSpinner(service.findByProjectIdLoaded(projectId)...)` into a
  `Var[Option[ProjectMappingResp]]` (take `.headOption` of the returned `Seq`, which will hold 0 or 1
  elements given the uniqueness constraint).
- Renders a `TgCard`:
  - **if present**: mapping details (origin project id, loaded project id, created/updated
    timestamps), an **Edit** button (`router.route(EditProjectMappingPage(mapping.id))`), and a
    **Delete** button that fires `spinnerService.runWithSpinner(service.deleteById(mapping.id))
    .toastBackendMessageCodeBlock { ... }` immediately on click (no confirmation), then re-renders the
    empty state (e.g. by setting the `Var` to `None` or re-fetching).
  - **if absent**: an empty-state message plus a **Create mapping** button
    (`router.route(CreateProjectMappingPage(projectId))`).

## 4. Create component

`f-core/src/main/scala/com/togger/ui/projectmapping/CreateProjectMappingComponent.scala` — mirrors
`CreateTaskDumpConfigComponent`'s `Var[FormState]` / `tgInputManualErrorLPSig` pattern:

- `projectIdLoaded` comes directly from the route's `projectId` param — not an editable form field
  (may be shown read-only for context, e.g. "Mapping this project to:").
- `projectIdOrigin` is a text input with UUID-format validation: parse with `UUID.fromString` inside a
  `Try`, surface a validation error via the same input-error plumbing used elsewhere
  (`tgInputManualErrorLPSig`) if parsing fails, and disable submit until valid.
- Submit builds `CreateProjectMappingReq(projectIdOrigin, projectIdLoaded)`, calls
  `spinnerService.runWithSpinner(service.create(req))`, and on success toasts
  ("Project mapping created successfully") and navigates to `ShowProjectMappingPage(projectIdLoaded)`.

## 5. Edit component

`f-core/src/main/scala/com/togger/ui/projectmapping/EditProjectMappingComponent.scala`:

- On mount, loads the existing mapping via `service.findById(id)` into a `Var[FormState]`.
- `projectIdLoaded` is displayed read-only (not an editable input).
- `projectIdOrigin` is the only editable field, with the same UUID-format validation as Create.
- Submit builds `UpdateProjectMappingReq(id, projectIdOrigin, projectIdLoaded)`, calls
  `service.update(...)`, toasts ("Project mapping updated successfully"), and navigates back to
  `ShowProjectMappingPage(projectIdLoaded)`.

## 6. Sidebar entry point

`f-core/src/main/scala/com/togger/ui/navbar/Sidebar.scala`:

- Add a new slot to `SidebarLink`: `onProjectMappingClick: Option[() => Unit] = None`, rendered as an
  icon button next to the existing per-project action icons (`onDumpClick`, `onConfigClick`,
  `onStatusClick`, `onLoadClick`, `onLoadConfigClick`). Pick an unused icon from
  `f-core/src/main/scala/com/togger/ui/organization/TgIcons.scala` — `briefcaseIcon` is free
  (`listIcon` is already used by load-config).
- At the call site building `SidebarLink` for each project, wire:
  ```scala
  onProjectMappingClick = Some(() => router.route(ShowProjectMappingPage(project.id)))
  ```

## Explicitly out of scope

- Any dump-file reading/parsing/import UI.
- Backend changes (already implemented — see `project_mapping_backend.md`).
- A table/list UI for mappings — not needed given the uniqueness constraint on `projectIdLoaded`
  (single detail/empty-state view instead).
- Confirm-before-delete UX — no such pattern exists anywhere in this codebase today; deletes fire
  immediately, matching `ShowAllTaskDumpConfigsComponent`.
