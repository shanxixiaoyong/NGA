#!/usr/bin/env bash
set -euo pipefail

readonly REPO=tophtab/nga-just-works
readonly OLD_TIP=0e4c06600830e8d3e2adb373dd6a3f18e6592f40
readonly TASK_DIR=.trellis/tasks/07-27-rebuild-git-history
readonly ARTIFACT_DIR="$TASK_DIR/artifacts"
readonly TEMP_NAMESPACE=refs/trellis/rebuild-git-history
readonly CANDIDATE=$(git rev-parse "$TEMP_NAMESPACE/main")
readonly BACKUP_DIR=$(cat "$ARTIFACT_DIR/backup-path.txt")
readonly MIGRATION_STAMP=$(cat "$ARTIFACT_DIR/migration-stamp.txt")
readonly BACKUP_BRANCH=backup/pre-history-rebuild-$MIGRATION_STAMP

actions_disabled=0
published=0
publication_committed=0

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

canonical_releases() {
  jq -S 'map({
    id, tag_name, target_commitish, name, draft, immutable, prerelease,
    created_at, published_at, body,
    author: {login: .author.login, id: .author.id},
    assets: (.assets | map({
      id, name, label, content_type, state, size, digest, created_at, updated_at,
      uploader: {login: .uploader.login, id: .uploader.id}
    }) | sort_by(.id))
  }) | sort_by(.id)'
}

assert_dirty_state() {
  cmp "$BACKUP_DIR/status.porcelain-v1.z" <(git status --porcelain=v1 -z) || die "working status drifted"
  cmp "$BACKUP_DIR/working-tree.patch" <(git diff --binary --full-index) || die "working tree patch drifted"
  cmp "$BACKUP_DIR/index.patch" <(git diff --cached --binary --full-index) || die "index patch drifted"
  cmp "$BACKUP_DIR/working-tree.raw.z" <(git diff --raw -z) || die "working tree raw state drifted"
  cmp "$BACKUP_DIR/index.raw.z" <(git diff --cached --raw -z) || die "index raw state drifted"
  cmp "$BACKUP_DIR/dirty-file-sha256.txt" <(
    git status --porcelain=v1 -z | while IFS= read -r -d '' entry; do
      path=${entry:3}
      [[ -f $path ]] && sha256sum -- "$path"
    done
  ) || die "dirty file content drifted"
}

assert_releases_unchanged() {
  cmp <(canonical_releases < "$ARTIFACT_DIR/releases-before.json") \
      <(gh api --paginate "repos/$REPO/releases?per_page=100" | canonical_releases) || die "Release inventory drifted"
}

assert_no_active_runs() {
  local active
  active=$(gh api "repos/$REPO/actions/runs?per_page=100" --jq '.workflow_runs[] | select(.status != "completed") | .id')
  [[ -z $active ]] || die "workflow runs are active: $active"
}

restore_actions() {
  local attempt
  for attempt in 1 2 3; do
    if gh api --method PUT "repos/$REPO/actions/permissions" \
        -F enabled=true -f allowed_actions=all > "$ARTIFACT_DIR/actions-restore-response.json" &&
       cmp <(jq -S . "$ARTIFACT_DIR/actions-before.json") \
           <(gh api "repos/$REPO/actions/permissions" | jq -S .); then
      actions_disabled=0
      return 0
    fi
    sleep 1
  done
  return 1
}

rollback_remote() {
  local tag type old_object old_target new_object new_target remote
  local release_id
  local -a leases refspecs
  leases=("--force-with-lease=refs/heads/main:$CANDIDATE")
  refspecs=("$OLD_TIP:refs/heads/main")
  while IFS=$'\t' read -r tag type old_object old_target new_object new_target remote; do
    [[ $tag == tag || $remote != yes ]] && continue
    leases+=("--force-with-lease=refs/tags/$tag:$new_object")
    refspecs+=("$old_object:refs/tags/$tag")
  done < "$ARTIFACT_DIR/tag-map.tsv"
  git push --atomic "${leases[@]}" origin "${refspecs[@]}"
  release_id=$(jq -r '.[] | select(.tag_name == "debug-0e4c06600830") | .id' "$ARTIFACT_DIR/releases-before.json")
  gh api --method PATCH "repos/$REPO/releases/$release_id" -f target_commitish="$OLD_TIP" >/dev/null
}

cleanup() {
  local status=$?
  trap - EXIT
  if (( status != 0 && published == 1 && publication_committed == 0 )); then
    printf 'Publication failed after ref update; rolling remote refs back.\n' >&2
    rollback_remote || printf 'CRITICAL: automatic remote rollback failed.\n' >&2
  fi
  if (( actions_disabled == 1 )); then
    restore_actions || printf 'CRITICAL: automatic Actions restoration failed.\n' >&2
  fi
  exit "$status"
}
trap cleanup EXIT

assert_preflight() {
  [[ $(git rev-parse HEAD) == "$OLD_TIP" ]] || die "HEAD drifted"
  [[ $(git rev-parse refs/heads/main) == "$OLD_TIP" ]] || die "local main drifted"
  [[ $(git rev-parse refs/remotes/origin/main) == "$OLD_TIP" ]] || die "origin/main drifted"
  [[ $(git rev-parse "$TEMP_NAMESPACE/main") == "$CANDIDATE" ]] || die "candidate ref drifted"
  [[ $(git rev-list --count 5d807617f8058950f7ea81dda405e38fb0cc37ec.."$CANDIDATE") -eq 53 ]] || die "candidate count drifted"
  [[ $(git rev-parse "$OLD_TIP^{tree}") == $(git rev-parse "$CANDIDATE^{tree}") ]] || die "candidate tree drifted"

  cmp "$ARTIFACT_DIR/remote-refs-before.txt" \
      <(git ls-remote origin refs/heads/main 'refs/tags/*' 'refs/tags/*^{}') || die "remote refs drifted"
  cmp <(jq -S . "$ARTIFACT_DIR/actions-before.json") \
      <(gh api "repos/$REPO/actions/permissions" | jq -S .) || die "Actions permissions drifted"
  [[ $(gh api "repos/$REPO" --jq '.fork') == false ]] || die "repository became a fork"
  [[ $(gh api "repos/$REPO" --jq '.default_branch') == main ]] || die "default branch drifted"
  assert_releases_unchanged
  assert_no_active_runs
  assert_dirty_state

  [[ -z $(git ls-remote origin "refs/heads/$BACKUP_BRANCH") ]] || die "backup branch already exists"
}

publish_remote() {
  local tag type old_object old_target new_object new_target remote
  local backup_api_path release_id
  local -a leases refspecs

  gh api --method PUT "repos/$REPO/actions/permissions" -F enabled=false > "$ARTIFACT_DIR/actions-disable-response.json"
  actions_disabled=1
  [[ $(gh api "repos/$REPO/actions/permissions" --jq '.enabled') == false ]] || die "Actions did not disable"
  assert_no_active_runs

  git push origin "$OLD_TIP:refs/heads/$BACKUP_BRANCH"
  [[ $(git ls-remote origin "refs/heads/$BACKUP_BRANCH" | cut -f1) == "$OLD_TIP" ]] || die "remote backup branch verification failed"
  backup_api_path=$(jq -rn --arg value "$BACKUP_BRANCH" '$value | @uri')
  [[ $(gh api "repos/$REPO/branches/$backup_api_path" --jq '.commit.sha') == "$OLD_TIP" ]] || die "GitHub backup branch verification failed"
  printf '%s\n' "$BACKUP_BRANCH" > "$ARTIFACT_DIR/remote-backup-branch.txt"

  assert_releases_unchanged
  leases=("--force-with-lease=refs/heads/main:$OLD_TIP")
  refspecs=("$TEMP_NAMESPACE/main:refs/heads/main")
  while IFS=$'\t' read -r tag type old_object old_target new_object new_target remote; do
    [[ $tag == tag || $remote != yes ]] && continue
    leases+=("--force-with-lease=refs/tags/$tag:$old_object")
    refspecs+=("$TEMP_NAMESPACE/tags/$tag:refs/tags/$tag")
  done < "$ARTIFACT_DIR/tag-map.tsv"

  git push --atomic "${leases[@]}" origin "${refspecs[@]}"
  published=1

  [[ $(git ls-remote origin refs/heads/main | cut -f1) == "$CANDIDATE" ]] || die "remote main verification failed"
  while IFS=$'\t' read -r tag type old_object old_target new_object new_target remote; do
    [[ $tag == tag || $remote != yes ]] && continue
    [[ $(git ls-remote origin "refs/tags/$tag" | cut -f1) == "$new_object" ]] || die "remote tag verification failed: $tag"
  done < "$ARTIFACT_DIR/tag-map.tsv"

  release_id=$(jq -r '.[] | select(.tag_name == "debug-0e4c06600830") | .id' "$ARTIFACT_DIR/releases-before.json")
  [[ $release_id != null && -n $release_id ]] || die "debug Release not found in snapshot"
  gh api --method PATCH "repos/$REPO/releases/$release_id" \
    -f target_commitish="$CANDIDATE" > "$ARTIFACT_DIR/debug-release-patch-response.json"

  jq --arg candidate "$CANDIDATE" \
    'map(if .tag_name == "debug-0e4c06600830" then .target_commitish = $candidate else . end)' \
    "$ARTIFACT_DIR/releases-before.json" | canonical_releases > "$ARTIFACT_DIR/releases-expected-after.json"
  gh api --paginate "repos/$REPO/releases?per_page=100" | canonical_releases > "$ARTIFACT_DIR/releases-after.json"
  cmp "$ARTIFACT_DIR/releases-expected-after.json" "$ARTIFACT_DIR/releases-after.json" || die "post-publication Release metadata mismatch"

  restore_actions || die "Actions permissions could not be restored"
  publication_committed=1
}

move_local_refs() {
  local tag type old_object old_target new_object new_target remote
  local old_migration new_migration origin_tracking
  local transaction
  read -r old_migration new_migration < "$ARTIFACT_DIR/branch-map.tsv"
  transaction=$(mktemp)
  {
    printf 'start\n'
    printf 'update refs/heads/main %s %s\n' "$CANDIDATE" "$OLD_TIP"
    printf 'update refs/heads/migration/kotlin-compose-mvvm %s %s\n' "$new_migration" "$old_migration"
    while IFS=$'\t' read -r tag type old_object old_target new_object new_target remote; do
      [[ $tag == tag || $tag == debug-0e4c06600830 ]] && continue
      printf 'update refs/tags/%s %s %s\n' "$tag" "$new_object" "$old_object"
    done < "$ARTIFACT_DIR/tag-map.tsv"
    origin_tracking=$(git rev-parse refs/remotes/origin/main)
    if [[ $origin_tracking == "$OLD_TIP" ]]; then
      printf 'update refs/remotes/origin/main %s %s\n' "$CANDIDATE" "$OLD_TIP"
    elif [[ $origin_tracking != "$CANDIDATE" ]]; then
      die "origin/main moved unexpectedly after push"
    fi
    printf 'prepare\ncommit\n'
  } > "$transaction"
  git update-ref --stdin < "$transaction"
  rm -f "$transaction"

  [[ $(git rev-parse HEAD) == "$CANDIDATE" ]] || die "local HEAD did not move"
  [[ $(git rev-parse refs/remotes/origin/main) == "$CANDIDATE" ]] || die "local origin/main did not move"
  assert_dirty_state
}

assert_preflight
publish_remote
move_local_refs
trap - EXIT
printf 'History publication completed successfully.\n'
