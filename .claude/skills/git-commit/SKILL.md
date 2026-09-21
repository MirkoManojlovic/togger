---
name: git-commit
description: Group all pending git changes (staged, unstaged, and untracked) into multiple focused commits by related class/module, with conventional-prefixed imperative messages. Use when the user asks to "commit everything", "split my changes into commits", "commit these files", or otherwise wants their working tree committed in logically grouped commits rather than one big commit.
---

# Git Commit Skill

Turns the current working tree changes into a small set of focused, well-grouped commits — never one giant commit, never one commit per file.

## Step 1 — Inventory every change

Run these to see the full picture (staged, unstaged, and untracked):

```bash
git status
git diff
git diff --cached
```

For untracked files, `Read` their contents to understand what they add.

## Step 2 — Group changes by relationship

Group files that touch the same or related classes/modules/feature into one commit. Signals to group together:

- Same class/trait/object, or a class and its direct callers/tests.
- A production file and its corresponding test file for the same change.
- Files that only make sense together (e.g. a new trait extracted + the files updated to use it).

Keep unrelated changes in separate commits, even if they were edited in the same sitting (e.g. a build.sbt bump and a bug fix belong in different commits).

## Step 3 — Choose a type prefix per commit

Based on the dominant nature of that group's change pick the first prefix. This is called a <type>

- `feat` — new code/functionality added
- `refactor` — existing code restructured/changed, same behavior
- `fix` — a bug was fixed
- `test` — changes to test code only
- `chore` — build.sbt, shell scripts, CI config, tooling
- `style` — formatting, renames, no behavior change
- `docs` — documentation only

Based on where the changes are found in the codebase, choose a second prefix. This is called a <place>
- `common` - changes found in the folder starting with c-0 (examples: `c-00-tg-macros`, `c-01-common`, `c-03-http-endpoints`)
- `backend-auth` - changes found in the folder `b-auth-app-core`
- `backend` - changes found in the folder starting with b-0 (examples: `b-05-common`, `b-05-core`, `b-05-database`)
- `frontend` - changes found in the folder starting with f- (examples: `f-core`, `f-main`)
- `project` - changes made to the rest of the files

## Step 4 — Draft messages

Format: `<type>(<place>): <short imperative summary>`. No period, no scope tags, no body unless truly necessary. Examples:

```
fix(backend): remove debug controller
feat(backend): introduce new openai endpoint pair
refactor(frontend): show day in log entry component
chore(project): add passing of changelog file to bin scripts
fix(backend): postgres18 detects deadlocks run auth tests before backend tests
feat(common): add orderByDescLimitOne method
refactor(common): add LogEntryDtoMapper
docs(project): add link version claude plan
```

## Step 5 — Present the plan and wait for approval

Before touching git, show the user the proposed commits as a numbered list: files included, chosen type prefix, and message. Do not run any `git add` or `git commit` until the user confirms or adjusts the plan.

## Step 6 — Commit each group

Once approved, for each group, stage exactly its files (never `git add -A`/`git add .`) and commit with the message via a heredoc:

```bash
git add <file1> <file2>
git commit -m "$(cat <<'EOF'
<type>: <message>
EOF
)"
```

Process groups sequentially so each commit only contains its intended files. After the last commit, run `git status` and `git log --oneline -n <count>` to confirm the working tree is clean and the commits match the plan.

## Rules

- Never use `git add -A` or `git add .` — always name files explicitly per group.
- Never amend, force-push, or skip hooks (`--no-verify`).
- Never invent changes — only commit what's actually present in the diff/status.
- If a file's changes are unrelated to any other pending change, it gets its own commit rather than being forced into a group.
