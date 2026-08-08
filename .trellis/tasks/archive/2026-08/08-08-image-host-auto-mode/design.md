# 技术设计：图片域名自动模式与页面级附件前缀

## 1. 设计结论

采用“用户策略 + 页面级服务端值”的混合解析：

```text
THREAD.PAGE data.__GLOBAL._ATTACH_BASE_VIEW
                    │
                    ▼
          ArticleConvertFactory（每页提取一次）
                    │ raw server value
                    ▼
             NgaImageHost（策略解析）
        ┌───────────┴─────────────────┐
        │ 自动：解析服务端值          │ 手动：忽略服务端值
        │ 非法/缺失 → 固定安全前缀    │ 预设或自定义前缀
        └───────────┬─────────────────┘
                    │ full attachments prefix
                    ▼
                 HtmlData
        ┌───────────┼───────────────┬──────────────┐
        ▼           ▼               ▼              ▼
 Forum decoders  attachment builder  comments/signatures  image URL list
```

核心边界是：`_ATTACH_BASE_VIEW` 是一次 `THREAD.PAGE` 响应的页面数据，不是全局配置。
它只在自动模式中参与解析，解析后立即收敛为完整前缀并随 `HtmlData` 向下传递。

## 2. 选项与持久化契约

### 2.1 模式值

| UI 位置 | 展示 | 存储值 | 行为 |
|---:|---|---:|---|
| 0 | `自动` | `0` | 帖子页使用合法服务端值；无上下文或非法值时使用固定安全前缀 |
| 1 | `https://img.nga.cn` | `1` | 始终使用 `https://img.nga.cn/attachments` |
| 2 | `http://img9.nga.cn` | `2` | 始终使用 `http://img9.nga.cn/attachments` |
| 3 | 自定义输入项 | `3` | 使用合法自定义地址，否则回退默认 |

`settings.xml`、`PreferenceUtils.getData` 和非法值兜底统一以 `0` 为默认。UI 位置与存储值保持一致，
现有对话框直接持久化单选 index 的机制可以继续使用，只需扩为四项并把 custom index 改为 3。

### 2.2 一次性升级迁移

旧版本已经使用同一个偏好键保存 `0/1/2`，不能只替换数组后直接解释，否则旧 img9 会被当成新默认、
旧自定义会被当成新 img9。项目已有 `VersionUpgradeHelper.upgradeSettings()`，迁移放在该统一入口：

| 旧存储值 | 旧含义 | 新存储值 | 升级后含义 |
|---:|---|---:|---|
| 缺失 | 从未配置 | `0` 或继续缺失 | 自动 |
| `0` | 默认 `img.nga.cn` | `0` | 自动（用户要求升级后切换） |
| `1` | `img9.nga.cn` | `2` | 仍为 img9 |
| `2` | 自定义 | `3` | 仍为自定义，原输入键不变 |
| 其他 | 损坏/未知 | `0` | 自动 |

新增独立 schema version 或 boolean 完成标记。只有标记缺失时才读取旧值并迁移，写入新值与标记应在同一个
`SharedPreferences.Editor` 事务中完成。新安装没有旧值时只写完成标记；后续每次启动均直接跳过。

此策略有意让旧值 0（无论是默认落盘还是用户主动选择）进入自动，满足“升级后切换成自动”；
旧 img9 和自定义属于明确的非默认选择，因此保留其行为。

## 3. `NgaImageHost` 契约

### 3.1 权威常量

建议把“数组 index”命名改成明确的模式常量，避免再次混淆 UI 位置：

```java
MODE_AUTO = 0;
MODE_DEFAULT = 1;
MODE_IMG9 = 2;
MODE_CUSTOM = 3;

DEFAULT_BASE_URL = "https://img.nga.cn";
DEFAULT_ATTACHMENTS_PREFIX = "https://img.nga.cn/attachments";
```

预设 URL 仍按模式值索引或由显式 `switch` 解析；自动不是一个预设 URL。

### 3.2 对外解析入口

保留无上下文入口，并新增带服务端值的入口：

```java
attachmentsPrefix()
attachmentsPrefix(@Nullable String serverAttachmentBaseView)
normalizeLegacyHosts(String content)
normalizeLegacyHosts(String content, String attachmentsPrefix)
```

行为矩阵：

| 当前模式 | `serverAttachmentBaseView` | 结果 |
|---|---|---|
| 自动 | 合法 | 服务端值规范化后的完整附件前缀 |
| 自动 | null/空白/非法 | `DEFAULT_ATTACHMENTS_PREFIX` |
| 默认预设 | 任意 | `https://img.nga.cn/attachments` |
| img9 预设 | 任意 | `http://img9.nga.cn/attachments` |
| 自定义 | 任意 | 合法自定义值 + `/attachments`，否则默认前缀 |
| 缺少 Android Context/偏好读取异常 | 有合法服务端值 | 按默认自动模式使用服务端值 |
| 缺少 Android Context/偏好读取异常 | 无合法服务端值 | `DEFAULT_ATTACHMENTS_PREFIX` |

实现中应保留一个不依赖 Android 的纯解析函数，供 JVM 契约测试覆盖模式、custom 和 server 三个输入的组合。

### 3.3 服务端字段解析

`_ATTACH_BASE_VIEW` 最终必须规范成不带尾斜杠的完整附件前缀。

允许的代表性输入：

| 输入 | 输出 |
|---|---|
| `img.nga.cn` | `https://img.nga.cn/attachments` |
| `img.nga.cn/` | `https://img.nga.cn/attachments` |
| `img.nga.cn/attachments` | `https://img.nga.cn/attachments` |
| `https://img.nga.cn` | `https://img.nga.cn/attachments` |
| `https://img.nga.cn/attachments/` | `https://img.nga.cn/attachments` |
| `//img.nga.cn/attachments` | `https://img.nga.cn/attachments` |
| `http://img9.nga.cn` | `http://img9.nga.cn/attachments` |

解析规则：

1. 输入必须是字符串，先 trim；裸主机和 `//host` 默认补 HTTPS。
2. 仅接受 HTTP/HTTPS；保留服务端显式给出的 HTTP。
3. 主机沿用现有自定义地址的合法字符约束，拒绝空主机、userinfo、空格和非法字符。
4. 只允许无路径、尾斜杠或 `/attachments[/]`；查询、fragment 和其他路径视为非法，避免把非附件基址误当附件主机。
5. 输出统一为 `scheme://host[:port]/attachments`。
6. 任一步失败均返回 null，由自动模式转换为固定安全前缀，不向上抛异常。

不要使用上游的 `data.split("/")[0]`：它会丢失协议信息，对 `https://...` 得到空字符串，也无法区分非法输入。

### 3.4 缓存边界

- 可以保留现有偏好派生的无上下文缓存及 `invalidate()`，以减少旧入口重复读取偏好；
  自动模式的无上下文结果是固定安全前缀。
- `attachmentsPrefix(serverValue)` 在自动模式中必须绕过静态 URL 缓存，现场解析当前页的 `serverValue`。
- 服务端原始值和解析结果均不得写入 `static` 字段、SharedPreferences 或 `ThreadRowInfo` 持久状态。
- `ArticleConvertFactory` 每个页面只解析一次，然后把同一个不可变字符串传给该页全部行。

这样页面 A 和页面 B 即使并行转换，也不会互相覆盖主机值。

## 4. 页面级数据流

### 4.1 `ArticleConvertFactory`

在 `buildThreadRowList(JSONObject data)` 中：

1. 以 null-safe 方式读取 `data.getJSONObject("__GLOBAL")`；
2. 若存在，再读取字符串 `_ATTACH_BASE_VIEW`；
3. 调用 `NgaImageHost.attachmentsPrefix(rawServerValue)` 得到完整前缀；
4. 把完整前缀作为参数传给 `convertJsObjToList`；
5. 递归构建贴条/评论行时继续传同一个前缀；
6. `buildHtmlData` 将它写入 `HtmlData`。

不采用上游给 `ThreadRowInfo` 新增 `attachmentHost` 的做法：该值是页面渲染上下文，没必要进入业务行模型、缓存或序列化边界。
直接参数传递也能明确保证一页一个值。

### 4.2 `HtmlData`

新增私有字段及访问器，例如：

```java
private String mAttachmentsPrefix;

public void setAttachmentsPrefix(String value);
public String getAttachmentsPrefix();
```

getter 的兼容契约：字段未设置时返回 `NgaImageHost.attachmentsPrefix()`。该入口在手动模式返回所选前缀，
在自动模式返回固定安全前缀，因此以下旧调用仍安全：

- `new HtmlData(raw)`；
- `HtmlData.create(raw, host)`；
- JVM 测试构造的最小 `HtmlData`；
- 个人资料、独立签名、旧工具类和没有 `__GLOBAL` 的缓存路径。

自动模式下这些无上下文路径使用固定安全前缀；手动模式仍尊重用户选择。

### 4.3 消费者

| 消费者 | 改动 |
|---|---|
| `ForumImageDecoder` | 相对图片使用 `htmlData` 的最终前缀；历史附件绝对 URL 使用带前缀的归一化重载 |
| `ForumBasicDecoder` | `[flash=video]` / `[flash=audio]` 使用最终前缀 |
| `ForumVoteDecoder` | 游戏/评分相对图片使用最终前缀 |
| `HtmlAttachmentBuilder` | 图片、音频、视频和 image URL list 全部使用最终前缀；内部 helper 接收同一值 |
| `HtmlCommentBuilder` | 已复用同一个 `HtmlData`，无需另存字段；内部解码自然继承页面前缀 |
| `HtmlSignatureBuilder` | 同上 |

`StringUtils.decodeForumTag`、`HtmlUtils`、两份 `MessageConvertFactory` 等无页面上下文入口继续调用无参
`NgaImageHost.attachmentsPrefix()` / `normalizeLegacyHosts(content)`，不伪造服务端字段；自动模式由无参入口返回固定安全前缀。

## 5. 历史绝对地址与路径族

现有两阶段归一化保持不变，但附件阶段增加页面前缀参数：

1. 匹配 `img*.nga.178.com` / `img*.ngacn.cc` 的 `/attachments/` 前缀，替换为当前页面最终前缀；
2. 再处理其余旧图床地址，只把后缀改成 `.nga.cn` 并保留 `img4` 等编号。

建议让附件正则消费到 `/attachments`，然后直接替换为完整 prefix，避免从完整 prefix 反向切主机：

```text
http://img6.nga.178.com/attachments/mon_x/a.jpg
└──────── legacy attachment prefix ────────┘
                    ↓
https://server-selected.example/attachments/mon_x/a.jpg
```

路径、查询串、`.thumb.jpg` / `.medium.jpg` 等后缀不得变化。当前可用的绝对地址不做泛化重写；
板块图标 `img4.nga.cn` 继续由 `ApiConstants` 字面量管理，因为 Glide 路径不经过帖子解码链。

## 6. 设置 UI

### 6.1 资源

`arrays.xml`：

```text
image_domain       = [自动, https://img.nga.cn, http://img9.nga.cn, 自定义]
image_domain_value = [0,    1,                      2,                    3]
```

`settings.xml` 的 `pref_image_domain` 默认值保持/明确为 `0`，但其新含义变为自动。主设置页位置与行数不变。

### 6.2 对话框

- `dialog_image_domain.xml` 在现有默认单选项上方新增 `rb_image_domain_auto`。
- `RADIO_IDS` 按 UI 顺序包含四项。
- 文案继续从 `image_domain` 读取；自定义项仍不重复显示「自定义」文字，只保留无障碍描述。
- 自定义判断改用新的 `MODE_CUSTOM = 3`。
- 保存成功后仍由宿主把 `ListPreference` 内存值同步为实际存储值，summary 显示 `自动` 或所选项。

## 7. 测试设计

### 7.1 `NgaImageHostContractTest`

新增或调整：

- 选项文案顺序与 entryValues 精确为 `[0,1,2,3]`；
- `MODE_AUTO = 0` 是默认值，`MODE_CUSTOM = 3`；
- 服务端字段表格中的合法形式全部规范成完整前缀；
- null、空白、错误 scheme、userinfo、查询/fragment、其他路径、非法字符由自动模式回退固定安全前缀；
- 自动模式使用合法服务端值，无上下文使用固定安全前缀；
- 三种手动模式忽略服务端值；
- 两次不同服务端输入依次解析得到不同结果，证明没有静态串值；
- 页面级 legacy attachment 归一化使用传入 prefix；非附件路径继续保号；
- 无 Android Context 时不抛异常。

### 7.2 设置契约

`DefaultSettingsContractTest`：

- `pref_image_domain` 默认值断言保持 `0`，并由 resolver/数组契约证明其含义已是自动；
- 主设置页键顺序不变；
- 自定义键仍不成为独立 Preference 行。

增加 `VersionUpgradeHelper` 迁移测试或纯迁移函数测试，覆盖缺失、旧 `0/1/2`、损坏值和重复执行；
同时锁住 UI 四项与存储值按序一致。

### 7.3 `ArticleConvertFactory` 契约

将“从 `JSONObject data` 提取并解析页面 prefix”保留为 package-private、无 Android 调用的小函数，添加 JVM 测试覆盖：

- 有合法 `__GLOBAL._ATTACH_BASE_VIEW`；
- `__GLOBAL` 缺失；
- 字段缺失、非字符串或非法；
- 解析失败得到固定安全 prefix，且不影响帖子页其余 JSON。

编译与代码审查再确认同一 prefix 被传入普通行和递归评论行，且没有写入 `ThreadRowInfo` 或静态字段。

## 8. 兼容、风险与回滚

### 8.1 兼容

- 新安装、缺失值和损坏值进入自动。
- 首次升级时旧 `0` 进入自动；旧 `1/2` 迁为 `2/3`，保留 img9/自定义行为。
- 迁移标记保证编号只转换一次。自定义地址仍沿用原键，不复制、不清空。
- 降级到旧版本会重新按旧语义读取同一数值，可能与新版本选择不一致；若发布后需要回滚 APK，回滚版本应包含
  反向迁移（新 `0/1 → 旧 0`、新 `2 → 旧 1`、新 `3 → 旧 2`），不能只机械 revert resolver。

### 8.2 主要风险

| 风险 | 防护 |
|---|---|
| 旧值直接被新数组误解释 | `VersionUpgradeHelper` 一次性迁移 + schema 标记 + 迁移测试 |
| 迁移重复执行导致值持续增加 | 新值与完成标记同事务写入，重复执行测试 |
| `__GLOBAL` 缺失导致整页失败或拼出 `null` URL | null-safe 提取 + 固定安全前缀 |
| 页面 A 的主机污染页面 B | 服务端值只用局部参数和 `HtmlData`，禁止静态缓存 |
| 只修正文相对图，其他链路半修复 | `HtmlData` 统一前缀 + 消费者清单 + 编译/静态扫描 |
| 把表情/板块图标压到附件主机 | 保留路径族规则，板块图标不接自动前缀 |
| img9 被改为 HTTPS | 模式常量与契约测试锁定 HTTP |
| 服务端格式未来变化 | 纯解析函数、明确合法形态、非法 fail-safe；不以异常中断页面 |

### 8.3 回滚

代码提交可以 revert，但已执行的偏好编号迁移不是天然可逆。发布前回滚可清理测试数据；发布后的 APK 回滚必须在回滚构建中
先执行上节所述反向映射。服务端结果本身没有持久化，因此无需清理页面主机缓存或数据库。
