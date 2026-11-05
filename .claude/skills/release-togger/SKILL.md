---
name: release-togger
description: Release a new Togger desktop version through a release PR. Asks for the version based on the commits since the last tag, drafts release notes for approval, commits the notes and the version bump separately, pushes without the pre-push hook, opens the PR, waits for its checks, merges it and then pushes the git tag that starts the release-desktop workflow. Use when the user asks to "release", "cut a release", "publish a new version" or "tag a version".
---

# Release Skill

Releases are driven by the `v*` tag: pushing it starts `.github/workflows/release-desktop.yml` (macOS build, publishes the GitHub release; the Tauri updater reads `latest.json` from the latest release). The workflow fails if the tag does not match `src-tauri/tauri.conf.json`'s version. Its release body is `release-notes/<tag>.md` when that file exists (`.github/scripts/release-notes.sh`), otherwise a list of the feat/fix/refactor commits.

The release goes through a PR into `develop`, and the **tag is pushed only after the PR is merged**, on the merge commit. Never tag or push to `develop` directly.

The bump and the tag are separate recipes: `just release X.Y.Z` bumps and commits (no tag), `just release-tag X.Y.Z` tags after the merge.

## Step 1 — Preconditions

```bash
git branch --show-current   # expected: develop
git status --porcelain      # must be empty
git fetch origin --tags
git status -sb              # must be up to date with origin/develop (not ahead, not behind)
git describe --tags --abbrev=0
```

Stop and tell the user if the tree is dirty, the branch is not `develop`, or it differs from the remote. Never stash or reset on their behalf.

## Step 2 — Ask for the version

```bash
git log --oneline --no-merges "$(git describe --tags --abbrev=0)"..HEAD
```

Show the commits since the last tag and ask the user for the version (`MAJOR.MINOR.PATCH`, no `v`) with `AskUserQuestion`. Offer a recommendation based on the commits while the app is 0.x: `feat` → next minor, only fixes/chores → next patch, plus the other bump as options. Check the tag does not exist yet (`git rev-parse "v<version>"` must fail).

## Step 3 — Draft the release notes and get approval

Read the previous notes (`release-notes/v*.md`) and match their style: user-facing sections (e.g. `## Log entries`) with short bullets describing what changed for the user, not commit subjects. Read the diffs or PRs behind the `feat`/`fix` commits when a subject is unclear. Skip chores, docs and internal tooling.

Show the full draft to the user and ask for approval. Apply their changes and show it again until they approve. Do not write the file before approval.

## Step 4 — Branch and the two commits

The branch is named `release/X.Y.Z` after the version chosen in Step 2 (for example `release/0.2.2`, no `v`); `X.Y.Z` stands for that version below.

```bash
git switch -c release/X.Y.Z
```

Commit 1 — the release notes, `release-notes/v<version>.md` (the approved text):

```bash
git add release-notes/v<version>.md
git commit -m "docs(project): add vX.Y.Z release notes"
```

Commit 2 — the version bump (rewrites `src-tauri/tauri.conf.json`, `Cargo.toml` and `Cargo.lock`, validates the version, needs a clean tree and an unused tag, commits `chore(project): release X.Y.Z`):

```bash
just release X.Y.Z
```

Verify `git show --stat HEAD` touches only those three files. If `cargo update --offline` fails, report it; do not hand-edit around it silently. The recipe writes its own commit message, leave it as it is (no amend, no attribution line).

## Step 5 — Push without the pre-push hook

```bash
SKIP_PRE_PUSH=1 git push -u origin release/X.Y.Z
```

The release push **always** skips the pre-push hook (`SKIP_PRE_PUSH=1`): the same checks run on the PR in Step 7, and the release must not wait for the local test run.

## Step 6 — Open the PR

Follow the PR conventions of CLAUDE.md: title `Release <version>` (no prefix), assignee `@me`, project "Togger", milestone "First working version - team", labels `🧹 chore` and `infra`. The body lists the release notes and ends with the Claude Code attribution line.

```bash
gh pr create --base develop --title "Release <version>" --body-file <body> \
  --assignee @me --project "Togger" --milestone "First working version - team" \
  --label "🧹 chore" --label infra
```

## Step 7 — Wait for the checks

```bash
gh pr checks <pr> --watch
```

If a check fails, show which one and its log (`gh run view --log-failed`), and ask the user how to proceed. Do not merge with failing or pending checks.

## Step 8 — Merge the PR

Merging is outward-facing: ask the user for confirmation unless they already said to merge without asking. The repo history uses merge commits:

```bash
gh pr merge <pr> --merge
git switch develop
git pull --ff-only
```

Delete the local `release/X.Y.Z` branch afterwards (`git branch -d`).

## Step 9 — Tag and push the tag

Make sure `HEAD` is the merge commit and `src-tauri/tauri.conf.json` has the released version, then:

```bash
just release-tag <version>
git push origin v<version>
```

This publishes a release, so only do it once the user has said to go ahead with the release (the confirmation in Step 8 may cover it if it said so explicitly).

## Step 10 — Follow the workflow

```bash
gh run list --workflow release-desktop.yml --limit 1
gh run watch <run-id>        # optional; the build is slow
```

Report the run URL, and the release URL (`gh release view v<version> --json url -q .url`) once published.

## If something goes wrong

- Before the tag is pushed, the release can be abandoned: close the PR and delete the branch, with the user's approval.
- After the tag is pushed, never move or reuse it; release a new patch version instead.
