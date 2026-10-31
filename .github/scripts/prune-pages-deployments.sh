#!/usr/bin/env bash
# Deletes the old production deployments of the Cloudflare Pages project, keeping the newest KEEP of them (the newest one
# is the deployment served on the root domain, so it is always kept).
#
# Needs CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID and PROJECT; KEEP defaults to 10.
set -euo pipefail

KEEP="${KEEP:-10}"
API="https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/pages/projects/${PROJECT}/deployments"

# prints the body of the response, and fails with it when Cloudflare answers with an error
cloudflare() {
  local response status
  response=$(curl -sS -w '\n%{http_code}' -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" "$@")
  status="${response##*$'\n'}"
  if [ "${status:0:1}" != "2" ]; then
    echo "::error::Cloudflare answered $status: ${response%$'\n'*}" >&2
    return 1
  fi
  echo "${response%$'\n'*}"
}

# every page of the production deployments as "<created_on> <id>" lines
DEPLOYMENTS=""
PAGE=1
while true; do
  RESPONSE=$(cloudflare "${API}?env=production&page=${PAGE}")
  DEPLOYMENTS="$DEPLOYMENTS
$(echo "$RESPONSE" | jq -r '.result[] | "\(.created_on) \(.id)"')"
  TOTAL_PAGES=$(echo "$RESPONSE" | jq -r '.result_info.total_pages // 1')
  [ "$PAGE" -lt "$TOTAL_PAGES" ] || break
  PAGE=$((PAGE + 1))
done

# newest first (ISO timestamps sort as text), everything after the first KEEP is old
OLD_IDS=$(echo "$DEPLOYMENTS" | sed '/^$/d' | sort -r | tail -n +$((KEEP + 1)) | cut -d' ' -f2)

DELETED=0
for ID in $OLD_IDS; do
  echo "Deleting production deployment $ID"
  cloudflare -X DELETE "${API}/${ID}?force=true" > /dev/null
  DELETED=$((DELETED + 1))
done
echo "Deleted $DELETED old production deployments, kept the newest $KEEP"
