# Favorite Board State Contract

## Scenario: App-Wide Favorite Reorder Transaction

### 1. Scope / Trigger

Use this contract when changing favorite membership/order, the favorite grid,
drag gestures, accessibility reorder actions, Pager interaction, or
`board_bookmark.json` persistence. Favorite state crosses Compose UI,
`ForumBoardViewModel`, `ForumBoardModel`, and `ForumBoardRepository`.

### 2. Signatures

```kotlin
fun bookmarkStableKey(board: BoardEntity): String
fun ForumBoardViewModel.beginBookmarkReorder(): List<BoardEntity>
fun ForumBoardViewModel.moveBookmark(from: Int, to: Int): Boolean
fun ForumBoardViewModel.cancelBookmarkReorder(snapshot: List<BoardEntity>)
fun ForumBoardViewModel.commitBookmarkReorder(snapshot: List<BoardEntity>)
fun ForumBoardRepository.writeBookmarkBoard(context: Context, boardList: List<BoardEntity>)
```

The enclosing pager accepts an explicit scroll gate:

```kotlin
TabLayoutWithPager(..., userScrollEnabled: Boolean = true)
```

### 3. Contracts

- Favorite membership/order is App-wide and is not keyed by the active NGA
  account. Login or account switching must not replace or reset it.
- Stable identity is `fid + stid`; list index is only a transient position.
- A short press opens the board. A long press on a favorite card activates
  direct drag without a separate sorting mode.
- Capture a snapshot before moving. Publish candidate moves immediately, then
  persist on the IO dispatcher.
- Persist only when the candidate is still current. Roll back only when that
  same candidate is still current, so an older failed write cannot overwrite a
  newer add/remove/reorder.
- Disable Pager user scrolling only while drag is active. Restore it on end,
  cancel, disposal, and rollback.
- Provide TalkBack move up/down/top/bottom actions; pointer drag is not the only
  reorder path.
- Write JSON to a staging file before replacing the primary file. A valid
  staging/backup file may recover a damaged primary. `[]` is an intentional
  empty list, not a missing source.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Invalid or no-op move index | Return false; do not persist |
| Valid move | Publish new order and schedule persistence |
| Drag cancelled | Restore the captured snapshot and re-enable Pager |
| Save fails and candidate is still current | Restore snapshot and republish |
| Save fails after a newer mutation | Do not overwrite or roll back newer state |
| Primary JSON malformed, backup/staging valid | Recover valid ordered data |
| All candidate files malformed | Return an empty safe list without deleting recovery evidence |
| Duplicate `fid + stid` entries | Preserve first stable occurrence only |
| Account changes | Favorite order remains unchanged |

### 5. Good/Base/Bad Cases

- **Good**: long press captures `[A,B,C]`, moving B after C publishes
  `[A,C,B]`, the same candidate persists, and Pager is restored on release.
- **Base**: short horizontal movement before long-press activation remains a
  Pager gesture; no reorder transaction begins.
- **Bad**: key items by index, save on the UI thread, disable Pager for every
  touch, or let a failed old write restore over a newer order.
- **Bad**: scope `board_bookmark.json` by account or require a network/session
  abstraction to load local favorites.

### 6. Tests Required

- Stable keys distinguish identical `fid` values with different `stid` values.
- Move covers forward, backward, same-index, and out-of-range positions.
- JSON/file round trips cover empty and duplicate data.
- Recovery covers malformed primary with valid staging/backup.
- Persistence failure covers rollback and newer-mutation protection.
- UI/static review covers short-click navigation, long-press drag activation,
  Pager restoration on every terminal path, and TalkBack reorder actions.

### 7. Wrong vs Correct

#### Wrong

```kotlin
itemsIndexed(boards, key = { index, _ -> index }) { ... }
pagerEnabled = false // for every pointer down
```

Index keys lose identity during reorder, and disabling paging before drag
activation breaks ordinary category swiping.

#### Correct

```kotlin
items(boards, key = ::bookmarkStableKey) { board -> ... }
TabLayoutWithPager(userScrollEnabled = !isBookmarkDragging)
```

The stable key follows the board while the explicit drag state arbitrates only
the active gesture.
