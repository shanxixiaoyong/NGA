# 执行计划：图床域名迁移与可覆盖

> 前置：`prd.md`（需求与验收）、`design.md`（技术设计）已定稿。
> 本文件只管执行顺序、校验命令与回滚点。

## 校验命令速查

```bash
# 单模块单测（改完对应模块就跑）
./gradlew :lib_base_common:testDebugUnitTest
./gradlew :lib_core:testDebugUnitTest
./gradlew :nga_phone_base_3.0:testDebugUnitTest

# 本任务涉及的四个模块（--continue 免得一个失败掩盖其余）
./gradlew :lib_base_common:testDebugUnitTest :lib_core:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest :lib_bu_message:testDebugUnitTest --continue

# 编译
./gradlew assembleDebug

# 残留域名扫描（AC10）
grep -rnE "img[0-9]*\.(nga\.178\.com|ngacn\.cc)" --include=*.java --include=*.kt --include=*.xml . \
  | grep -v references/ | grep -v /build/
```

> ⚠️ **`./gradlew test` 与 `./gradlew testDebugUnitTest` 在本机都跑不通**，与本任务无关：
> 前者会连带配置 release 变体，要求 `ANDROID_SIGNING_*` 四个环境变量；
> 后者会撞上 `lib_base_ui` / `lib_bu_statistics`——这两个模块有测试文件但没声明 junit 依赖。
> 用上面按模块点名的命令替代。

---

## Step 1 — 建立 `NgaImageHost` 与契约测试

**改动**
- 新增 `lib_base_common/src/main/java/gov/anzong/androidnga/common/util/NgaImageHost.java`，按 design §3.1 实现
  `DEFAULT_BASE_URL` / `EMOTICON_HOST` / `PRESET_BASE_URLS` / `attachmentBaseUrl()` /
  `attachmentsPrefix()` / `sanitizeBaseUrlInput()` / `normalizeLegacyHosts()` / `invalidate()`。
- `PreferenceKey` 新增 `KEY_IMAGE_DOMAIN = "pref_image_domain"` 与
  `KEY_IMAGE_DOMAIN_CUSTOM = "pref_image_domain_custom"`。
- `lib_base_common/res/values/arrays.xml` 新增 `image_domain` / `image_domain_value` 两个数组。
- 新增 `lib_base_common/src/test/java/gov/anzong/androidnga/common/util/NgaImageHostContractTest.java`，
  覆盖 design §6 列出的全部用例（含 `PRESET_BASE_URLS` 与 `arrays.xml` 的序号对齐断言）。

**要点**
- `attachmentBaseUrl()` 的 try 必须捕 `Throwable`（design §1.3），否则 Step 3 会炸 `lib_core` 单测。
- `PRESET_BASE_URLS[1]` 是 `http://img9.nga.cn`——**不要**顺手统一成 https，实测其 https 稳定 404。
- 归一化正则严格按 design §3.1 两条规则，规则 1 用前瞻不消费路径。

**校验**
```bash
./gradlew :lib_base_common:testDebugUnitTest
```
预期：新增契约测试全绿，`EmoticonUtilsContractTest` / `EmoticonOrderResolverTest` 无回归。

**回滚点 A**：此步纯新增（除 `PreferenceKey` 一行），删除新文件即可完全回退。

---

## Step 2 — 接入设置项

**改动**
- `nga_phone_base_3.0/src/main/res/xml/settings.xml`：「域名与账号」分组内，`nga_domain` **之后**、
  `pref_user_compose` **之前**，按 design §3.2 插入**一个** `ListSummaryPreference`
  （`pref_image_domain`，默认 `"0"`）。`pref_image_domain_custom` 不进 settings.xml。
- `nga_phone_base_3.0/src/main/res/values/strings.xml`：新增 `setting_image_domain`（「图片域名」）、
  `setting_image_domain_custom_hint`、`setting_image_domain_custom_invalid`。
- 新增 `nga_phone_base_3.0/src/main/res/layout/dialog_image_domain.xml`：`RadioGroup` 三项
  （文案取 `R.array.image_domain`）+ 第三项下方缩进的 `EditText`。
- 新增 `sp/phone/ui/fragment/dialog/ImageDomainDialogFragment.java`：按 design §3.2 的行为清单实现。
- `SettingsFragment`：重写 `onDisplayPreferenceDialog`，命中 `pref_image_domain` 时弹自绘对话框；
  对话框关闭后刷新该行 summary。
- `DefaultSettingsContractTest` 同步更新（design §6）：
  - `settingsAreGroupedByPurpose`「域名与账号」期望键序改为 `nga_domain`, `pref_image_domain`, `pref_user_compose`；
  - `preferenceDefaultsMatchStandardSettings` 增加 `pref_image_domain` = `"0"`；
  - 新增 `customImageDomainIsNotItsOwnSettingsRow`，断言 `pref_image_domain_custom` 不在 settings.xml 里。

**要点**
- `DefaultSettingsContractTest` 会因插入新项而先失败——这正是它的作用（锁定分组与顺序）。
  按上面改期望值即可，**不要**为了让测试变绿而把新项挪出「域名与账号」分组。
- 自定义值的键**不要**加进 `settings.xml`。它没有界面行；加了就退回被否决的两行方案，
  `removedPreferencesAreNotExposed` 会当场拦住。
- `RadioButton` 文案从 `R.array.image_domain` 取，不要在布局里写死第二份，否则选项列表变成三处漂移。

**校验**
```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest
```

**回滚点 B**

---

## Step 3 — 改造 `lib_core` 解码器

**改动**
- `ForumImageDecoder.java`：删除 `NGA_ATTACHMENT_HOST` 常量；`decode(content, htmlData)` 开头插入
  `content = NgaImageHost.normalizeLegacyHosts(content);`；`REPLACE_IMG_NO_HTTP` 的拼接改用
  `NgaImageHost.attachmentsPrefix()`，协议转 https。
- `ForumVoteDecoder.java:63`：改用 `NgaImageHost.attachmentsPrefix()`。
- `ForumBasicDecoder.java:233,236`：`http://img.ngacn.cc/attachments` → `NgaImageHost.attachmentsPrefix()`。

**校验**
```bash
./gradlew :lib_core:testDebugUnitTest
```
⚠️ `ExampleUnitTest.testQuote` 在 `main` 上**本来就是红的**（`NoClassDefFoundError: StringUtils`，
因 `lib_base_common` 是 `compileOnly`，不在单测 classpath 上），与本任务无关。
判定标准是**失败原因与基线一致**，不是变绿——用 `git stash -u` 对照即可确认。
详见 design §1.3 的「实测更正」：别再试 `testImplementation project(':lib_base_common')`，那条路已走过，修不好。

**回滚点 C**

---

## Step 4 — 改造 `nga_phone_base_3.0` 工具类

**改动**
- `HttpUtil.java:25`：**删除** `NGA_ATTACHMENT_HOST`（design §4 常量内联坑）。
- `HtmlUtils.java:154,166,177`：三处拼接改用 `NgaImageHost.attachmentsPrefix()`。
- `StringUtils.java:433-436`：改用 `NgaImageHost.attachmentsPrefix()`；
  `decodeForumTag` 开头插入 `ret = NgaImageHost.normalizeLegacyHosts(ret);`。
- `StringUtils.java:484`：图集收集判断放宽为「含 `attachments/`」。
- `StringUtils.java:651,665`：音视频域名改用 `NgaImageHost.attachmentsPrefix()`。

**要点**：删掉 `HttpUtil.NGA_ATTACHMENT_HOST` 后编译器会报出**全部**引用点，正好当作覆盖度检查表——
编译通过即说明 R4 中属于本模块的调用点都处理到了。

> 这一招当场抓出 R4 漏收的一条链：`ArticleConvertFactory:163` 把该常量灌进
> `AttachmentData.mAttachmentHost`，再由 `lib_core/corebuild/HtmlAttachmentBuilder`
> 拼成附件地址。该字段唯一的作用就是把常量跨模块搬一趟，已整条拆除。
> 附件区显示走的正是这条链，靠 grep 域名字面量是找不到它的。

**校验**
```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew assembleDebug
```

**回滚点 D**

---

## Step 5 — 短消息与表情常量

**改动**
- `nga_phone_base_3.0/.../convert/MessageConvertFactory.java:124`
- `lib_bu_message/.../MessageConvertFactory.java:123`
  两份拷贝同步改为 `NgaImageHost.attachmentsPrefix() + "/mon_"`。
- `lib_base_common/.../EmoticonUtils.java:19`：`PRR_EMOTION_URL` 是**死常量**（全仓仅声明处），
  按 PRD R4 的「一并清理」直接删除，而非改域名——改一个无人读的常量只是把误导换个新地址。
  我自己加的 `NgaImageHost.EMOTICON_HOST` 同理删除：归一化规则 2 靠保留编号就落到 `img4`，常量无人引用。

**要点**：两份 `MessageConvertFactory` 是历史遗留的重复实现，**必须两份都改**，只改一份会留下半修复状态。

**校验**
```bash
./gradlew :lib_bu_message:testDebugUnitTest :lib_base_common:testDebugUnitTest
```

**回滚点 E**

---

## Step 6 — 全量校验与残留扫描

```bash
./gradlew :lib_base_common:testDebugUnitTest :lib_core:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest :lib_bu_message:testDebugUnitTest --continue
./gradlew assembleDebug
grep -rnE "img[0-9]*\.(nga\.178\.com|ngacn\.cc)" --include=*.java --include=*.kt --include=*.xml . \
  | grep -v references/ | grep -v /build/
```

grep 的预期**不是零输出**，而是逐条判定后只剩两类无害命中：

1. `NgaImageHostContractTest` 里的旧域名——那是归一化的**输入样本**，删了测试就废了；
2. `HtmlUtils.java:129`、`StringUtils.java:655` 两处注释里的旧地址范例。

生效代码必须零命中。执行期该扫描抓出 `ApiConstants.java:5,7` 两条真问题（详见 prd.md 补录表）。

---

## Step 7 — 真机/模拟器验收

按 `prd.md` 的 AC1–AC11 逐条走查，重点：

- **AC1** 含图帖子正文直接出图（对照 URL
  `https://img.nga.cn/attachments/mon_202608/01/-7Q46-48cvZeT1kShs-12h.jpg` 实测 200）。
- **AC4** 找一个正文里写死 `img.nga.178.com` 完整地址的老帖验证归一化。
- **AC6** 确认设置主界面「图片域名」就在「NGA 域名」下一栏，且**只多这一行**；
  点开后的弹窗内三个单选项顺序为 `img.nga.cn` / `img9.nga.cn` / `自定义`。
- **AC7** 三个选项逐个切换验证出图；`img9.nga.cn` 走的是 http，注意确认没有被改成 https。
- **AC8** 弹窗内输入框：未选「自定义」时应置灰；选中后依次填 `https://img.nga.cn/`、`  img.nga.cn  `、
  空白、`乱码//`，确认生效 / 生效 / 回默认 / **当场报错且弹窗不关**，均不崩。
- **AC9** 改「图片域名」后确认「NGA 域名」设置未受影响。
- 附带：板块列表的板块图标（含合集板块）应正常显示——这是 R4 补录的调用点。

## Step 8 — 收尾

- `.trellis` 规范更新（Phase 3.3）：已写入
  `backend/nga-platform-access-rules.md`（图床按路径族分主机 + 探测时要用真实标识符）与
  `guides/code-reuse-thinking-guide.md`（`public static final String` 常量内联坑 + 死常量应删不应改）。
- release notes：`release-notes/5.3.0.md`，`scripts/validate_release_notes.py` 已通过。
- 提交并推送 tag `5.3.0`（Phase 3.4）。推 tag 即触发 `.github/workflows/build.yml` 出正式包；
  版本号由 CI 从 tag 推导，**不需要**改 `build.gradle` 里的 `localAppVersionName`。

---

## 风险登记

| 风险 | 触发点 | 应对 |
|---|---|---|
| 常量内联导致设置项失效 | Step 4 保留 `HttpUtil.NGA_ATTACHMENT_HOST` | 直接删除常量，强制编译期暴露引用 |
| 把 `img9.nga.cn` 统一成 https | Step 1 图省事对齐协议 | 其 https 稳定 404；`PRESET_BASE_URLS` 注释标注，契约测试锁死 |
| `lib_core` JVM 单测崩 | Step 3 | Step 1 的 `Throwable` 兜底；不用 mock 绕过 |
| 一刀切归一化打死表情路径 | Step 1 正则写错 | 规则 2 保留编号；契约测试锁死 `img4` 用例 |
| 误伤主站域名 | 正则未锚 `img` 前缀 | 契约测试加 `nga.178.com` / `bbs.ngacn.cc` 不变更用例 |
| 选项列表两处漂移 | `arrays.xml` 与 `PRESET_BASE_URLS` 各改各的 | 契约测试断言二者长度与序号对齐 |
| 只改一份 `MessageConvertFactory` | Step 5 | Step 6 的 grep 扫描兜底 |
| 官方再次迁移图床 | 上线后 | 「自定义」选项可覆盖，用户无需等发版 |
