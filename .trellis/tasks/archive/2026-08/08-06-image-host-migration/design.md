# 技术设计：图床域名迁移与可覆盖

## 1. 现状与约束

### 1.1 域名事实（2026-08-06 实测）

图床按**路径族**分布在不同主机上，这是本设计最关键的约束：

| 路径族 | 可用主机 | 不可用 |
|---|---|---|
| `/attachments/...`（附件、正文图） | `img.nga.cn`（http+https）、`img9.nga.cn`（**仅 http**） | `img1`–`img8.nga.cn` 全 404/403 |
| `/ngabbs/post/smile/...`（表情） | `img4.nga.cn` | `img.nga.cn` 及其余编号主机全 404 |

⚠️ 因此**不能**把所有旧域名一刀切重写成 `img.nga.cn`：那会让 `/ngabbs/post/smile/` 从「域名已死」变成「404」，等于没修。

⚠️ `img9.nga.cn` 的 https 稳定返回 NGA 自己的 404 页（三轮复测一致，返回体是 `NGACN - HTTP Error 404` 的 gb2312 页面），
只有 http 可用。**协议必须与主机绑定，不能全局固定成 https。**

`img*.nga.178.com` 与 `img*.ngacn.cc` 两族均已不可用（前者 DNS 撤销，后者 302 到死域名或论坛首页）。

### 1.2 模块依赖

```
nga_phone_base_3.0 ──┐
lib_bu_message ──────┼──► lib_base_common   (implementation)
lib_core_data ───────┘
lib_core ────────────────► lib_base_common   (compileOnly)
```

`lib_base_common` 是三个改动模块唯一的公共下游，且已含 `PreferenceKey` / `PreferenceUtils` / `EmoticonUtils`，是共享代码的自然落点。
`lib_core` 是 `compileOnly` 依赖，运行期由 app 模块提供实现——`ForumImageDecoder` 已经在 import `gov.anzong.androidnga.common.util.EmoticonUtils`，该路径已被验证可行。

### 1.3 JVM 单测约束（关键）

`lib_core/src/test/.../ExampleUnitTest.testQuote` 在**纯 JVM** 上直接调用 `ForumBasicDecoder.decode()`，而 `ForumBasicDecoder:233,236` 正是本次要改的音视频域名所在处。
若新工具类在类初始化或取值时触达 `ContextUtils.getContext()` / `PreferenceUtils`（Android framework，JVM 上是 `Stub!`），该测试会直接崩。

→ **硬性要求**：主机名解析必须在无 Android 环境下静默回退到内置默认值，不得抛异常。

> **实测更正（2026-08-06，Step 3 执行时）**：上述推理的结论（要兜底）成立，但它设想的验证机制不成立——
> `ExampleUnitTest.testQuote` 在 `main` 上**本来就是红的**，且与图床无关：
> `lib_base_common` 对 `lib_core` 是 `compileOnly`，压根不在单测运行时 classpath 上，
> 测试在加载 `StringUtils` 时就 `NoClassDefFoundError`，到不了被测逻辑。
> 试着补 `testImplementation project(':lib_base_common')` 也修不好——`StringUtils` 的静态初始化块
> （`StringUtils.java:23`）要读 Android 资源，裸 JVM 上 `ContextUtils.getContext()` 为 null 直接 NPE，
> 只是把失败形态从「类找不到」换成「类初始化炸」。该依赖已撤回，不留无效改动。
>
> 因此：**`lib_core` 的单测不是本设计兜底逻辑的守门人**，别指望它变绿。兜底由
> `NgaImageHostContractTest.attachmentBaseUrlFallsBackToDefaultWithoutAndroid` 覆盖——
> 它在 `lib_base_common` 里跑，那里 `PreferenceUtils` 的静态初始化同样会炸，正好是要兜的那个 `Error`。
> 修 `testQuote` 需要 Robolectric 或给 `ContextUtils` 播种，属另一件事。

### 1.4 渲染链约束

图片最终由 WebView 内核发起请求，不经过 OkHttp。因此方案只能作用在**生成 HTML 字符串的解码阶段**，拦截器类手段无效。

## 2. 方案总览

新增单一权威工具类 `NgaImageHost`（`lib_base_common`），承担三件事：

1. 提供当前**附件主机名**（设置覆盖 → 默认 `img.nga.cn`）；
2. 提供拼接好的附件 base url；
3. 把内容里的**遗留主机名**按路径族归一化。

所有散落的域名常量收敛到它，调用点不再各自持有字符串。

```
                       ┌──────────────────────────────┐
 pref_image_domain ───►│  NgaImageHost                │
   (0/1/2, 默认0)      │   attachmentBaseUrl()        │──► lib_core/decode/*
 pref_image_domain_ ──►│   attachmentsPrefix()        │──► nga_phone_base/util/*
        custom         │   normalizeLegacyHosts(text) │──► lib_bu_message/*
   (仅 index==2 生效)  └──────────────────────────────┘
                                 │
              0→https://img.nga.cn  1→http://img9.nga.cn  表情→img4.nga.cn
```

## 3. 详细设计

### 3.1 `NgaImageHost`（新增，`lib_base_common`）

包：`gov.anzong.androidnga.common.util`

```java
public final class NgaImageHost {

    /** 附件默认地址；实测唯一 http+https 双通的附件源。 */
    public static final String DEFAULT_BASE_URL = "https://img.nga.cn";

    /** 表情资源主机；与附件主机不同族，见 design §1.1。 */
    public static final String EMOTICON_HOST = "img4.nga.cn";

    /**
     * 下拉选项 index → 附件 base url。协议与主机绑定（§1.1）：
     * img9 只能走 http。index 2 是「自定义」，值由 KEY_IMAGE_DOMAIN_CUSTOM 提供。
     */
    static final String[] PRESET_BASE_URLS = {
            "https://img.nga.cn",   // 0 默认
            "http://img9.nga.cn",   // 1 仅 http 可用
            null,                   // 2 自定义
    };

    /** 生效的附件 base url，如 "https://img.nga.cn"。 */
    public static String attachmentBaseUrl();

    /** attachmentBaseUrl() + "/attachments" */
    public static String attachmentsPrefix();

    /** 把用户输入归一成完整 base url；非法/空白返回 null。 */
    public static String sanitizeBaseUrlInput(String raw);

    /** 把 content 中的遗留图床主机按路径族重写为当前可用地址。 */
    public static String normalizeLegacyHosts(String content);

    /** 设置项变更时由 SettingsFragment / PhoneConfiguration 调用。 */
    public static void invalidate();
}
```

> **为什么 URL 映射放 Java 常量而不是 `arrays.xml`**：该映射是行为而非文案，且 `lib_core` 需要在
> **无 Android 环境的 JVM 单测**里走到它（§1.3）。放资源里就无法被 JVM 测试覆盖。
> `arrays.xml` 只保留下拉的展示文案与 value，与 `nga_domain` 的观感保持一致；
> 两者的漂移由契约测试锁住（§6）。

**取值与缓存**

```java
private static volatile String sCachedBaseUrl;   // null = 未解析

public static String attachmentBaseUrl() {
    String cached = sCachedBaseUrl;
    if (cached != null) return cached;
    String resolved = DEFAULT_BASE_URL;
    try {                                     // §1.3：JVM 单测无 Android 环境
        int index = Integer.parseInt(
                PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN, "0"));
        if (index >= 0 && index < PRESET_BASE_URLS.length) {
            String preset = PRESET_BASE_URLS[index];
            if (preset != null) {
                resolved = preset;
            } else {                          // 自定义
                String custom = sanitizeBaseUrlInput(
                        PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN_CUSTOM, ""));
                if (custom != null) resolved = custom;
            }
        }
    } catch (Throwable ignored) {
        // 无 Android context（单测）、偏好读取失败或 index 非数字，一律回退默认值
    }
    sCachedBaseUrl = resolved;
    return resolved;
}

public static void invalidate() { sCachedBaseUrl = null; }
```

用 `Throwable` 而非 `Exception` 是因为 JVM 上访问 Android stub 抛的是 `RuntimeException("Stub!")` 的同时，
`PreferenceKey` 的静态初始化（`PreferenceManager.getDefaultSharedPreferencesName(ContextUtils.getContext())`）
可能抛 `ExceptionInInitializerError`（属 `Error`）。`Integer.parseInt` 的 `NumberFormatException` 也一并被兜住。

**`sanitizeBaseUrlInput` 契约**（对应 PRD R3/AC8）

| 输入 | 输出 |
|---|---|
| `null` / `""` / `"   "` | `null`（→ 用默认值） |
| `img.nga.cn` | `https://img.nga.cn`（未写协议默认 https） |
| `https://img.nga.cn` | `https://img.nga.cn` |
| `http://img9.nga.cn/` | `http://img9.nga.cn`（保留用户指定的 http） |
| `  img.nga.cn/attachments  ` | `https://img.nga.cn` |
| `img.nga.cn:8080` | `https://img.nga.cn:8080`（保留端口） |
| `乱码//` | `null` |

实现：trim → 剥离并记住协议头（缺省 https）→ 截断首个 `/` `?` `#` 之前 →
用 `^[A-Za-z0-9.\-]+(:\d+)?$` 校验主机部分 → 不匹配返回 `null` → 拼回 `<scheme>://<host>`。

**`normalizeLegacyHosts` 契约**（对应 PRD R2/AC4）

两条规则，顺序敏感：

```
规则1（附件族）：https?://img\d*\.(nga\.178\.com|ngacn\.cc)(?=/attachments/)
              → <attachmentBaseUrl()>          // 含协议，如 https://img.nga.cn

规则2（其余族）：https?://img(\d*)\.(nga\.178\.com|ngacn\.cc)
              → https://img$1.nga.cn           // 保留编号，仅换域名后缀
```

- 规则 1 用**前瞻** `(?=/attachments/)` 而非消费，避免吞掉路径。
- 规则 2 保留 `img4` → `img4.nga.cn`，正好落到表情主机上（§1.1）。`img` 无编号时得到 `img.nga.cn`。
- 主机名锚定在 `img` 前缀上，**不会**误伤论坛主站 `nga.178.com`、`bbs.ngacn.cc`（它们无 `img` 前缀）。
- 只改主机名，路径 / 查询串 / `.thumb.jpg` 等画质后缀原样保留。

### 3.2 设置项

对齐既有「NGA 域名」的实现形态（`ListSummaryPreference` + `arrays.xml` 索引值），
但**对话框自绘**：设置主界面只多「图片域名」一行，三个单选项与自定义输入框都在点开后的选择页内。

> **为什么不用两个并列的 Preference**：初版设计把自定义输入框做成紧邻的 `EditTextPreference`，
> 于是它在未选「自定义」时仍占一行、还得常灰着——一个设置需求撑出两行界面。
> 用户复审后否决。自定义值是「自定义」这个选项的参数，不是独立设置，理应待在同一层里。

**`PreferenceKey` 新增**

```java
public static final String KEY_IMAGE_DOMAIN        = "pref_image_domain";
public static final String KEY_IMAGE_DOMAIN_CUSTOM = "pref_image_domain_custom";
```

**`lib_base_common/res/values/arrays.xml` 新增**（紧邻既有 `nga_domain` 系列）

```xml
<string-array name="image_domain">
    <item>https://img.nga.cn</item>
    <item>http://img9.nga.cn</item>
    <item>自定义</item>
</string-array>

<string-array name="image_domain_value">
    <item>0</item>
    <item>1</item>
    <item>2</item>
</string-array>
```

展示文案即完整 base url，与既有 `nga_domain`（`https://bbs.ngacn.cc` 等）的写法保持一致。
协议对用户可见是有意的——`img9` 只能走 http，藏起来反而会让人误以为是笔误。
该数组与 `NgaImageHost.PRESET_BASE_URLS` 须逐字对应，由契约测试锁死。

**`settings.xml`**：「域名与账号」分组内，`nga_domain` **之后**、`pref_user_compose` **之前**插入**一项**：

```xml
<ListSummaryPreference
    android:defaultValue="0"
    android:dialogTitle="@string/setting_image_domain"
    android:entries="@array/image_domain"
    android:entryValues="@array/image_domain_value"
    android:key="pref_image_domain"
    android:title="@string/setting_image_domain" />
```

保留 `ListSummaryPreference` 而非换成裸 `Preference`，是为了继续白拿三件事：
`android:defaultValue` 的首次写入、选中项回显为 summary、以及 `DefaultSettingsContractTest` 现有的断言形态。
`pref_image_domain_custom` **不出现在 `settings.xml` 里**——它没有对应的界面行，只是选择页内输入框的存储位置。

`strings.xml` 新增：`setting_image_domain` =「图片域名」、`setting_image_domain_custom_hint`（输入框 hint）、
`setting_image_domain_custom_invalid`（格式错误提示）。

**选择页 `ImageDomainDialogFragment`（新增）**

`SettingsFragment` 重写 `onDisplayPreferenceDialog(Preference)`：命中 `pref_image_domain` 时弹出自绘对话框并
`return`，其余键交给 `super`。这样默认的 `ListPreference` 单选对话框被替换掉，而 Preference 本身的
默认值 / 回显 / 持久化仍走框架。

布局 `dialog_image_domain.xml`：`RadioGroup` 三个 `RadioButton`（文案取 `R.array.image_domain`，
不另写死一份），第三个下方缩进一个 `EditText`（`inputType="textUri"`）。

行为：

- 打开时按 `pref_image_domain` 勾选对应项，输入框回填 `pref_image_domain_custom`；
- `EditText.setEnabled(checkedIndex == INDEX_CUSTOM)`——未选「自定义」时置灰，
  切换单选项时联动，避免让人误以为输入框随时生效；
- 点确定时：
  - 选中「自定义」且输入**非空但非法**（`sanitizeBaseUrlInput` 返回 `null`）→ `setError` 并**不关闭**，
    不静默存坏值；
  - 否则写入 `pref_image_domain`（经 `ListPreference.setValue` 以触发 summary 回显）与
    `pref_image_domain_custom`（原样存用户输入，归一化留给读取侧），调用 `NgaImageHost.invalidate()` 后关闭。

> 对话框直接读写 `PreferenceUtils`，不持有 `Preference` 引用，因此旋转重建后不会拿到失效对象；
> 回写 summary 由宿主 fragment 在 `onDismiss` 时按 key 重新 `findPreference` 完成。

`android:dependency` 只支持布尔型 Preference，无法表达「index == 2 才启用」，故输入框的启停用代码控制。

新项与 `nga_domain` 无任何读写耦合（AC9）。

### 3.3 调用点改造

| 文件 | 改法 |
|---|---|
| `lib_core/.../ForumImageDecoder.java:35,44` | 删除 `NGA_ATTACHMENT_HOST` 常量，改用 `NgaImageHost.attachmentsPrefix()`；`decode()` 开头插入 `content = NgaImageHost.normalizeLegacyHosts(content)` |
| `lib_core/.../ForumVoteDecoder.java:63` | `https://img.nga.178.com/attachments` → `NgaImageHost.attachmentsPrefix()` |
| `lib_core/.../ForumBasicDecoder.java:233,236` | `http://img.ngacn.cc/attachments` → `NgaImageHost.attachmentsPrefix()` |
| `nga_phone_base_3.0/.../HttpUtil.java:25` | **删除** `NGA_ATTACHMENT_HOST`，全部引用改用 `NgaImageHost.attachmentsPrefix()`（见 §4 常量内联） |
| `nga_phone_base_3.0/.../HtmlUtils.java:154,166,177` | 改用 `NgaImageHost.attachmentsPrefix()`，协议随选项而定，不再写死 `http://` |
| `nga_phone_base_3.0/.../StringUtils.java:433-436` | 同上；并在 `decodeForumTag` 开头插入归一化 |
| `nga_phone_base_3.0/.../StringUtils.java:484` | 图集收集的主机判断改为「命中当前主机或任一遗留主机」 |
| `nga_phone_base_3.0/.../StringUtils.java:651,665` | 音视频 `img.ngacn.cc` → `NgaImageHost.attachmentsPrefix()` |
| `.../convert/MessageConvertFactory.java:124` | `http://img6.nga.178.com/attachments/mon_` → `NgaImageHost.attachmentsPrefix() + "/mon_"` |
| `lib_bu_message/.../MessageConvertFactory.java:123` | 同上（第二份拷贝，必须同步改） |
| `lib_base_common/.../EmoticonUtils.java:19` | `PRR_EMOTION_URL` 域名改为 `https://img4.nga.cn/ngabbs/post/smile/` |

> `StringUtils.java:484` 那处原本是 `s1.indexOf(HttpUtil.NGA_ATTACHMENT_HOST) != -1` 才收进图集。改造后若只认新主机，历史帖子里已被归一化的地址仍能命中（因为归一化在前），但为稳妥应放宽为「包含 `attachments/`」的判断。

### 3.4 归一化的插入位置

`ForumDecoder.sDecoderPool` 顺序为
`Basic → Vote → Album → Emoticon → Image → Dice`。

归一化放在 `ForumImageDecoder.decode()` 开头，理由：

- 它在链的靠后位置，此时 `[img]` 完整 URL 形式已定型；
- `MessageConvertFactory` 会先把 `[img]./mon_` 展开成带主机的绝对地址再送进解码链，因此该处也能被同一次归一化覆盖；
- 表情已由 `ForumEmoticonDecoder` 在上一步替换成 `file:///android_asset/`，不会被规则 2 波及。

`StringUtils.decodeForumTag` 是另一条并行的老解码路径，需独立插入同一次归一化。

## 4. 决策与取舍

| 议题 | 决策 | 理由 |
|---|---|---|
| 附件主机可配置形态 | 下拉三选（`img.nga.cn` / `img9.nga.cn` / `自定义`）+ 自定义输入框 | 用户指定；对齐既有「NGA 域名」的交互观感。实测可用候选确实只有这两个，第三项兜住未来迁移 |
| 协议 | **随选项绑定**，不全局固定 | `img9.nga.cn` 的 https 稳定 404（§1.1）；固定 https 会让该选项形同虚设 |
| 选项 URL 映射的存放位置 | Java 常量 `PRESET_BASE_URLS`，不放 `arrays.xml` | 需被 `lib_core` 的 JVM 单测覆盖；资源查表在无 Context 环境不可用（§1.3） |
| 自定义输入框的显隐 | 代码控制 `setEnabled`，不用 `android:dependency` | `dependency` 只支持布尔型 Preference |
| 表情主机是否可配置 | 否，常量 `img4.nga.cn` | 表情走本地 asset，远程仅在极端边缘出现；增设第三个设置项收益为负 |
| 失败自动回退 `img9.nga.cn` | 不做 | 图片由 WebView 加载，需注入 JS `onerror` 钩子，复杂度不成比例（PRD Out of Scope）；已用手动选项替代 |
| `HttpUtil.NGA_ATTACHMENT_HOST` | 直接删除并改所有引用 | 它是 `public static final String`，编译期会被**内联**到各调用点，保留转发常量会造成「改了设置但旧值仍生效」的隐蔽 bug |

> ⚠️ `HttpUtil.NGA_ATTACHMENT_HOST` 的**常量内联**是本次最容易踩的坑：Java 会把 `public static final String` 字面量编译进引用方的 class 文件。必须改成方法调用，否则设置项形同虚设。

## 5. 兼容性与回滚

- **数据兼容**：`pref_image_domain` 默认 `"0"`，老用户升级后即默认 `https://img.nga.cn`，无迁移逻辑。
- **降级**：自定义填错 → `sanitizeBaseUrlInput` 返回 `null` → 回退 `DEFAULT_BASE_URL`，图片仍可用。
- **回滚**：改动集中在解码阶段、一个新类和两个设置项，`git revert` 单个提交即可完整回退；
  用户已写入的两个偏好键会成为孤儿键，无副作用。

## 6. 测试策略

新增 `lib_base_common/src/test/.../NgaImageHostContractTest.java`，沿用仓内既有 `*ContractTest` 约定：

- `sanitizeBaseUrlInput` 全部 §3.1 表格用例；
- `normalizeLegacyHosts` 覆盖：附件族改写、表情族保号改写（`img4.nga.178.com` → `img4.nga.cn`）、
  主站域名不误伤（`nga.178.com` / `bbs.ngacn.cc` 原样）、画质后缀保留、查询串保留、`null`/空输入；
- 无 Android 环境下 `attachmentBaseUrl()` 返回 `DEFAULT_BASE_URL` 且不抛异常（守住 §1.3）；
- `PRESET_BASE_URLS` 与 `arrays.xml` 的 `image_domain` / `image_domain_value` **长度与序号对齐**，
  防止两处选项列表漂移（解析 xml 断言，手法同 `DefaultSettingsContractTest`）。

需同步更新 `nga_phone_base_3.0/src/test/.../DefaultSettingsContractTest.java`：

- `settingsAreGroupedByPurpose` 中「域名与账号」的期望键序改为
  `nga_domain`, `pref_image_domain`, `pref_user_compose`——只多一行；
- `preferenceDefaultsMatchStandardSettings` 增加 `pref_image_domain` = `"0"` 的断言；
- 新增 `customImageDomainIsNotItsOwnSettingsRow`：断言 `pref_image_domain_custom` **不在** `settings.xml` 里。
  它是选择页内输入框的存储键，不该作为独立设置行出现——这条正是用来钉住「不要退回两行方案」。

回归面：`lib_core` 的 `ExampleUnitTest`、`lib_base_common` 的 `EmoticonUtilsContractTest` 必须继续通过。
