# 表情分类内拖拽排序

## Goal

让用户在发帖/回复的表情面板里，通过长按拖拽自定义**每个表情分类内部**的表情排列顺序，并持久化保存。当前顺序完全由 `EmoticonUtils.EMOTICON_URL` 静态数组写死，用户无法把自己常用的表情挪到前面。

本任务只改变表情面板的**展示顺序**，不改变表情的插入代码、不改变帖子正文的表情解码逻辑。

## Confirmed Facts

调研于 2026-07-28，基于当前 `main` 分支：

- 表情面板是老 Java View 体系：`layout_toolbar_panel_emoticon.xml` →
  `sp.phone.view.toolbar.EmoticonControlPanel`（`ViewPager` + `TabLayout`）→
  `sp.phone.ui.adapter.EmoticonParentAdapter`（`PagerAdapter`，一页一个分类）→
  `sp.phone.ui.adapter.EmoticonChildAdapter`（`RecyclerView`，`GridLayoutManager`，4 列）。
- 面板由 `ToolbarContainer` 通过 `R.id.bottom_emoticon_stub` 的 `ViewStub` 懒加载，
  每个 `TopicPostFragment` 实例只 inflate 一次。
- 顺序数据源是 `lib_base_common/.../common/util/EmoticonUtils.java` 的
  `EMOTICON_LABEL`（6 个分类）和 `EMOTICON_URL`（`[分类][序号][名称, 文件名]`），
  共 238 个表情：ac 45、a2 46、ng 34、pg 15、pst 65、dt 33。没有任何持久化。
- **每个分类内部的表情名称和图片文件名都不重复**（已用脚本对全部 6 个分类校验）。
  因此图片文件名（如 `ac0.png`）可以作为分类内的稳定标识。
- 图片资源位于 `lib_core/src/main/assets/<分类 id>/<文件名>`，分类 id 取
  `EMOTICON_LABEL[i][0]`（`ac` / `a2` / `ng` / `pg` / `pst` / `dt`）。
  注意 `pst` 分类的文件名前缀是 `pt`，两者不一致。
- `EmoticonUtils.getPathByURI()` 本意是把帖子正文里的表情映射到本地 assets 图片，
  调用方是 `lib_core/.../ForumImageDecoder.java:57` 和 `sp/phone/util/StringUtils.java:465`。
  **但该函数目前恒定返回 null，是一个先于本任务存在的失效路径**：
  它用 `EMOTICON_URL[c][i][0]`（表情名，如 `blink`）去 `equals` 调用方传入的值，
  而调用方传入的是 `ForumImageDecoder.java:25` 正则 `<img src='(http\S+)'>` 抓出的 http 网址，
  两者永不相等。旁证：`getFilePath()` 内部变量名为 `httpUri` 却读第 0 列，
  且 `PRR_EMOTION_URL`（`EmoticonUtils.java:19`）定义后全项目无引用——
  该数组的两列语义在历史重构中被调换，`getPathByURI` / `getFilePath` 未同步修改。
  实际后果是帖子正文中的表情走远程图片加载而非本地 assets。
  **本任务不修此 bug**（用户 2026-07-28 决定），仅要求不让它变得更糟。
- 因此「重排数组会破坏正文解码」这一顾虑**不成立**：该查表是按内容线性搜索、与顺序无关，
  且当前根本不生效。不写回静态数组的真正理由见 `design.md` §1。
- 点击表情时 `EmoticonChildAdapter` 发送 `RxEvent.EVENT_INSERT_EMOTICON`，
  payload 是 `"[s:分类:名称]-分类/文件名"`。
- 项目持久化惯例是 `gov.anzong.androidnga.base.util.PreferenceUtils`
  （SharedPreferences，支持 `putData(String, List<?>)` / `getData(String, Class<T>)`，内部走 fastjson）。
- 项目已有的 RecyclerView 拖拽先例是 `TopicHistoryFragment`（仅用了 `ItemTouchHelper` 的 swipe 能力）。
- 设置页是 `SettingsFragment` + `res/xml/settings.xml`，确认弹窗用
  `sp.phone.ui.fragment.dialog.AlertDialogFragment`，成功提示用 `ToastUtils.success`。
- `lib_base_common` 已具备 `junit:junit:4.12`、`commons-io`、`fastjson` 依赖，可写纯 JVM 单元测试。

## Requirements

### R1. 分类内拖拽排序

- 在表情面板中，**长按**某个表情后可以拖拽，把它移动到同一分类内的任意位置。
- 拖拽支持上下左右四个方向（网格布局）。
- 拖拽过程中实时看到其他表情让位。
- 松手即生效并持久化。
- 拖拽**不得**触发表情插入（长按与单击必须区分开）。
- 不支持跨分类拖拽；不支持删除表情。

### R2. 顺序持久化

- 每个分类独立保存自己的顺序。
- 顺序按图片文件名列表保存，不按数组下标保存。
- 未自定义过的分类保持内置顺序，不写入任何数据。
- 顺序在应用重启、面板重建后仍然生效。

### R3. 顺序与内置数据的兼容

当保存的顺序和当前版本的内置表情列表不一致时（应用升级导致官方增删表情）：

- 保存的顺序里存在、但内置列表里已不存在的表情：**忽略**。
- 内置列表里存在、但保存的顺序里没有的表情（新增表情）：**追加到该分类末尾**，
  多个新增表情之间保持内置列表的相对顺序。
- 保存的顺序里出现重复项：只保留第一次出现的位置。
- 解析失败或数据损坏：回退到内置顺序，不崩溃。

### R4. 重置入口

- 设置页新增一项「重置表情顺序」，点击后弹确认框，确认后清除**所有分类**的自定义顺序，
  恢复内置顺序，并给出成功提示。
- 取消确认框不产生任何修改。

### R5. 不得回归的既有行为

- `EmoticonUtils.EMOTICON_LABEL` 和 `EMOTICON_URL` 的**内容**保持不变，
  且保持为不被写入的只读常量；`getPathByURI()` / `getFilePath()` 一行不改
  （含其既有失效行为，本任务不修也不使其恶化）。
- 分类 Tab 的顺序、标题、数量不变。
- 点击表情插入的 payload 字符串格式不变（`"[s:分类:名称]-分类/文件名"`）。
- 夜间模式下 `ac` / `a2` / `dt` 三组表情加白底的逻辑不变。
- 表情面板的高度、列数（4 列）、item 尺寸计算不变。

## Out of Scope

- 表情分类（Tab）之间的排序、隐藏分类。
- 「常用表情」分类、使用频率统计。
- 跨设备/跨账号同步表情顺序。
- 把表情面板迁移到 Compose。
- 自定义表情、外部表情包导入。
- **修复 `getPathByURI()` 的既有失效**（表情走远程加载而非本地 assets）。
  用户 2026-07-28 明确决定不处理，也不单独立任务。
- 拖拽功能的新手引导 / 一次性提示。用户 2026-07-28 明确决定不做。

## Acceptance Criteria

### 交付前由实现方验证（自动化 / 代码审查）

- [ ] AC7：`getPathByURI()` / `getFilePath()` 源码零改动，帖子正文渲染路径不受影响
      （该函数当前恒返回 null，本任务不改变这一既有行为）。
- [ ] AC8：顺序解析逻辑有纯 JVM 单元测试，覆盖：空数据、完整排列、含已失效表情、缺失新增表情、含重复项、损坏数据。
- [ ] AC9：有单元测试守住「分类内文件名唯一」这一关键前提。
- [ ] AC10：`./gradlew :lib_base_common:testDebugUnitTest`、
      `./gradlew :nga_phone_base_3.0:testDebugUnitTest`、
      `./gradlew :nga_phone_base_3.0:assembleDebug` 通过；
      `:nga_phone_base_3.0:lintDebug` 相对基线无新增 error。
- [ ] AC11：`EmoticonChildAdapter` 发出的插入 payload 字符串与改动前逐字符一致（代码比对）。

### 交付后由用户在真机确认（不做自动化）

用户 2026-07-28 决定不跑设备自动化测试。以下为功能性验收，实现方**不得**自行标记为通过。

**验收结果：用户于 2026-07-29 在真机（`REDACTED_SERIAL_XIAOMI` / 24129PN74C，Android 15）
逐条确认全部通过。** AC5 在首轮验收中失败，修复后复测通过，详见下方缺陷记录。

- [x] AC1：在表情面板任一分类中长按一个表情并拖到其他位置，松手后该表情停在新位置。
- [x] AC2：单击表情仍然正常插入到输入框，拖拽结束后不会额外插入表情。
- [x] AC3：退出并重新进入发帖界面（或重启应用）后，AC1 中调整的顺序仍然保持。
- [x] AC4：调整 A 分类的顺序不影响 B 分类的顺序。
- [x] AC5：拖拽时 ViewPager 不会误响应为左右翻页。（首轮失败 → 修复 → 复测通过）
- [x] AC6：设置页「重置表情顺序」点击后弹确认框；确认后所有分类恢复内置顺序并提示成功；取消则无变化。
- [x] AC12：夜间模式下 ac / a2 / dt 三组表情仍有白底。

### 缺陷记录：AC5 首轮失败

- **现象**：长按后横向拖拽被外层 `ViewPager` 抢走，变成切换分类。
- **根因**：实现中在 `onSelectedChanged` 里对 item 的父容器（即 `RecyclerView`）
  调用了 `requestDisallowInterceptTouchEvent(true)`。`RecyclerView` 重写了该方法，
  会**先遍历通知所有 `OnItemTouchListener`**；`ItemTouchHelper` 自身即是其中之一，
  其处理为 `select(null, ACTION_STATE_IDLE)`，等于当场取消本次拖拽，
  横向手势随即回落给 `ViewPager`。该「保护」代码正是故障本身。
- **证据**：反编译 `androidx.recyclerview:recyclerview:1.1.0` 字节码确认
  `RecyclerView.requestDisallowInterceptTouchEvent` 的转发行为、
  `ItemTouchHelper$2.onRequestDisallowInterceptTouchEvent` 的取消逻辑，
  以及 `ItemTouchHelper.select()` 内部已对 `mRecyclerView.getParent()` 申请 disallow。
- **修复**：删除该调用，仅保留振动反馈。`ViewPager` 冲突由 `ItemTouchHelper` 自身处理。
- **防复发**：`component-guidelines.md` 中原有的「应调用
  `requestDisallowInterceptTouchEvent`」建议是错误指导，已改写为明确禁令并附原因。

## Known Limitations

- 设置页重置时，如果某个发帖界面仍然存活且表情面板已经 inflate，该面板要等下次重建才显示内置顺序。
  设置页是独立 Activity，正常使用路径下发帖界面通常已销毁，不额外做跨界面刷新。
- 拖拽功能没有显式的界面提示，属于「长按发现式」交互，与项目内收藏版块拖拽的交互一致。
  用户 2026-07-28 确认接受，不做引导提示。
- 帖子正文里的表情仍走远程图片加载（`getPathByURI` 既有失效），本任务不改善这一点。

## Notes

- 本任务为复杂任务，需 `prd.md` + `design.md` + `implement.md` 齐备后再 `task.py start`。
- 相关规范：`.trellis/spec/backend/android-quality-guidelines.md`（构建/测试门禁、设备测试策略）、
  `.trellis/spec/frontend/component-guidelines.md`（保持 Justwen UI 基线，不引入并行 UI 架构）。
