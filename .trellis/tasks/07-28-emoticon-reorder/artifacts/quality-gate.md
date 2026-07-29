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
**未编译 androidTest、未构建测试 APK、未运行任何设备测试。**

AC1–AC6、AC12 为交付后由用户真机确认项，当前状态一律为「未验证」，
详见 `prd.md` 验收标准第二段。
