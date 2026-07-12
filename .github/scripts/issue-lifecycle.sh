#!/usr/bin/env bash
set -euo pipefail

REPO="${REPO:?REPO is required}"

ensure_labels() {
  gh label create "under-review" \
    --description "Implementation PR is open" \
    --color "FBCA04" \
    --force \
    --repo "$REPO"
  gh label create "pending-release" \
    --description "Merged to main; awaiting release" \
    --color "F9D0C4" \
    --force \
    --repo "$REPO"
  gh label create "released" \
    --description "Shipped in a tagged release" \
    --color "0E8A16" \
    --force \
    --repo "$REPO"
}

is_release_please_pr() {
  local title="$1"
  local head_ref="$2"

  if [[ "$head_ref" == *release-please* ]]; then
    return 0
  fi
  if [[ "$title" == chore*release* ]]; then
    return 0
  fi
  return 1
}

extract_issues() {
  local body="$1"
  local section

  section=$(echo "$body" | awk '/^## Issues resolved/{found=1; next} found && /^## /{exit} found{print}')
  if [ -z "$section" ]; then
    section="$body"
  fi
  echo "$section" | grep -oE '(Refs?|References)\s+#[0-9]+' | grep -oE '[0-9]+' | sort -un
}

add_under_review() {
  local issue="$1"
  local pr_url="$2"
  local add_comment="${3:-true}"

  gh issue edit "$issue" \
    --add-label "under-review" \
    --repo "$REPO"
  if [ "$add_comment" = "true" ]; then
    gh issue comment "$issue" \
      --body "Implementation PR opened: ${pr_url}" \
      --repo "$REPO"
  fi
}

remove_under_review() {
  local issue="$1"

  gh issue edit "$issue" \
    --remove-label "under-review" \
    --repo "$REPO" || true
}

move_to_pending_release() {
  local issue="$1"
  local pr_url="$2"

  gh issue edit "$issue" \
    --remove-label "under-review" \
    --add-label "pending-release" \
    --repo "$REPO"
  gh issue comment "$issue" \
    --body "PR merged (${pr_url}). Issue is pending the next release." \
    --repo "$REPO"
}

close_released_issues() {
  local release_tag="$1"
  local issue

  while IFS= read -r issue; do
    [ -z "$issue" ] && continue
    gh issue edit "$issue" \
      --remove-label "pending-release" \
      --add-label "released" \
      --repo "$REPO"
    gh issue close "$issue" --repo "$REPO"
    gh issue comment "$issue" \
      --body "Released in ${release_tag}." \
      --repo "$REPO"
  done < <(gh issue list \
    --label "pending-release" \
    --state open \
    --json number \
    --jq '.[].number' \
    --repo "$REPO")
}

process_pr_issues() {
  local action="$1"
  local body="$2"
  local pr_url="$3"
  local add_comment="${4:-true}"
  local issue

  while IFS= read -r issue; do
    [ -z "$issue" ] && continue
    case "$action" in
      under-review) add_under_review "$issue" "$pr_url" "$add_comment" ;;
      pending-release) move_to_pending_release "$issue" "$pr_url" ;;
      cleanup-unmerged) remove_under_review "$issue" ;;
      *) echo "Unknown action: $action" >&2; return 1 ;;
    esac
  done < <(extract_issues "$body")
}
