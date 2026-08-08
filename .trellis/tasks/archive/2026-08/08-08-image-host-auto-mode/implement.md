# 执行计划：图片域名自动模式与页面级附件前缀

> 前置：本任务仍处于 planning。只有用户审核并明确批准本规划后，才运行 `task.py start` 和修改产品代码。

## Step 1 — 先锁定模式、解析与兼容契约

修改 `NgaImageHostContractTest`，先覆盖以下预期：

- 四项 UI 文案与存储值顺序：`自动/默认/img9/自定义` 对应 `0/1/2/3`；
- 新默认模式为 `0`；
- 一次性升级映射：缺失/旧 `0` → 新 `0`，旧 `1` → 新 `2`，旧 `2` → 新 `3`，损坏值 → 新 `0`；
- 迁移完成后再次执行不改变值；
- 服务端字段合法/非法输入表；
- 自动模式与三个手动模式的决策矩阵；
- 自动模式缺失/非法/无上下文时返回固定安全前缀；
- 页面 A/B 不同输入不串值；
- 带页面前缀的历史附件 URL 归一化；
- 无 Android Context 安全返回服务端前缀或固定安全前缀。

保持 `DefaultSettingsContractTest` 对 `pref_image_domain=0` 的期望，并增加模式含义、四项顺序与升级迁移契约测试。

**验证**

```bash
./gradlew :lib_base_common:test :nga_phone_base_3.0:testDebugUnitTest --continue
```

预期在产品实现前，新断言按计划失败；不得通过放宽断言绕过。

**回滚点 A**：只含测试期望变化。

## Step 2 — 扩展 `NgaImageHost`

修改：

- `lib_base_common/.../PreferenceKey.java`
- `lib_base_common/.../util/NgaImageHost.java`
- `lib_base_common/src/main/res/values/arrays.xml`
- `nga_phone_base_3.0/.../VersionUpgradeHelper.java`

执行：

1. 新增明确的 `MODE_DEFAULT/IMG9/CUSTOM/AUTO` 常量，停止把模式称为 dropdown index。
2. 资源数组调整为四项，entryValues 固定为 `0/1/2/3`。
3. 增加纯函数，按 mode + custom + server raw 解析完整附件前缀。
4. 增加带 server raw 的 `attachmentsPrefix` 入口；自动模式解析当前值，手动模式完全忽略它。
5. 保留无参入口，自动模式无上下文时返回固定安全前缀；手动模式仍返回对应前缀。
6. 增加带完整 prefix 的 legacy 归一化重载；保留无参重载供消息和旧入口使用。
7. 保证服务端结果不写静态缓存；如保留偏好缓存，只允许缓存无上下文/手动派生结果。
8. 在 `VersionUpgradeHelper.upgradeSettings()` 增加带 schema 标记的一次性迁移；旧 0 切到自动，旧 1/2 分别迁到 2/3。
9. 新值和迁移完成标记使用同一个 editor 事务写入，自定义输入键保持原样。

**验证**

```bash
./gradlew :lib_base_common:test
```

**回滚点 B**：resolver 与资源层可独立回退；此时尚未改页面数据流。

## Step 3 — 给 `HtmlData` 增加页面级前缀并改核心消费者

修改：

- `lib_core/.../data/HtmlData.java`
- `lib_core/.../decode/ForumImageDecoder.java`
- `lib_core/.../decode/ForumBasicDecoder.java`
- `lib_core/.../decode/ForumVoteDecoder.java`
- `lib_core/.../corebuild/HtmlAttachmentBuilder.java`

执行：

1. `HtmlData` 增加完整附件前缀字段；getter 在未设置时调用无上下文 resolver。
2. `ForumImageDecoder` 使用该前缀构造相对图，并将它传给 legacy 归一化。
3. `ForumBasicDecoder` 与 `ForumVoteDecoder` 使用同一前缀。
4. `HtmlAttachmentBuilder` 在一次 build 中读取一次 prefix，再传给图片/音频/视频 helper，图片列表记录同一最终 URL。
5. 确认 `HtmlCommentBuilder` 与 `HtmlSignatureBuilder` 继续复用原 `HtmlData`，不复制解析逻辑。

**验证**

```bash
./gradlew :lib_core:testDebugUnitTest --continue
```

`lib_core:ExampleUnitTest.testQuote` 存在既有 Android/JVM 基线；若仍失败，核对失败形态与任务前基线一致，不为本任务添加无关依赖或禁用测试。

**回滚点 C**：core 数据流和消费者一起回退，避免字段存在但调用点仍读全局值。

## Step 4 — 在 `ArticleConvertFactory` 接入 `THREAD.PAGE.__GLOBAL`

修改：

- `nga_phone_base_3.0/.../convert/ArticleConvertFactory.java`
- 新增或扩展对应 JVM 契约测试

执行：

1. 从顶层 `data` null-safe 提取 `__GLOBAL._ATTACH_BASE_VIEW`。
2. 通过 `NgaImageHost` 每页解析一次完整 prefix。
3. 给 `convertJsObjToList`、递归 `buildRowComment`、`buildRowContent` 和 `buildHtmlData` 增加局部 prefix 参数。
4. 在 `HtmlData` 上设置该值后再进入 `HtmlConvertFactory`。
5. 不给 `ThreadRowInfo` 增加 attachment host 字段，不持久化或静态保存页面结果。
6. 增加 present/missing/malformed/non-string `__GLOBAL` 测试。

**验证**

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest
```

**回滚点 D**：Article parser 接线可独立回退；core 仍会走安全无上下文前缀。

## Step 5 — 把设置弹窗改成四项并同步新编号

修改：

- `nga_phone_base_3.0/.../dialog/ImageDomainDialogFragment.java`
- `nga_phone_base_3.0/src/main/res/layout/dialog_image_domain.xml`
- `nga_phone_base_3.0/src/main/res/xml/settings.xml`
- 必要的 strings 与设置契约测试

执行：

1. 在布局首项加入 `自动` 单选按钮，保持自定义输入项结构不变。
2. `RADIO_IDS` 调整为四个 UI 位置。
3. 同时读取 `image_domain` 和 `image_domain_value`；保存/恢复均通过 entryValues 映射。
4. 自定义判断改为新 index/模式 `3`。
5. `settings.xml` 与 `SettingsFragment` 的缺省值保持 `0`，其含义改为自动。
6. 保持输入框聚焦自动选中自定义、错误不关闭、只占一行主设置入口等现有行为。

**验证**

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
```

**回滚点 E**：UI、resolver 与迁移必须一同回退；若迁移已在发布用户上执行，需要反向偏好迁移，不能只还原数组。

## Step 6 — 审计无上下文入口与路径族

逐项确认，不给没有 `THREAD.PAGE.__GLOBAL` 的路径伪造页面值：

- 两份 `MessageConvertFactory`；
- `sp.phone.util.HtmlUtils`；
- `sp.phone.util.StringUtils.decodeForumTag`；
- `ProfileActivity` / `FunctionUtils` 的签名渲染；
- 旧缓存/测试中直接构造的 `HtmlData`；
- `ApiConstants` 的 fid/stid 板块图标。

必要时只调整调用到新的无上下文兼容入口。自动模式使用固定安全前缀，手动模式照常展开。
板块图标必须继续是 `img4.nga.cn`，上传主机不动。

**静态检查**

```bash
rg -n "_ATTACH_BASE_VIEW|attachmentsPrefix|normalizeLegacyHosts|pref_image_domain" \
  lib_base_common lib_core nga_phone_base_3.0 lib_bu_message
rg -n "img4\.nga\.cn/(ngabbs/nga_classic/f/app|proxy/cache_attach/ficon)" \
  nga_phone_base_3.0/src/main
```

**回滚点 F**：若审计暴露旧入口回归，优先恢复其无参 resolver，不扩大为新的全局状态。

## Step 7 — 完整质量门

### 聚焦单测

```bash
./gradlew :lib_base_common:test \
  :lib_core:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest \
  :lib_bu_message:testDebugUnitTest \
  --continue
```

### 编译

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
```

### Lint

```bash
./gradlew :lib_base_common:lintDebug \
  :lib_core:lintDebug \
  :nga_phone_base_3.0:lintDebug \
  :lib_bu_message:lintDebug \
  --continue
```

检查生成的 lint 报告，不能只看进程退出码；既有无关问题与本任务新增问题分开记录。

### 最终代码审查清单

- 四项 UI 顺序和 `0/1/2/3` 映射一致；
- 所有默认读取均为 `0`，并解析为自动；
- 旧 `0/1/2` 仅迁移一次为 `0/2/3`，img9 和自定义行为不丢失；
- 自动只在有页面 server raw 时采用服务端值；
- 三个手动模式忽略 server raw；
- server raw/result 不在 static、SharedPreferences 或 `ThreadRowInfo`；
- 正文、投票、音视频、附件、评论、签名使用同一 `HtmlData` 前缀；
- 消息和旧入口在自动模式下使用固定安全前缀，在手动模式下仍正常展开；
- legacy 非附件路径保号，板块图标保持 img4；
- 没有新增 NGA 网络测试或 ADB 操作。

## Step 8 — 设备与线上验证状态

本任务默认不运行 ADB、安装、instrumentation、真机/模拟器 E2E，也不发起新的 NGA 在线探测。
按项目策略记录为“未运行（无本任务新授权）”，不构成交付失败或要求用户补做的阻塞项。

若后续用户另行明确授权真机验收，再单独制定只读、限量且不触发真实 NGA 写操作的验证步骤。
