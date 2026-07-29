# 执行计划：表情分类内拖拽排序

前置：`prd.md`、`design.md` 已评审通过，`task.py start` 已执行（状态 `in_progress`）。

## 改动清单

| # | 文件 | 类型 |
| --- | --- | --- |
| 1 | `lib_base_common/src/main/java/gov/anzong/androidnga/common/util/EmoticonOrderResolver.java` | 新增 |
| 2 | `lib_base_common/src/main/java/gov/anzong/androidnga/common/util/EmoticonOrderStore.java` | 新增 |
| 3 | `lib_base_common/src/main/java/gov/anzong/androidnga/common/util/EmoticonUtils.java` | 修改（只增 `getFileNames`） |
| 4 | `lib_base_common/src/test/java/gov/anzong/androidnga/common/util/EmoticonOrderResolverTest.java` | 新增 |
| 5 | `lib_base_common/src/test/java/gov/anzong/androidnga/common/util/EmoticonUtilsContractTest.java` | 新增 |
| 6 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/EmoticonChildAdapter.java` | 修改 |
| 7 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/EmoticonParentAdapter.java` | 修改 |
| 8 | `lib_base_common/src/main/java/gov/anzong/androidnga/common/PreferenceKey.java` | 修改（增 1 个常量） |
| 9 | `nga_phone_base_3.0/src/main/res/xml/settings.xml` | 修改（增 1 项） |
| 10 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/SettingsFragment.java` | 修改 |

**禁止改动**：`EmoticonUtils.EMOTICON_LABEL` / `EMOTICON_URL` 的内容与顺序、
`EmoticonUtils.getPathByURI()`、`ForumImageDecoder`、`StringUtils`、
`EmoticonControlPanel`、`ToolbarContainer`、`layout_toolbar_panel_emoticon.xml`、`dimens.xml`。

## 步骤

### Step 1 — 记录 lint 基线 `[gate]`

```bash
./gradlew :nga_phone_base_3.0:lintDebug
```

把报告中的 error 数量与条目记到 `artifacts/lint-baseline-before.txt`。
`android-quality-guidelines.md` 说明固定上游树本身存在 11 个 app lint error，
所以判定标准是「相对基线无新增」，不是「零 error」。

> 回滚点 R0：此时工作区仍是干净的 `main`。

### Step 2 — 纯逻辑层：`EmoticonOrderResolver` + 测试

1. 新增 `EmoticonOrderResolver`，实现 `resolve` / `toFileNames` / `move`（签名见 `design.md` §3.1）。
   - 类不得 import 任何 `android.*` 或 `PreferenceUtils`。
   - `resolve` 必须保证返回 `0..n-1` 的完整排列。
2. 新增 `EmoticonUtils.getFileNames(int category)`。
3. 新增 `EmoticonOrderResolverTest`、`EmoticonUtilsContractTest`，用例见 `design.md` §5.1。

验证：

```bash
./gradlew :lib_base_common:testDebugUnitTest
```

必须全绿，且报告里测试数 > 0（零测试要当作配置失败处理）。

> **审查门 G1**：`EmoticonUtilsContractTest` 若报告某分类文件名不唯一，
> 说明 `design.md` 的 key 选型前提被推翻，**停止实现并回到 Plan 阶段**，不要就地换 key。

> 回滚点 R1：此步骤自成一体，`git revert` 后不影响任何现有行为。

### Step 3 — 存储层：`EmoticonOrderStore`

1. 新增 `EmoticonOrderStore`（`design.md` §3.2）：`loadOrder` / `saveOrder` / `resetAll` / `prefKey`。
2. `loadOrder` 整段 try/catch `Exception`，异常回退恒等排列；分类下标越界返回空数组。
3. `saveOrder` 在结果等于内置顺序时删除该 key。
4. `PreferenceKey` 增加 `KEY_RESET_EMOTICON_ORDER = "key_reset_emoticon_order"`。

验证：

```bash
./gradlew :lib_base_common:assembleDebug
./gradlew :lib_base_common:testDebugUnitTest
```

> 注意：`EmoticonOrderStore` 依赖 `PreferenceUtils`，其静态初始化需要 `ContextUtils`，
> **不要**给它写 host JVM 单元测试，也不要在 Resolver 的测试里 import 它。

### Step 4 — 面板拖拽

1. 改 `EmoticonChildAdapter`（`design.md` §3.4）：
   - 内部数据源换成 `mCategoryIndex` + `mCategoryId` + `int[] mOrder`。
   - 新增 `setData(int, int[])`、`moveItem(int,int)`、`getOrder()`。
   - tag 拼接、夜间白底 switch、padding、`mHeight / 3`、`RxBus` 事件保持不变。
2. 改 `EmoticonParentAdapter`（`design.md` §3.5）：
   - `instantiateItem` 用 `EmoticonOrderStore.loadOrder(position)` 装配 adapter。
   - 装配 `ItemTouchHelper`：drag 四向、swipe 传 0、`isItemViewSwipeEnabled()` 返回 `false`、
     `onSelectedChanged` 做 `requestDisallowInterceptTouchEvent(true)` + 振动、
     `clearView` 里 `saveOrder`。
   - 删除已无调用者的 `getEmotionList()`。

验证：

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
```

> **审查门 G2**：编译通过后逐行对比新旧 tag 拼接结果，确认
> `"[s:" + id + ":" + 名称 + "]-" + id + "/" + 文件名` 与改前逐字符一致。
> 这是 PRD R5 里最容易悄悄回归的一条。

> 回滚点 R2：Step 2–4 构成「功能可用但无重置入口」的完整状态，可独立成一次提交。

### Step 5 — 设置页重置入口

1. `settings.xml` 的「其他设置」分组内、`key_clear_cache` 之后新增 `<Preference>`（`design.md` §3.6）。
   注意必须是 `<Preference>`，不是 `<PreferenceScreen>`。
2. `SettingsFragment.configPreference()` 挂点击监听，
   `showResetEmoticonOrderDialog()` 用 `AlertDialogFragment.create("确认要重置所有表情的自定义顺序吗？")`，
   positive 回调 `EmoticonOrderStore.resetAll()` + `ToastUtils.success("表情顺序已重置")`。

验证：

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
```

### Step 6 — 全量质量门禁 `[gate]`

```bash
./gradlew :lib_base_common:testDebugUnitTest
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:lintDebug
```

- 单元测试必须全绿且测试数 > 0。
- lint 进程退出码为 0 **不足以**判定通过，必须打开生成的 lint 报告，
  与 Step 1 的基线逐条比对，确认无新增 error。
- 结果写入 `artifacts/quality-gate.md`。

### Step 7 — 交付说明（不做设备验证）

用户 2026-07-28 决定不跑设备自动化测试，也不要求实现方进行真机验收。
**不编译 androidTest、不构建测试 APK、不跑设备测试。**

完成报告中必须把以下项如实标注为「未验证，待用户真机确认」，
**不得**因为编译通过就打勾：

- AC1 长按拖拽后表情停在新位置
- AC2 单击仍正常插入；拖拽结束不会多插入一个表情
- AC3 退出重进 / 重启应用后顺序保持
- AC4 改 A 分类不影响 B 分类
- AC5 横向拖拽不会被 ViewPager 抢成翻页
- AC6 设置页重置：确认生效并提示；取消无变化
- AC12 夜间模式下 ac / a2 / dt 三组表情仍有白底

### Step 8 — 收尾

1. 依据实际实现更新 `.trellis/spec/frontend/component-guidelines.md`，
   补一节「表情面板分类内拖拽排序」契约（存储 key 格式、合并规则、拖拽装配要点）。
2. 提交切分建议：
   - commit 1：`feat: add emoticon order model and store`（Step 2–3）
   - commit 2：`feat: reorder emoticons by drag in the picker`（Step 4）
   - commit 3：`feat: add emoticon order reset to settings`（Step 5）
   - commit 4：`docs: record emoticon reorder contract`（Step 8.1）
3. 走 Phase 3.3 / 3.4。

## 验证命令汇总

```bash
./gradlew :lib_base_common:testDebugUnitTest
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:lintDebug
```

## 回滚

| 点 | 状态 | 回滚方式 |
| --- | --- | --- |
| R0 | 干净 `main` | — |
| R1 | 仅新增纯逻辑 + 测试，无行为变化 | revert commit 1 |
| R2 | 拖拽可用，无重置入口 | revert commit 2 |
| R3 | 功能完整 | revert commit 3 |

任一 gate 失败且无法定位时，回退到最近的回滚点，不要在失败状态上继续叠加改动
（重复调试超过 2 轮请转 `trellis-break-loop`）。
