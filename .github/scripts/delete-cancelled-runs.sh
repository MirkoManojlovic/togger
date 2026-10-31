#!/usr/bin/env bash
# Deletes the cancelled runs of a workflow on a branch. The concurrency groups of the workflows cancel the runs that a
# newer one supersedes, and they stay in the history; the run that survived calls this at its end.
#
# Needs GH_TOKEN (with actions: write), REPO, WORKFLOW (name or file) and BRANCH. A failure to delete (a pull request
# from a fork gets a read-only token) is only a warning.
set -euo pipefail

IDS=$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch "$BRANCH" --status cancelled --limit 100 \
  --json databaseId --jq '.[].databaseId')

for ID in $IDS; do
  echo "Deleting the cancelled run $ID of $WORKFLOW on $BRANCH"
  gh api -X DELETE "repos/$REPO/actions/runs/$ID" --silent || echo "::warning::Could not delete the run $ID"
done
