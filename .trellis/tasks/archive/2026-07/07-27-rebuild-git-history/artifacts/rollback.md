# Verified Rollback Points

Rollback is reserved for a newly discovered integrity or Release failure. The successful rewritten history should otherwise remain published.

## Recovery Sources

- Remote old-history branch: `backup/pre-history-rebuild-20260727T124639Z` -> `0e4c06600830e8d3e2adb373dd6a3f18e6592f40`.
- Verified local bundle: `.git/trellis-backups/rebuild-git-history-20260727T124639Z/repository.bundle`.
- Original local and remote ref inventories are stored beside the bundle and in `remote-refs-before.txt`.
- Original working-tree and index patches, raw records, status, and content hashes are stored beside the bundle.

## Guarded Remote Rollback

Disable Actions, verify there are no active runs, and then restore only the enumerated refs with the current rewritten objects as leases:

```bash
gh api --method PUT repos/tophtab/nga-just-works/actions/permissions -F enabled=false

git push --atomic \
  --force-with-lease=refs/heads/main:c7e76d3cb6873b17149019417398eec54b41c294 \
  --force-with-lease=refs/tags/4.3.0:3c150f378f21d2d4dbfd46ee41d436998e02877d \
  --force-with-lease=refs/tags/4.5.0:98aa546bc01c6255fb7c65eed3a1739884052d6c \
  --force-with-lease=refs/tags/4.6.0:db2f64eef3b1aeac4b9a64fe3884815735985e57 \
  --force-with-lease=refs/tags/4.7.0:e0b066d319b97d36172517b6f0dcfc1a20f32056 \
  --force-with-lease=refs/tags/4.7.1:c3cfbd647fd7f3e6bae36b3be918c76c071c6c89 \
  --force-with-lease=refs/tags/4.7.2:f10ddc5dbb61294b472b5234d2f8df9687dfd924 \
  --force-with-lease=refs/tags/debug-0e4c06600830:c7e76d3cb6873b17149019417398eec54b41c294 \
  origin \
  0e4c06600830e8d3e2adb373dd6a3f18e6592f40:refs/heads/main \
  bb72ba893b8fd01ad16a09f5228e69bc0b00aa95:refs/tags/4.3.0 \
  20bf63295afd60e4b687a8d94fd9ce4de96ea962:refs/tags/4.5.0 \
  260aef4c75a89983ef904bc7e1e198efad92a7ae:refs/tags/4.6.0 \
  f50c8ca03b71e9de7f56402753cea13c9c635ad2:refs/tags/4.7.0 \
  02b5ec4dad8b81ed3eb8027e9a04301723d4f7fc:refs/tags/4.7.1 \
  1c12d463ede05884879eacdc3abb60cd341f6424:refs/tags/4.7.2 \
  0e4c06600830e8d3e2adb373dd6a3f18e6592f40:refs/tags/debug-0e4c06600830

gh api --method PATCH repos/tophtab/nga-just-works/releases/360408702 \
  -f target_commitish=0e4c06600830e8d3e2adb373dd6a3f18e6592f40

gh api --method PUT repos/tophtab/nga-just-works/actions/permissions \
  -F enabled=true -f allowed_actions=all
```

## Guarded Local Rollback

After the remote rollback succeeds, restore the local refs in one guarded transaction:

```bash
git update-ref --stdin <<'EOF'
start
update refs/heads/main 0e4c06600830e8d3e2adb373dd6a3f18e6592f40 c7e76d3cb6873b17149019417398eec54b41c294
update refs/remotes/origin/main 0e4c06600830e8d3e2adb373dd6a3f18e6592f40 c7e76d3cb6873b17149019417398eec54b41c294
update refs/heads/migration/kotlin-compose-mvvm 269c4bdfaee8c3460d5b1ca634d8ba815c5b5a5f 94323161320a7a245322963d3a4934520dcad79c
update refs/tags/4.3.0 bb72ba893b8fd01ad16a09f5228e69bc0b00aa95 3c150f378f21d2d4dbfd46ee41d436998e02877d
update refs/tags/4.5.0 20bf63295afd60e4b687a8d94fd9ce4de96ea962 98aa546bc01c6255fb7c65eed3a1739884052d6c
update refs/tags/4.6.0 260aef4c75a89983ef904bc7e1e198efad92a7ae db2f64eef3b1aeac4b9a64fe3884815735985e57
update refs/tags/4.7.0 f50c8ca03b71e9de7f56402753cea13c9c635ad2 e0b066d319b97d36172517b6f0dcfc1a20f32056
update refs/tags/4.7.1 02b5ec4dad8b81ed3eb8027e9a04301723d4f7fc c3cfbd647fd7f3e6bae36b3be918c76c071c6c89
update refs/tags/4.7.2 1c12d463ede05884879eacdc3abb60cd341f6424 f10ddc5dbb61294b472b5234d2f8df9687dfd924
update refs/tags/debug-0817b0bc0f46 0817b0bc0f4665be5399a5d36398c097008435e3 ce860d1d537b319c613f05336a0f89317fac6fdb
update refs/tags/debug-c594869bcde4 c594869bcde488dfbbe1962fa133556a04004840 995ceb94c970693878eec04ca86e9e7979e53f3f
prepare
commit
EOF
```

The old and new legacy tips have the same tree, so moving `main` does not require touching the index or working tree. Recheck the saved binary patches and file hashes afterward; use the saved patches only if those checks differ.

Do not delete the backup branch until the rewritten history has been accepted for the desired retention period.
