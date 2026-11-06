---
name: sync-branch
description: Sync a branch with develop. Fetches and prunes all remotes, fast-forwards develop, deletes the local branches and worktrees whose remote branch is gone, and rebases the given branch on top of develop. Use when the user asks to "sync a branch", "rebase a branch on develop", "update my branch", or "clean up merged branches and worktrees".
---

# Sync Branch Skill

Runs the `sync-branch` recipe of the `justfile`: `just sync-branch [branch]`.

What the recipe does, in order:
1. `git fetch --all --prune`.
2. Fast-forwards `develop` from `origin` (in place with `merge --ff-only` when `develop` is checked out in a worktree).
3. For every local branch whose upstream is `[gone]` (never `develop`): removes its worktree if it has one, then `git branch -D`.
4. Rebases `<branch>` (default: the current branch) on `develop`, in the worktree where it is checked out, or after checking it out in the current directory.

## Step 1 — Branch name

Use the branch the user named. If none was given, the recipe rebases the current branch (it fails on `develop` or a detached HEAD, then ask which branch to rebase).

## Step 2 — Check what will be deleted

The recipe deletes branches and worktrees without asking. Before running it, show the user what it would remove and confirm:

```bash
git fetch --all --prune
git for-each-ref --format='%(refname:short) %(upstream:track)' refs/heads | awk '$2=="[gone]"'
git worktree list
```

Warn when the requested branch itself is `[gone]` (the recipe would delete it and then fail), or when a worktree to be removed may hold uncommitted work (`git worktree remove` refuses those, which stops the recipe).

## Step 3 — Run

```bash
just sync-branch [branch]
```

## Step 4 — Rebase conflicts

If the rebase stops on conflicts, report the conflicting files and ask the user how to proceed. Do not run `git rebase --abort` or `--skip`, and do not force-push; pushing the rebased branch is a separate request (`git push --force-with-lease`, only when asked).
