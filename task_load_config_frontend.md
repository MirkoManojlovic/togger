# Task Load Configuration (frontend)

## Context

The backend for `task_load_config` (`b-05-core/.../taskloadconfig`, `b-05-database/.../taskloadconfig`,
`b-06-delivery-http4s/.../controllers/taskloadconfig`, `c-01-common/.../dto/taskloadconfig`,
`c-03-http-endpoints/.../taskloadconfig`) already exists (see `task_load_config_backend.md`) — a per-project,
per-account saved config with `id, name, dumpPath, projectId, accountId, createdAt, updatedAt` and full CRUD +
`findByProjectId`/`findByAccountId` endpoints, wired into `TgServer`.

This plan adds the `f-core` UI for it, mirroring the existing `task_dump_config` UI
(`f-core/src/main/scala/com/togger/ui/taskdumpconfig/`) file-for-file, since that feature is structurally
identical minus the `name` field. `f-core` has no unit tests for this kind of component today (none exist for
`ui/taskdumpconfig`); verification is manual, via the dev server, per `CLAUDE.md`'s UI-change guidance.

Decisions confirmed with the user:
- **Per-project scoped**, exactly like `task_dump_config`: reached via a new icon button on each project's row
  in the sidebar (`Sidebar.scala`), listing/creating/editing configs scoped to that project
  (`findByProjectId`), not a single account-wide list.
- **Include a Create page** (`CreateTaskLoadConfigComponent`) alongside List/Edit/Delete, mirroring
  `CreateTaskDumpConfigComponent` — otherwise no config could ever be added through the UI.

Naming note: keep everything under the `taskloadconfig` package / `TaskLoadConfig*` name, distinct from the
already-existing, unrelated `ui/taskload/TaskLoadComponent.scala` (`TaskLoadPage`, disk→DB import feature) and
its `onLoadClick` sidebar icon — do not reuse or collide with those.

## 1. FE service — `f-core/src/main/scala/com/togger/ui/taskloadconfig/TaskLoadConfigFeService.scala`

Mirrors `TaskDumpConfigFeService`/`TaskDumpConfigFeServiceImpl` exactly (trait + impl,
`ProtectedBackendClient.protectedHttpCall` per endpoint), against the existing `TaskLoadConfigEndpoints`:

```scala
trait TaskLoadConfigFeService {
  def create(dto: CreateTaskLoadConfigReq): TgRespIO[TaskLoadConfigResp]
  def findById(id: UUID): TgRespIO[TaskLoadConfigResp]
  def update(dto: UpdateTaskLoadConfigReq): TgRespIO[TaskLoadConfigResp]
  def deleteById(id: UUID): TgRespIO[TgEmptyBody]
  def findByProjectId(projectId: UUID): TgRespIO[Seq[TaskLoadConfigResp]]
  def findByAccountId(accountId: UUID): TgRespIO[Seq[TaskLoadConfigResp]]
}
```
`TaskLoadConfigFeServiceImpl(securedEndpoint, protectedBackendClient)` builds `new TaskLoadConfigEndpoints(securedEndpoint)`
internally, same as the dump config impl does.

## 2. List page — `ShowAllTaskLoadConfigsComponent.scala`

Mirrors `ShowAllTaskDumpConfigsComponent` exactly: constructor
`(taskLoadConfigService, router, spinnerService, toast, projectId)`, loads via `findByProjectId` into a
`Var[Seq[TaskLoadConfigResp]]` on construction, renders a `TgCard` with header "Task load configs" + a
"Create" button (`router.navigateTo(CreateTaskLoadConfigPage(projectId))`) and a table body.

Table columns: **id, name, dump path, updated at, Action** (adds `name` between id and dump path, vs. dump
config's `id, dump path, updated at, Action`). Action column: same two-button pattern — "Edit"
(`router.route(EditTaskLoadConfigPage(dto.id))`) and "Delete" (`spinnerService.runWithSpinner(taskLoadConfigService.deleteById(dto.id))`
→ `toastBackendMessageCodeBlock { router.route(ShowAllTaskLoadConfigsPage(projectId)) }`), same `TgIcons.editIcon`/`TgIcons.trashIcon`.

## 3. Create page — `CreateTaskLoadConfigComponent.scala`

Mirrors `CreateTaskDumpConfigComponent`'s overall shape (constructor `(taskLoadConfigService, spinnerService,
projectId, toast)`, `TgCard.headerNoFooter` with a `form`/`onSubmit`/`TgHtmlButton.create`), **with one
deviation**: drop the `projectService.findAllMembers` / `ProjectMembersState` / `membersVar` plumbing —
in the dump config file that machinery fetches project members into `renderElement(selectValues: ...)` but
`selectValues` is never actually used in the rendered form, so it's dead code not worth reproducing. This
component doesn't need `ProjectService` at all.

```scala
case class CreateTaskLoadConfigForm(name: String = "", dumpPath: String = "")

class CreateTaskLoadConfigComponent(
  taskLoadConfigService: TaskLoadConfigFeService,
  spinnerService: SpinnerService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime) extends ToastMessages {

  val formState = Var(CreateTaskLoadConfigForm())

  lazy val createSubmitter = Observer[CreateTaskLoadConfigForm] { state =>
    val request = CreateTaskLoadConfigReq(name = state.name, projectId = projectId, dumpPath = state.dumpPath)
    spinnerService.runWithSpinner(taskLoadConfigService.create(request))
      .toastMessageJustMessage("Task load config created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name), errors = EventStream.empty,
    labelValue = "Name", tgInputType = TgInputType.Text)
  private val dumpPathInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.dumpPath), errors = EventStream.empty,
    labelValue = "Dump path (must be an abs path)", tgInputType = TgInputType.Text)

  val element = div(
    justifyCenterFlex,
    TgCard.headerNoFooter(
      header = TgCard.CardHeader.plainText("Create a task load config", TgCard.CardHeaderSize.H3),
      body = TgCard.HtmlCardContent(div(form(
        onSubmit.preventDefault.mapTo(formState.now()) --> createSubmitter,
        nameInput.htmlElement.amend(cls("mb-4")),
        dumpPathInput.htmlElement.amend(cls("mb-4")),
        TgHtmlButton.create(EventStream.fromValue(false))
      )))
    ).element.amend(heightAndWidthFitContent)
  )
}
```

## 4. Edit page — `EditTaskLoadConfigComponent.scala`

Mirrors `EditTaskDumpConfigComponent` exactly, with `name` added to the form and `findById`/`update` calls:

```scala
case class EditTaskLoadConfigForm(
  id: UUID = UUID.randomUUID(),
  projectId: UUID = UUID.randomUUID(),
  accountId: UUID = UUID.randomUUID(),
  name: String = "",
  dumpPath: String = ""
)
```
Constructor `(id, taskLoadConfigService, router, spinnerService, toast)`. On construction, `findById(id)` populates
the form (including `name`); submit builds `UpdateTaskLoadConfigReq(id, name, projectId, dumpPath)` (no
`accountId` — matches the backend contract, which derives it from the session) and calls `update`, toasting
success and re-routing to `EditTaskLoadConfigPage(id)` on completion (same pattern as dump config). Two
inputs (name, dump path) instead of one.

## 5. Routes — `Router.scala` + `TgRoutes.scala`

**`Router.scala`** — add three `PrivatePage` cases next to the `TaskDumpConfig`/`TaskLoad` ones:
```scala
case class ShowAllTaskLoadConfigsPage(projectId: UUID) extends PrivatePage
case class CreateTaskLoadConfigPage(projectId: UUID) extends PrivatePage
case class EditTaskLoadConfigPage(id: UUID) extends PrivatePage
```

**`TgRoutes.scala`** — add three `Route(...)` entries mirroring the `task-dump-config` ones, under a
`task-load-config` path prefix (kept distinct from both `task-dump-config` and the existing `task-load`
prefix used by `TaskLoadPage`):
```scala
Route(
  encode = (page: ShowAllTaskLoadConfigsPage) => page.projectId.toString,
  decode = (projectId: String) => ShowAllTaskLoadConfigsPage(UUID.fromString(projectId)),
  pattern = root / "task-load-config" / "project" / segment[String] / endOfSegments
),
Route(
  encode = (page: CreateTaskLoadConfigPage) => page.projectId.toString,
  decode = (projectId: String) => CreateTaskLoadConfigPage(UUID.fromString(projectId)),
  pattern = root / "task-load-config" / "create" / segment[String] / endOfSegments
),
Route(
  encode = (page: EditTaskLoadConfigPage) => page.id.toString,
  decode = (id: String) => EditTaskLoadConfigPage(UUID.fromString(id)),
  pattern = root / "task-load-config" / "edit" / segment[String] / endOfSegments
),
```

## 6. Rendering + DI — `TgSplitRender.scala`

Add `lazy val taskLoadConfigService = new TaskLoadConfigFeServiceImpl(securedEndpoint, protectedBackendClient)`
next to the existing `taskDumpConfigService` val, and three `case` branches next to the existing
`ShowAllTaskDumpConfigsPage`/`CreateTaskDumpConfigPage`/`EditTaskDumpConfigPage` ones, instantiating the three
new components with the same argument shapes (`router`, `spinnerService`, `toast`, `projectId`/`id` from the
matched page).

## 7. Sidebar wiring — `Sidebar.scala` + `TgIcons.scala`

Add a fourth optional click handler to `SidebarLink` alongside `onDumpClick`/`onConfigClick`/`onStatusClick`/
`onLoadClick`:
```scala
onLoadConfigClick: Option[() => Unit] = None
```
Render it the same way as the other three optional icon buttons (same button styling, appended after the
`onLoadClick` button). Needs a new icon distinct from `editIcon` (already used for `onConfigClick`) — add a
`settingsIcon`/`listIcon` (gear or list glyph, same Heroicons-outline SVG shape as the existing icons) to
`TgIcons.scala`, following the existing `def xIcon(className: String) = svg.svg(...)` pattern.

Wire it in the project loop:
```scala
onLoadConfigClick = Some(() => router.route(ShowAllTaskLoadConfigsPage(project.id)))
```

## Explicitly out of scope

- No changes to the existing `task_dump_config` or `TaskLoadPage`/`TaskLoadComponent` (disk-import) UI/behavior.
- No new automated tests — matches the existing convention (no tests exist for `ui/taskdumpconfig` either);
  verify manually per `CLAUDE.md`.

## Verification

- `sbt frontendMain/fastLinkJS` compiles cleanly.
- `npm run dev`, log in, open a project, click the new sidebar icon:
  - List page loads (empty state renders an empty table, no error).
  - Create a config (name + absolute dump path) → appears in the list.
  - Edit it (change name and/or path) → list reflects the change after navigating back.
  - Delete it → removed from the list.
  - Confirm a relative dump path is rejected server-side (toast shows the backend's `InvalidPath`/`BlankName`
    error message — no new client-side validation is being added, same as dump config today).

## Status: implemented

All of the above was implemented:
- New files: `TaskLoadConfigFeService.scala`, `ShowAllTaskLoadConfigsComponent.scala`,
  `CreateTaskLoadConfigComponent.scala`, `EditTaskLoadConfigComponent.scala` under
  `f-core/src/main/scala/com/togger/ui/taskloadconfig/`.
- `Router.scala`: added `ShowAllTaskLoadConfigsPage`/`CreateTaskLoadConfigPage`/`EditTaskLoadConfigPage`.
- `TgRoutes.scala`: added the three matching `Route`s under `task-load-config/...`.
- `TgSplitRender.scala`: added `taskLoadConfigService` and the three `case` branches.
- `TgIcons.scala`: added `listIcon` (distinct from `editIcon`, already used for the dump config sidebar icon).
- `Sidebar.scala`: added `onLoadConfigClick` to `SidebarLink`, rendered as a fourth icon button, wired to
  `ShowAllTaskLoadConfigsPage(project.id)` in the project loop.

Verified: `sbt frontendCore/compile` and `sbt frontendMain/fastLinkJS` both succeed with no errors.

**Not verified**: manual click-through in the browser (create/edit/delete a config end-to-end, confirm toasts
and validation errors render correctly). A local backend (port 8081) and vite dev server (port 5173) were
already running, but no login credentials for that instance were available to drive the UI — this needs a
manual pass, or credentials handed to Claude to drive via browser automation.
