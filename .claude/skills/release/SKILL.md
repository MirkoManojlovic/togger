---
name: release
description: Create a new Togger release - write the release notes, bump the version in the Tauri files and Cargo.lock, open a release PR to develop, and tag after it merges. Use when the user asks to "create a release", "cut a new version", "release 0.x.y" or "tag a release".
---

# Release Skill

`develop` is protected (the "PR Checks" status check is required), so a release never goes straight to `develop`: it
is a `release/X.Y.Z` branch and a PR. The tag is pushed only **after** the PR is merged, because the `v*` tag starts
`.github/workflows/release-desktop.yml` (desktop build + GitHub release) and must point at a commit on `develop`.

## Step 1 — Pick the version

```bash
git fetch origin --tags
git describe --tags --abbrev=0 origin/develop        # last release, e.g. v0.2.0
git log --oneline <last-tag>..origin/develop
```

Patch (`0.2.0` → `0.2.1`) for fixes and small refactors, minor (`0.2.0` → `0.3.0`) for new features. If unsure, give a
recommendation and ask. Tags are `vX.Y.Z`, the files use `X.Y.Z`.

## Step 2 — Branch

Branch from the latest `origin/develop`; check first that `release/X.Y.Z` does not already exist (locally, on the
remote, or in a worktree: `git branch -a`, `git worktree list`). If it exists, review and reuse it instead of creating
another one.

```bash
git switch -c release/X.Y.Z origin/develop
```

## Step 3 — Release notes (commit 1)

Write `release-notes/vX.Y.Z.md`. Read `release-notes/` for the latest file and copy its style: `##` sections
(e.g. "Milestone steps", "Fixes", "Under the hood"), bullets in plain user-facing language, bold for feature names, no
PR numbers. Base it on `git log <last-tag>..origin/develop` and the merged PRs (`gh pr list --state merged`); look at
the diffs when a commit message is not enough. Mention database migrations and anything users notice on startup.

```bash
git add release-notes/vX.Y.Z.md
git commit -m "docs(project): add vX.Y.Z release notes"
```

## Step 4 — Version bump (commit 2)

Change the version (old → new) in exactly these files:

- `src-tauri/Cargo.toml` (`version = "…"` under `[package]`)
- `src-tauri/tauri.conf.json` (`"version"`)
- `src-tauri/Cargo.lock` (the `app` package) — refresh it with cargo, do not edit it by hand:

```bash
cd src-tauri && cargo update -p app --offline
```

`cargo` is often not on PATH outside the devenv shell. If so, use one from the nix store:
`find /nix/store -maxdepth 3 -name cargo -path '*-cargo-*/bin/*'` and call it by full path. The diff of `Cargo.lock`
must be only the one `app` version line. `package.json` stays at `0.0.0`.

```bash
git add src-tauri/Cargo.toml src-tauri/Cargo.lock src-tauri/tauri.conf.json
git commit -m "chore(project): release X.Y.Z"
```

Commit messages end with the attribution line from the session's system reminder.

## Step 5 — Push and PR

The `pre-push` hook runs the whole test suite against the shared sbt server and takes minutes, so for a release push
always skip it (this applies to every push in this skill, including deleting a tag):

```bash
SKIP_PRE_PUSH=1 git push -u origin release/X.Y.Z
gh pr create --base develop --head release/X.Y.Z --title "Release X.Y.Z" \
  --assignee @me --label "🧹 chore" --label infra \
  --milestone "First working version - team" --project Togger --body "<body>"
```

The body says what the release contains in a sentence or two and ends with the Claude Code attribution line. Give the
user the PR URL.

## Step 6 — Tag after the merge

Only when the user confirms the PR is merged:

```bash
git switch develop && git pull --ff-only
git tag vX.Y.Z                       # lightweight tag on the develop commit with the release
SKIP_PRE_PUSH=1 git push origin vX.Y.Z
```

Tag the `chore(project): release X.Y.Z` commit, or the merge commit when the PR was merged with a merge commit; with a
squash merge, tag the squash commit. Pushing the tag starts the release workflow: check it with
`gh run list --workflow release-desktop.yml --limit 3` and `gh release list`.

## Rules

- Never push to `develop` directly and never bypass the branch protection.
- Never push the tag before the PR is merged. If a tag was pushed too early, delete it
  (`SKIP_PRE_PUSH=1 git push origin --delete vX.Y.Z` and `git tag -d vX.Y.Z`) after the user agrees, and check that the
  workflow did not create a release.
- Do not kill sbt processes. If a push hangs on the pre-push hook, stop that push and retry with `SKIP_PRE_PUSH=1`.
