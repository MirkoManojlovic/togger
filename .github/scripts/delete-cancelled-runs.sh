#!/usr/bin/env bash
# Deletes the runs of a workflow on a branch that only pollute the history: by default the cancelled ones. The
# concurrency groups of the workflows cancel the runs that a newer one supersedes, and they stay in the history; the run
# that survived calls this at its end.
#
# Needs GH_TOKEN (with actions: write), REPO, WORKFLOW (name or file) and BRANCH. STATUSES is a space separated list of
# the statuses to delete, "cancelled" by default. A failure to delete (a pull request from a fork gets a read-only
# token) is only a warning.
set -euo pipefail

for STATUS in ${STATUSES:-cancelled}; do
  IDS=$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch "$BRANCH" --status "$STATUS" --limit 100 \
    --json databaseId --jq '.[].databaseId')

  for ID in $IDS; do
    echo "Deleting the $STATUS run $ID of $WORKFLOW on $BRANCH"
    gh api -X DELETE "repos/$REPO/actions/runs/$ID" --silent || echo "::warning::Could not delete the run $ID"
  done
done
