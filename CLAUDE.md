# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Togger — a Scala full-stack app for logging everyday developer activity, with a lightweight project-management
tool (organizations → projects → tasks → task statuses → log entries) and analytics dashboards. Ships as both a
web app and a Tauri desktop app (same ScalaJS frontend, same http4s backend embedded via GraalVM native image).

## Dev environment

This repo uses `devenv`/`direnv` (`.envrc` → `use devenv`) to provision `sbt`, `nodejs`, GraalVM, and a local
Postgres 13 instance (`services.postgres` in `devenv.nix`). Enter the shell before running anything (`direnv
allow` once, or launch a shell where `.envrc` has been sourced). Useful devenv scripts: `kill-pg`, `reset-pg`
(runs Liquibase drop/update against `liquibase-local.properties`).

Backend tests connect to a real local Postgres database `togger-test` with user `togger_account` /
`togger_password` on port 5432 — hardcoded in `b-05-common/.../db/TgTestTransactor.scala`. Postgres must be
running before running any backend test suite (there is no embedded/mocked DB for most specs, though
`otj-pg-embedded` is a test dependency in some modules). Each spec (`TgDbSpec`/`BusinessTgDbSpec`) truncates its
known tables before and after each test, so specs in `b-auth-app-core` run with `parallelExecution := false`
(shared DB, avoids cross-test interference).

## Common commands

The build runs on sbt 2.x (Scala 3 build definition, project code still Scala 2.13). Things that differ from sbt 1:
`%%` already carries the platform suffix (no `%%%`; JVM-only libraries in a cross project go in `.jvmSettings`),
`test` only re-runs changed/failed tests (use `testFull`), outputs live under `target/out/`. Run scripted commands
through the thin client, `sbt --client "<command>"`, which talks to the sbt server that is already running (delete a
stale `project/target/active.json` if the client reports "server connection closed"). Frontend tests need `node` on
PATH (devenv shell).

There is no root/aggregate sbt project or command aliases in `build.sbt` — target individual projects by their
`lazy val` name (not the `name :=` string), e.g.:

```
sbt backendMain/compile
sbt backendMain/run
sbt "backendAuth/testOnly com.togger.account.AccountServiceTest"
sbt backendDatabase/test
sbt frontendMain/fastLinkJS      # dev ScalaJS build, used by `npm run dev`
sbt frontendMain/fullLinkJS      # prod ScalaJS build, used by `npm run build-*`
sbt scalafmtAll                  # format (see .scalafmt.conf)
```

Project-name → directory map (see `build.sbt`):

| sbt project           | directory              |
|------------------------|-------------------------|
| `frontendCore`         | `f-core`                |
| `frontendMain`         | `f-main`                |
| `backendCore`          | `b-05-core`             |
| `backendCommon`        | `b-05-common`           |
| `backendDatabase`      | `b-05-database`         |
| `backendDiskSync`      | `b-05-disk-sync`        |
| `backendDeliveryHttp4s`| `b-06-delivery-http4s`  |
| `backendMain`          | `b-07-main`             |
| `backendAuth`          | `b-auth-app-core`       |
| `backendAi`            | `b-05-ai`               |
| `backendMcp`           | `b-08-mcp` (Scala 3)    |
| `tgMacros`             | `c-00-tg-macros` (cross)|
| `tgCommon`             | `c-01-common` (cross)   |
| `httpEndpoints`        | `c-03-http-endpoints` (cross) |

Cross-projects (`c-*`) are compiled for both platforms; append `.js`/`.jvm` in `build.sbt` deps, or use e.g.
`sbt tgCommonJS/compile` / `sbt tgCommonJVM/compile` on the CLI.

Frontend (npm/vite):

```
npm run dev            # vite dev server (port 5173, fixed for Tauri); shells out to `sbt print fastLinkOutputDir`
npm run build-web      # production web build
npm run build-desktop  # production build for Tauri (VITE_IS_DESKTOP_MODE=true via .env.desktop)
```

Note: `vite.config.js` runs `sbt` synchronously on startup to resolve the ScalaJS linker output directory
(aliased as `@linkOutputDir`), so `npm run dev`/`build-*` requires a working `sbt` on PATH and will trigger an
sbt run every time vite starts.

Desktop packaging (`justfile`, requires the Tauri CLI and a working sbt/GraalVM setup):

```
just build-tauri-backend <development|prod>  # sbt Universal package -> unpack into src-tauri/java, copy Liquibase changelog, write java/db-mode
just build-tauri-app       # npm run tauri build
just desktop-dev           # full build; the app uses db togger-local-development
just desktop-prod          # full build; the app uses db togger-local
just desktop-dev-clean / desktop-prod-clean   # sbt clean first
```

## Rules for using sbt

- The user always has a manually started `sbt ~frontendMain/fastLinkJS` process running. Do not start another sbt
  JVM (each one is ~1 GB).
- To run tests, compile, or any other sbt command, use the thin client against that running server:
  `sbt --client "<command>"`. To only check that frontend changes compile, you can instead tail the watcher's output.
- Never kill sbt processes (no `pkill -f sbt`): that would also kill the user's watcher. The client needs no cleanup.

## Architecture

**Module layering by prefix:**
- `c-*` — cross-platform (`crossProject(JS, JVM)`) shared code, compiled into both frontend and backend.
  `c-00-tg-macros` → `c-01-common` (DTOs, `TgError`, shared auth types, logging) → `c-03-http-endpoints`
  (Tapir endpoint *definitions* only, no server/client logic — one file per domain, e.g. `TaskEndpoints`,
  `OrganizationEndpoints`).
- `b-*` — JVM-only backend. `b-05-core` holds domain models + repo *traits* (interfaces); `b-05-database`,
  `b-05-common`, `b-auth-app-core` hold Doobie repo *implementations* (`*RepoImpl`) and controllers;
  `b-06-delivery-http4s` and `b-auth-app-core` hold `*Controller`/`*Service` classes that implement the shared
  Tapir endpoints as http4s server logic; `b-07-main` is the executable entry point that wires everything
  together and runs Liquibase/DB bootstrap.
- `f-*` — ScalaJS-only frontend. `f-core` has the actual UI (Laminar components under `ui/`, routing, backend
  HTTP clients under `backendcalls/`); `f-main` is the thin entry point (`FrontendApp`).

**Endpoint-first API contract:** a domain's Tapir endpoint is defined once in `c-03-http-endpoints` (shared),
then given a server implementation in the backend (`*Controller` in `b-06-delivery-http4s`/`b-auth-app-core`)
and consumed by the frontend via an sttp client (`f-core/.../backendcalls`). Changing a request/response shape
means touching the shared DTO in `c-01-common`, the shared endpoint in `c-03-http-endpoints`, the backend
controller, and the frontend caller/component.

**No DI framework — manual constructor wiring.** `TgServer.DependenciesModule` (in `b-07-main`) and
`AuthDependenciesModule` (in `b-auth-app-core`) wire the whole object graph by hand with `lazy val`s; new
services/controllers get added there and to the endpoint list passed to `HttpModule.createServer`.

**AI (internal only):** `TgAiClient` (trait, models and `TgAiError` in `b-05-core`, package `com.togger.ai`) is
implemented by `TgOpenAiClient` in `b-05-ai` using sttp-ai (OpenAI) on an fs2 sttp-client4 backend that
`TgServer.instance` acquires as a `Resource`. `TgAiService` resolves the API key per account through
`TgAiApiKeyProvider` (currently `TgNoApiKeyProvider`; DB-backed keys come later). No controller or endpoints.
`TgAiEndpoints` (enum in `c-01-common`, package `com.togger.ai`) lists every AI endpoint; its int `value` is the id of the
`ai_endpoints` table, which `AiEndpointSeedService` syncs on startup (inserts missing rows only). A new AI endpoint
is a new enum entry with the next free `value`.
`TgAiAction` (`b-05-core`/`b-05-database`, package `com.togger.tgaiaction`) is an account-scoped, unversioned action
(name, prompt, optional `TgPromptTemplate` id, model). It is bound to any number of `TgAiEndpoints` through
`tg_ai_action_endpoint`; `tg_ai_action_default` holds one default action per member, project and endpoint (it must be
bound to that endpoint). Not yet consumed by `MilestoneAiSummaryService`.

**MCP server (`backendMcp`, `b-08-mcp`):** a read-only MCP server built with Chimp, in its own **Scala 3.9** module that
depends on no other Togger project (Chimp's tapir/sttp are compiled with Scala 3.9, and the module overrides the
build-wide `dependencyOverrides` for that reason). It is a thin HTTP client of the backend: `McpApp` logs in as the `mcp`
account (retrying while the backend starts), then serves 16 tools (`tools/*Tools.scala`, one class per domain) on
`http://127.0.0.1:8090/mcp` by calling the existing endpoints with the session as bearer token. Tools return the
backend's `body` JSON as text. Config is env only: `TOGGER_BACKEND_URL` (default `http://localhost:8081`),
`TOGGER_MCP_HOST`, `TOGGER_MCP_PORT`, and `TOGGER_MCP_API_KEY` (if set, every MCP call needs it as bearer token).
The `mcp` account (`mcp@email.com`, admin type, member of every organization and project) is ensured on every backend
start by `McpAccountSeedService` in `b-07-main`, so projects created later get it at the next backend restart. `McpApp desktop` (first arg, like the
backend's `AppEnv`) also runs `CheckDesktopAppRunning` and exits when the Togger desktop app is no longer running; without
the arg it runs until stopped. Register
with `claude mcp add --transport http togger http://localhost:8090/mcp`. Run with `sbt --client "backendMcp/run"` (this
blocks the shared sbt server; alternatively run `com.togger.mcp.McpApp` from the exported classpath).

**Auth:** bearer-token sessions (`AccountSessionRepo`), Tapir endpoints authenticated via `SecuredEndpoint`
(wraps the shared `baseEndpoint` with `securityIn(auth.bearer[String]())` + `SecurityLogic`, which has separate
JVM (`SecurityLogicJvm`) and JS (`SecurityLogicJs`) implementations). Passwords hashed with `scalapass`.

**Repo pattern:** each domain has a `*Repo` trait (interface, in `b-05-core`) plus a `*RepoImpl` (Doobie
`ConnectionIO`, in `b-05-database`/`b-auth-app-core`), each exposing its table name as a constant (e.g.
`AccountRepoImpl.ACCOUNT_TABLE`) that test specs use for cleanup.

**DB schema:** managed by Liquibase, changelog at `b-05-database/src/main/resources/liquibase/changelog.yaml`
(gitignored contents pattern relaxed — see `.gitignore` note in git log). On desktop app startup,
`BackendApp.setupDb` checks whether the `togger_account` DB role exists; if not, it creates the DB/role, runs
Liquibase, and seeds an admin account (`CreateAdminAccount`).

**Web vs. desktop split**, both at build time and runtime:
- Runtime: `AppEnv` (`web`/`desktop`, first CLI arg to `BackendApp`; third arg is the `DbMode`, `development` → db `togger-local-development` (default) or `prod` → `togger-local`) selects `application-web.conf` /
  `application-desktop.conf` (`AppEnvSelector`), which controls log archiving and whether the backend runs
  a foreground desktop-lifecycle watcher (`CheckDesktopAppRunning`) vs. `IO.never` for web.
  On the frontend, `VITE_IS_DESKTOP_MODE` (set via `.env`/`.env.desktop`/`.env.web`) picks between
  `WebFrontendApp` and `DesktopFrontendApp` in `FrontendApp`, which differ in router origin
  (`http://localhost:5173` vs `tauri://localhost`) and startup delay.
- Packaging: the desktop build packages the http4s backend as a GraalVM native/Universal artifact and bundles
  it as a sidecar inside the Tauri app (`src-tauri/`); see the `justfile` targets above.

**Frontend routing/rendering:** Waypoint router (`TgRoutes`/`TgRouterImpl` in `f-core/routes`) defines pages as
ADT cases (`Pages.scala`); `TgSplitRender` maps the router's signal to the Laminar component for the current
page.

## Code style

Governed by `.scalafmt.conf` (2-space indent, 120 col) and `CONVENTIONS.md`. Key points from `CONVENTIONS.md`
worth following when writing Scala in this repo:
- Favor early returns, small single-purpose functions, and self-explanatory code over comments.
- Functional style: pure functions, immutability, composition (Cats/Cats Effect idioms already used throughout).
- Match existing styling/component patterns in a file rather than introducing new ones (especially in `f-core`
  UI components).

## Current branch context

`feature/ids-long-to-uuids` is an in-progress migration of primary key types from `Long` to `UUID` (see
`uuid7` dependency and `b-05-common/.../db/Util.scala`). Expect some domains already on UUID ids and others
still on `Long` mid-migration — check the specific repo/DTO/endpoint before assuming either type.
