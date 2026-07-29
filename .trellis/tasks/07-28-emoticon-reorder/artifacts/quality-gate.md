# Quality Gate — 07-28-emoticon-reorder

日期：2026-07-29
命令：`./gradlew :lib_base_common:testDebugUnitTest :nga_phone_base_3.0:testDebugUnitTest
:nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:lintDebug`
Gradle 退出码：**0**

## 单元测试

| 模块 | tests | failures | errors |
| --- | --- | --- | --- |
| `lib_base_common` | 32 | 0 | 0 |
| `nga_phone_base_3.0` | 68 | 0 | 0 |

测试数 > 0，非「零测试」的运行器启动失败。

本次新增 31 个测试：

- `EmoticonOrderResolverTest` — 23 个
- `EmoticonUtilsContractTest` — 8 个

### 修改的既有测试

`sp.phone.common.DefaultSettingsContractTest#settingsAreGroupedByPurpose`
在首次门禁运行中失败（`DefaultSettingsContractTest.java:191`）。

- 原因：该契约测试把「其他设置」分组的 key 列表钉死为
  `[pref_black_list_new, key_clear_cache]`，本任务按 PRD R4 新增了
  `key_reset_emoticon_order`。
- 处置：把新 key 追加进期望列表。这是**有意的契约变更**，不是绕过测试——
  该测试的作用正是强制设置页结构变动被显式承认。
- 未放宽任何断言，未禁用任何测试。

## Lint

`./gradlew :nga_phone_base_3.0:lintDebug`

> 注意：lint 进程退出码为 0 **不足以**判定通过（规范：`android-quality-guidelines.md`），
> 以下为与 Step 1 基线的逐条比对结果。

| 指标 | 基线（改动前） | 改动后 | 结论 |
| --- | --- | --- | --- |
| total issues | 734 | 734 | 持平 |
| errors | 11 | 11 | **无新增** |
| warnings | 722 | 722 | 持平 |

error 构成两边完全一致：
`FragmentLiveDataObserve` 6、`WebViewLayout` 3、`MissingSuperCall` 1、`UseRequireInsteadOfGet` 1。
与 `android-quality-guidelines.md` 记录的「固定上游树 11 个既有 app lint error」吻合。

本次改动文件中的 lint 命中（均为既有 warning，非新增）：

- `EmoticonChildAdapter.java:90` `UseCompatLoadingForDrawables`
  —— 来自原有的 `mContext.getDrawable(...)` 一行，本任务原样保留未改。

## 专项验证

### 审查门 G1 — key 选型前提

`EmoticonUtilsContractTest#fileNamesAreUniqueWithinEachCategory` 通过：
6 个分类共 238 个表情，分类内文件名与表情名均唯一。按文件名持久化的前提成立。

### 审查门 G2 — 插入 payload 逐字符一致

脚本对全部 238 个表情比对改动前后的 tag 与 asset 路径构造公式：

```
checked 238 emoticons across 6 categories
tag mismatches      : 0
assetPath mismatches: 0
```

样例：tag = `[s:ac:blink]-ac/ac0.png`，asset = `ac/ac0.png`。

## 未执行项

按用户 2026-07-28 决定及 `android-quality-guidelines.md` 设备测试策略：
**未编译 androidTest、未构建测试 APK、未运行任何自动化设备测试。**

## 真机验收（2026-07-29）

用户手动验收，非自动化。设备通过 Windows ADB 连接，符合
`android-quality-guidelines.md` 的单一设备传输规则：

```
adb.exe = /mnt/c/Users/inter/AppData/Local/Android/Sdk/platform-tools/adb.exe
device  = REDACTED_SERIAL_XIAOMI  (product:dada  model:24129PN74C)
package = com.github.tophtab.ngajustworks.debug  (versionName 4.5.0 / versionCode 4050)
```

### 第一轮：AC5 失败

长按后横向拖拽被外层 `ViewPager` 抢走变成切换分类。

根因是实现中那行「防止 ViewPager 抢手势」的保护代码本身：对 `RecyclerView`
调用 `requestDisallowInterceptTouchEvent(true)` 会先通知所有
`OnItemTouchListener`，而 `ItemTouchHelper` 就在其中，收到后执行
`select(null, ACTION_STATE_IDLE)` 当场取消拖拽。

根因通过反编译 `androidx.recyclerview:recyclerview:1.1.0` 字节码确认，
非推测。完整证据链见 `prd.md` 缺陷记录。

### 第二轮：全部通过

删除该调用后重新构建安装（`lastUpdateTime=2026-07-29 10:08`），
用户确认 AC1–AC6、AC12 全部通过。

### 过程记录：并行会话改写了历史

验收期间另一会话重写了本分支的提交（SHA 全变），并覆盖了当时尚未提交的修复。
已核对本任务源码文件在改写前后内容零差异，修复已重新施加。
本记录保留以说明为何提交 SHA 与实现时不一致。
