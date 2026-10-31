#!/usr/bin/env bash
# Prints the release notes of a tag: release-notes/<tag>.md when it exists, otherwise the feat, fix and refactor
# commits since the previous tag.
set -euo pipefail

tag="$1"
file="release-notes/$tag.md"

if [ -f "$file" ]; then
  cat "$file"
  exit 0
fi

previous=$(git describe --tags --abbrev=0 --match 'v*' "$tag^" 2>/dev/null || true)
notes=$(git log "${previous:+$previous..}$tag" --no-merges --pretty='%s' |
  { grep -E '^(feat|fix|refactor)\(' || true; } |
  sed -E 's/^[a-z]+\(([a-z-]+)\): (.*)$/- \2 (\1)/')

echo "${notes:-No notable changes.}"
