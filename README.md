# Togger

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
