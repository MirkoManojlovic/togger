# Task Load Change 01 (Frontend): Project Mapping Prompt

## Context

`task_load_change_01.md` implemented the backend half of this change: `POST /project/dump/load/{project-id}`
now resolves the dump's project id (read from `project.json` at the given `dumpPath`) against the
`project_mapping` table before merging anything in. `TaskLoadResp` (`c-01-common/.../dto/TaskLoadResp.scala`)
gained an additive `mappingRequired: Option[TaskLoadMappingRequiredResp]` field:

```scala
case class TaskLoadMappingRequiredResp(dumpProjectId: UUID, dumpProjectName: String)

case class TaskLoadResp(
  projectId: UUID,
  dumpPath: String,
  mappingRequired: Option[TaskLoadMappingRequiredResp], // Some(...) => nothing was loaded, taskCount = 0, results = Nil
  taskCount: Int,
  results: List[TaskLoadTaskResultResp]
)
```

`ProjectMappingConflict` and `DumpManifestMissing` (the other two new failure modes) are plain `ProjectError`
cases and already surface for free as error toasts — every existing `TgRespIO` call site in this codebase goes
through `ToastMessages`' `TgRespToastSyntaxIO`, whose error branch always calls `toast.error(error)`
(`f-core/.../toast/ToastMessages.scala:82`, `:90`, `:98`, `:106`) regardless of which `TgError` was returned. No
frontend code needs to special-case those two.

`mappingRequired` is different: it's a **success** response (not a `TgJsonFailure`), carrying data
(`dumpProjectId`, `dumpProjectName`) the frontend needs to act on, so `TaskLoadComponent` needs new logic to
notice it and let the user create the missing mapping without leaving the page.

The existing manual project-mapping CRUD UI (`ShowProjectMappingComponent`/`CreateProjectMappingComponent`,
reached via the sidebar's "Project mapping" link, routed at `ShowProjectMappingPage`/`CreateProjectMappingPage`)
is untouched by this plan — it stays as the general-purpose way to view/edit/delete a project's mapping.

## Decisions confirmed with the user

- **Inline, not a redirect.** When `mappingRequired` comes back, `TaskLoadComponent` shows a small panel in
  place (dump project name + id, "Create mapping & continue" button) rather than navigating to the existing
  `CreateProjectMappingPage` form. Since the response already carries both `dumpProjectId` and
  `dumpProjectName`, there's nothing left for the user to type — a full navigate-away/type-a-UUID/navigate-back
  round trip would be pure friction.
- **Auto-retry.** Clicking "Create mapping & continue" creates the mapping via the existing
  `ProjectMappingFeService.create` call and, on success, immediately re-issues the load with the same
  `dumpPath` the user already entered, replacing the toast-driven "click Load again" step with one action.

## 1. `TaskLoadComponent` — new dependency and state

File: `f-core/src/main/scala/com/togger/ui/taskload/TaskLoadComponent.scala`

Add `projectMappingService: ProjectMappingFeService` to the constructor (all other params unchanged):

```scala
class TaskLoadComponent(
  taskDumpService: TaskDumpFeService,
  taskLoadConfigService: TaskLoadConfigFeService,
  projectMappingService: ProjectMappingFeService,
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {
```

New state, alongside the existing `formState`/`resultsVar`/`configsVar`:

```scala
private val mappingRequiredVar = Var(Option.empty[TaskLoadMappingRequiredResp])
```

## 2. Split the load call's success handling

Today `loadSubmitter` uses `toastMessageDto` (`ToastMessages.scala:95`), which unconditionally shows a success
toast built from the DTO. That's wrong once a `mappingRequired` response can come back with zero tasks loaded —
so switch to `toastJustErrors` (`ToastMessages.scala:71`, still shows the error toast automatically, but leaves
the success side effect entirely to the caller) and branch there:

```scala
private def summaryMessage(dto: TaskLoadResp): String = {
  val counts = dto.results.groupBy(_.status).view.mapValues(_.size).toMap
  s"Loaded ${dto.taskCount} task(s): " +
    s"${counts.getOrElse(TaskLoadStatusResp.Inserted, 0)} inserted, " +
    s"${counts.getOrElse(TaskLoadStatusResp.Merged, 0)} merged, " +
    s"${counts.getOrElse(TaskLoadStatusResp.Unchanged, 0)} unchanged, " +
    s"${counts.getOrElse(TaskLoadStatusResp.Malformed, 0) + counts.getOrElse(TaskLoadStatusResp.Failed, 0)} failed"
}

private def handleLoadResponse(dto: TaskLoadResp): Unit =
  dto.mappingRequired match {
    case Some(info) =>
      mappingRequiredVar.set(Some(info))
      resultsVar.set(Seq.empty)
    case None =>
      mappingRequiredVar.set(None)
      resultsVar.set(dto.results)
      toast.success(summaryMessage(dto))
  }

private def load(dumpPath: String): Unit =
  spinnerService
    .runWithSpinner(taskDumpService.loadTasks(projectId, dumpPath))
    .toastJustErrors(())(handleLoadResponse)
    .unsafeRunAndForget()

private lazy val loadSubmitter = Observer[TaskLoadForm](form => load(form.dumpPath))
```

(`summaryMessage` is the same string `loadSubmitter` already builds today — just extracted so both the normal
path and, if ever needed, other callers can reuse it. `toast.success`/`toast.error` come from `Toast`, already
mixed in via `ToastMessages`.)

## 3. Create-mapping-and-retry action

```scala
private def createMappingAndRetry(info: TaskLoadMappingRequiredResp): Unit = {
  val request = CreateProjectMappingReq(projectIdDb = projectId, projectIdDump = info.dumpProjectId)
  spinnerService
    .runWithSpinner(projectMappingService.create(request))
    .toastMessageFn("Project mapping created")(_ => load(formState.now().dumpPath))
    .unsafeRunAndForget()
}
```

Reuses `load` from §2, so a successful create feeds straight back into the same `handleLoadResponse` branching
(normally resolving to `None` this time and populating the results table, per the "auto-retry" decision).

## 4. Mapping-required panel

New render block, alongside the existing "Configured paths" / "Load tasks from disk" / results table:

```scala
private def mappingRequiredPanel(info: TaskLoadMappingRequiredResp) =
  TgCard
    .headerNoFooter(
      header = TgCard.CardHeader.plainText("Project mapping required", TgCard.CardHeaderSize.H3),
      body = TgCard.HtmlCardContent(
        div(
          p(
            cls("mb-4"),
            s"""No project mapping found for dump project "${info.dumpProjectName}" (${info.dumpProjectId})."""
          ),
          TgHtmlButton
            .extraSmall("Create mapping & continue")
            .amend(onClick.mapToUnit --> (_ => createMappingAndRetry(info)))
        )
      )
    )
    .element
```

Wired into `element` with `child.maybe <-- mappingRequiredVar.signal.map(_.map(mappingRequiredPanel))`, placed
between the "Load tasks from disk" card and the results table (`TgHtmlButton.extraSmall` — same plain
`typ("button")` factory `ShowProjectMappingComponent`'s "Create" button already uses, so no accidental form
submission even though it sits near the load `form(...)`).

New imports needed in this file: `com.togger.dto.projectmapping.CreateProjectMappingReq`,
`com.togger.dto.TaskLoadMappingRequiredResp`, `com.togger.ui.projectmapping.ProjectMappingFeService`.

## 5. Wiring

File: `f-core/src/main/scala/com/togger/TgSplitRender.scala`

`projectMappingService` is already an in-scope `lazy val` in this file (used by the `ShowProjectMappingPage`/
`CreateProjectMappingPage`/`EditProjectMappingPage` cases at lines 294-317) — just add it to the existing
`TaskLoadPage` construction:

```scala
case TaskLoadPage(projectId: UUID) =>
  new TaskLoadComponent(
    taskDumpService,
    taskLoadConfigService,
    projectMappingService,
    spinnerService,
    toast,
    projectId
  ).element
```

No routing changes (`Router.scala`/`TgRoutes.scala`/`Pages.scala` untouched), no changes to
`ShowProjectMappingComponent`/`CreateProjectMappingComponent`/`ProjectMappingFeService`, no changes to
`Sidebar.scala`.

## Manual verification

No existing test coverage exists for `f-core` UI components (only 6 test files in the whole module, none for
any `ui/*` component) — this pass follows that convention; verify by running the app (`npm run dev`) instead:

1. Configure a `TaskDumpConfig` on project A, dump it, note the resulting folder path.
2. On a **different** project B (no `project_mapping` row for it), go to "Load tasks from disk"
   (`TaskLoadPage(B)`), paste project A's dump folder path, submit.
3. Confirm the "Project mapping required" panel appears showing project A's name/id, and the results table
   stays empty.
4. Click "Create mapping & continue"; confirm a "Project mapping created" toast, the panel disappears, and the
   results table populates with the loaded tasks (spot-check via `ShowProjectMappingPage(B)` that the mapping
   row now exists too).
5. Submit the same load again (mapping now resolved); confirm it goes straight to the results table with no
   mapping panel.
6. Trigger the conflict case (create a second, unrelated dump/project, load it into project B, which already
   has a mapping to project A's dump id) and confirm a `ProjectMappingConflict` error toast appears with no UI
   crash.

## Explicitly out of scope

- Any change to `ShowProjectMappingComponent`, `CreateProjectMappingComponent`, `ProjectMappingFeService`,
  routing (`Router.scala`/`TgRoutes.scala`/`Pages.scala`), or `Sidebar.scala` — the standalone mapping
  management page keeps working exactly as it does today.
- Any change to `TaskLoadConfigFeService`/the "Configured paths" table in `TaskLoadComponent` — unaffected by
  this change.
- Automated frontend tests — matches this module's existing lack of UI component test coverage; verification is
  manual (§ above).
