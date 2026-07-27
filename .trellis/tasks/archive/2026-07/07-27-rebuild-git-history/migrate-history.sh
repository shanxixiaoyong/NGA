#!/usr/bin/env bash
set -euo pipefail

readonly OLD_ROOT=be26b6e07e5f6ac8fcbe505af5280ad95cc9e284
readonly OLD_TIP=0e4c06600830e8d3e2adb373dd6a3f18e6592f40
readonly UPSTREAM=5d807617f8058950f7ea81dda405e38fb0cc37ec
readonly TASK_DIR=.trellis/tasks/07-27-rebuild-git-history
readonly ARTIFACT_DIR="$TASK_DIR/artifacts"
readonly TEMP_NAMESPACE=refs/trellis/rebuild-git-history
readonly STABLE_TAGS=(4.3.0 4.5.0 4.6.0 4.7.0 4.7.1 4.7.2)
readonly DEBUG_TAGS=(debug-0817b0bc0f46 debug-c594869bcde4 debug-0e4c06600830)

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

assert_ref() {
  local ref=$1 expected=$2 actual
  actual=$(git rev-parse --verify "$ref") || die "missing ref: $ref"
  [[ $actual == "$expected" ]] || die "$ref drifted: expected $expected, found $actual"
}

map_commit() {
  local old=$1 mapped
  mapped=$(awk -F '\t' -v old="$old" '$1 == old { print $2 }' "$ARTIFACT_DIR/commit-map.tsv")
  [[ -n $mapped ]] || die "no commit mapping for $old"
  printf '%s\n' "$mapped"
}

commit_header_value() {
  local commit=$1 field=$2
  git cat-file commit "$commit" | sed -n "s/^${field} //p"
}

build_commits() {
  local old parent tree author committer new_parent new message_file
  local -a commits parents

  mapfile -t commits < <(git rev-list --reverse "$OLD_TIP")
  [[ ${#commits[@]} -eq 53 ]] || die "expected 53 project commits, found ${#commits[@]}"
  [[ ${commits[0]} == "$OLD_ROOT" ]] || die "unexpected project root: ${commits[0]}"

  printf 'old_commit\tnew_commit\told_parent\tnew_parent\ttree\n' > "$ARTIFACT_DIR/commit-map.tsv"
  message_file=$(mktemp)

  new_parent=$UPSTREAM
  for old in "${commits[@]}"; do
    mapfile -t parents < <(git show -s --format='%P' "$old" | tr ' ' '\n' | sed '/^$/d')
    if [[ $old == "$OLD_ROOT" ]]; then
      [[ ${#parents[@]} -eq 0 ]] || die "old root unexpectedly has a parent"
      parent=-
    else
      [[ ${#parents[@]} -eq 1 ]] || die "nonlinear commit $old"
      parent=${parents[0]}
    fi

    local unsupported
    unsupported=$(git cat-file commit "$old" | sed -n '1,/^$/p' | sed -E '/^(tree|parent|author|committer) /d; /^$/d')
    [[ -z $unsupported ]] || die "unsupported commit header in $old: $unsupported"

    tree=$(commit_header_value "$old" tree)
    author=$(commit_header_value "$old" author)
    committer=$(commit_header_value "$old" committer)
    [[ -n $tree && -n $author && -n $committer ]] || die "missing required header in $old"
    git cat-file commit "$old" | sed '1,/^$/d' > "$message_file"

    export GIT_AUTHOR_NAME=${author% <*}
    local author_tail=${author##* <}
    export GIT_AUTHOR_EMAIL=${author_tail%%>*}
    export GIT_AUTHOR_DATE=${author_tail#*> }
    export GIT_COMMITTER_NAME=${committer% <*}
    local committer_tail=${committer##* <}
    export GIT_COMMITTER_EMAIL=${committer_tail%%>*}
    export GIT_COMMITTER_DATE=${committer_tail#*> }

    new=$(git commit-tree "$tree" -p "$new_parent" < "$message_file")
    printf '%s\t%s\t%s\t%s\t%s\n' "$old" "$new" "$parent" "$new_parent" "$tree" >> "$ARTIFACT_DIR/commit-map.tsv"
    new_parent=$new
  done
  unset GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_AUTHOR_DATE
  unset GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL GIT_COMMITTER_DATE
  rm -f "$message_file"

  git update-ref "$TEMP_NAMESPACE/main" "$new_parent"
  printf '%s\n' "$new_parent" > "$ARTIFACT_DIR/candidate-tip.txt"
}

build_tags() {
  local tag old_object type old_target new_target new_object tag_file
  printf 'tag\ttype\told_object\told_target\tnew_object\tnew_target\tremote\n' > "$ARTIFACT_DIR/tag-map.tsv"
  tag_file=$(mktemp)

  for tag in "${STABLE_TAGS[@]}" "${DEBUG_TAGS[@]}"; do
    if git show-ref --verify --quiet "refs/tags/$tag"; then
      old_object=$(git rev-parse "refs/tags/$tag")
    elif [[ $tag == debug-0e4c06600830 ]]; then
      old_object=$OLD_TIP
    else
      die "missing local project tag: $tag"
    fi
    type=$(git cat-file -t "$old_object")
    if [[ $type == tag ]]; then
      old_target=$(git rev-parse "$old_object^{}")
    else
      old_target=$old_object
    fi
    new_target=$(map_commit "$old_target")
    if [[ $type == tag ]]; then
      git cat-file tag "$old_object" | sed "1s/^object $old_target$/object $new_target/" > "$tag_file"
      [[ $(sed -n '1p' "$tag_file") == "object $new_target" ]] || die "could not rewrite tag $tag"
      new_object=$(git hash-object -t tag -w "$tag_file")
    elif [[ $type == commit ]]; then
      new_object=$new_target
    else
      die "unsupported tag object type $type for $tag"
    fi

    local remote=no
    [[ $tag == debug-0817b0bc0f46 || $tag == debug-c594869bcde4 ]] || remote=yes
    git update-ref "$TEMP_NAMESPACE/tags/$tag" "$new_object"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$tag" "$type" "$old_object" "$old_target" "$new_object" "$new_target" "$remote" >> "$ARTIFACT_DIR/tag-map.tsv"
  done
  rm -f "$tag_file"

  local old_migration new_migration
  old_migration=$(git rev-parse refs/heads/migration/kotlin-compose-mvvm)
  new_migration=$(map_commit "$old_migration")
  git update-ref "$TEMP_NAMESPACE/migration-kotlin-compose-mvvm" "$new_migration"
  printf '%s\t%s\n' "$old_migration" "$new_migration" > "$ARTIFACT_DIR/branch-map.tsv"
}

validate_candidate() {
  local candidate old new old_parent new_parent tree
  candidate=$(git rev-parse "$TEMP_NAMESPACE/main")
  [[ $(git merge-base "$candidate" "$UPSTREAM") == "$UPSTREAM" ]] || die "candidate merge-base mismatch"
  [[ $(git rev-list --count "$UPSTREAM..$candidate") -eq 53 ]] || die "candidate project commit count mismatch"
  [[ $(git rev-parse "$OLD_TIP^{tree}") == $(git rev-parse "$candidate^{tree}") ]] || die "tip tree mismatch"

  while IFS=$'\t' read -r old new old_parent new_parent tree; do
    [[ $old == old_commit ]] && continue
    [[ $(git rev-parse "$old^{tree}") == $(git rev-parse "$new^{tree}") ]] || die "tree mismatch: $old"
    cmp <(git cat-file commit "$old" | sed -n '/^author /p;/^committer /p') \
        <(git cat-file commit "$new" | sed -n '/^author /p;/^committer /p') || die "raw identity/date mismatch: $old"
    cmp <(git show -s --format='%an%x00%ae%x00%aI%x00%cn%x00%ce%x00%cI' "$old") \
        <(git show -s --format='%an%x00%ae%x00%aI%x00%cn%x00%ce%x00%cI' "$new") || die "identity/date mismatch: $old"
    cmp <(git cat-file commit "$old" | sed '1,/^$/d') \
        <(git cat-file commit "$new" | sed '1,/^$/d') || die "message mismatch: $old"
    [[ $(git rev-parse "$new^{tree}") == "$tree" ]] || die "recorded tree mismatch: $old"
    [[ $(git show -s --format='%P' "$new") == "$new_parent" ]] || die "new parent mismatch: $old"
    if [[ $old_parent != - ]]; then
      cmp <(git diff-tree --root --binary --full-index "$old_parent" "$old") \
          <(git diff-tree --root --binary --full-index "$new_parent" "$new") || die "diff mismatch: $old"
    fi
  done < "$ARTIFACT_DIR/commit-map.tsv"

  while IFS=$'\t' read -r tag type old_object old_target new_object new_target remote; do
    [[ $tag == tag ]] && continue
    [[ $(git rev-parse "$TEMP_NAMESPACE/tags/$tag^{}") == "$new_target" ]] || die "tag target mismatch: $tag"
    if [[ $type == tag ]]; then
      cmp <(git cat-file tag "$old_object" | sed '1s/^object .*/object MAPPED/') \
          <(git cat-file tag "$new_object" | sed '1s/^object .*/object MAPPED/') || die "tag metadata mismatch: $tag"
    fi
  done < "$ARTIFACT_DIR/tag-map.tsv"

  git fsck --full --no-reflogs --unreachable > "$ARTIFACT_DIR/fsck-before-publish.txt" 2>&1 || die "git fsck failed"
}

main() {
  mkdir -p "$ARTIFACT_DIR"
  assert_ref refs/heads/main "$OLD_TIP"
  assert_ref HEAD "$OLD_TIP"
  assert_ref refs/remotes/origin/main "$OLD_TIP"
  assert_ref "$UPSTREAM" "$UPSTREAM"
  [[ -z $(git merge-base "$OLD_TIP" "$UPSTREAM" || true) ]] || die "old history unexpectedly has a merge base with upstream"
  build_commits
  build_tags
  validate_candidate
  printf 'Candidate history built and validated.\n'
}

main "$@"
