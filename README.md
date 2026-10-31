# Togger

[![Preview](https://img.shields.io/badge/preview-develop-blue)](https://togger-preview.pages.dev)

Togger is an app for logging everyday developer activity. It contains a lightweight project management tool
(organizations, projects, tasks, task statuses and log entries) and dashboards for analytics.

It is written in Scala and ships as a web app and as a macOS desktop app (Tauri).

## Install

The desktop app is available for macOS on Apple Silicon.

1. Download `Togger_<version>_aarch64.dmg` from the [latest release](../../releases/latest).
2. Open the DMG and drag **Togger** into the **Applications** folder.
3. The app is not signed with an Apple Developer ID, so macOS marks it as "damaged" when it is downloaded in a
   browser. Remove the quarantine flag once, before the first start:

   ```
   xattr -cr /Applications/Togger.app
   ```

4. Open Togger from the Applications folder.

Later versions are installed from inside the app, in **Settings → Updates**, and do not need the command again.

### Requirements

- PostgreSQL running locally on the default port, for example [Postgres.app](https://postgresapp.com/). On the first start
  the app creates its own database and account.
- Java 21 or newer.

## Development

The development environment is provisioned with [devenv](https://devenv.sh) and `direnv`. See
[CLAUDE.md](CLAUDE.md) for the project layout, the sbt and npm commands and the architecture.

## Frontend preview

The frontend can run without a backend, with demo data kept in the browser's localStorage:

```
npm run dev-preview     # dev server
npm run build-preview   # static build
```

Log in with `admin@email.com` / `A!good_1_password_is_good` (or `demo@togger.preview` / `demo`). Data is keyed by the build id (git short hash, `-dirty` with uncommitted
changes), so every new build starts with fresh demo data. Export and import are not available in this mode.
"Reset preview data" in the Settings menu restores the demo data.

### Develop preview

Every push to `develop` (a merged pull request) builds the preview again and publishes it to the stable URL
https://togger-preview.pages.dev (workflow `Develop Preview`, same Cloudflare setup as below). The URL stays
the same, only the content and the build id change (every deployment also keeps its own `<hash>.togger-preview.pages.dev`
URL, which is not the one to share); a new build id starts with fresh demo data.

### Pull request previews

A pull request with the label `👀 preview` gets a preview URL, posted as a comment on the pull request (adding the
label starts the build, every later push builds again; removing the label does not delete the preview, closing the pull
request does). `PR Preview
Build` builds the frontend in preview mode (build id = the head commit of the pull request) and `PR Preview Deploy`
publishes it to Cloudflare Pages as `pr-<number>`. One time setup: a Cloudflare Pages project `togger-preview`, the
repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`, and both workflows on the default branch (a
`workflow_run` workflow only runs from there). `PR Preview Cleanup` deletes the deployments of a pull request and edits
the comment when the pull request is closed or merged; it needs the same secrets.

## Releasing

```
just release 0.2.0
git push origin develop v0.2.0
```

`just release` sets the version in `src-tauri/tauri.conf.json`, `src-tauri/Cargo.toml` and `src-tauri/Cargo.lock`,
commits and tags `v0.2.0`. Pushing the tag starts the release workflow, which builds, signs and publishes the app.
The release notes come from `release-notes/v0.2.0.md` when that file exists, otherwise from the `feat`, `fix` and
`refactor` commits since the previous tag.

## License

[MIT](LICENSE)
