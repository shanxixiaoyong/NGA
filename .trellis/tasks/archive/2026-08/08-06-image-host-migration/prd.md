# 修复帖子图片无法显示：图床域名迁移至 img.nga.cn

## Goal

帖子正文里的图片、附件、投票配图和短消息配图当前一律加载失败，用户只能靠右上角「用浏览器打开」去看图。
根因是应用把附件地址硬编码到了 `img.nga.178.com`，该域名族已被 NGA 撤销。本任务把图床切到仍在服务的
`img.nga.cn`，把历史帖子里写死的旧域名归一化回来，并在设置里留一个可覆盖入口，避免下次官方再迁域名时
所有已安装用户都必须等新版发布。

## Background

2026-08-06 实测（同一张附件 `/attachments/mon_202608/01/-7Q46-48cvZeT1kShs-12h.jpg`）：

| 域名 | https | http | 结论 |
|---|---|---|---|
| `img.nga.cn` | 200 image/jpeg | 200 image/jpeg | 唯一全能，`.thumb/.medium/.thumb_s` 后缀齐全 |
| `img9.nga.cn` | 404 | 200 image/jpeg | 仅 http 可用，各画质字节数与上者一致 |
| `img1`–`img8.nga.cn` | 404 / 403 | 404 / 403 | 已不再提供附件 |
| `img*.nga.178.com` | DNS 解析失败 | DNS 解析失败 | 整族撤销（`img9` 解析到 `127.0.0.1` 黑洞） |
| `img*.ngacn.cc` | TLS 不通 | 302 → 死域名或 `www.nga.cn` 首页 | 不可用 |
| `cdn/static/pic/attachments.nga.cn`、`bbs.nga.cn`、`nga.178.com` | 404 | 404 | `*.nga.cn` 泛解析回论坛，无附件 |

补充事实：

- **图床按路径族分主机**：`/attachments/`（附件）只有 `img.nga.cn` 与 `img9.nga.cn` 提供；
  `/ngabbs/post/smile/`（表情）只有 `img4.nga.cn` 提供，`img.nga.cn` 对该路径返回 404。
  这决定了旧域名归一化不能一刀切（详见 `design.md` §1.1）。
- 上游 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE` 同样硬编码旧域名，因此这不是本项目二次开发引入的回归。
- `TopicPostModel.java:61` 的**上传**路径早已是 `https://img8.nga.cn/attach.php?`，只有**显示**路径滞留在旧域名。
- `network_security_config.xml` 已全局放开明文流量，`http://` 不会被系统拦截，`img9.nga.cn` 选项因此可行。
- 图床域名与设置里的「NGA 域名」是两套独立体系，官方 App 无论主站走哪个镜像，图片都取自 `img.nga.cn`，二者不应联动。
- 帖子内的表情由 `ForumEmoticonDecoder` 替换为本地 `file:///android_asset/`，不联网，因此表情显示不受本次故障影响。

## Requirements

### R1 图床域名切换（必做）

- 所有附件/正文图片/音视频地址由 `img.nga.178.com`、`img*.ngacn.cc` 改为**当前所选图片域名**（默认 `img.nga.cn`）。
- 协议不再写死 `http://`，改为随所选域名而定（默认项用 https；`img9.nga.cn` 用 http，见 R3）。
- 保留应用既有的画质后缀行为（`.thumb.jpg` / `.medium.jpg` / `.thumb_s.jpg`），实测新域名全部支持。

### R2 旧域名归一化（必做）

- 帖子正文里以 `[img]http://img.nga.178.com/...[/img]` 形式写死旧域名的历史内容，当前是原样透传的，切常量救不回来。
- 解码阶段需把 `img*.nga.178.com`、`img*.ngacn.cc` 的主机名重写为当前可用地址，使历史帖子恢复可见。
- 归一化只改协议与主机名，路径、查询串、画质后缀保持不变。
- **分路径族处理**：`/attachments/` 走当前所选图片域名；其余路径（如表情 `/ngabbs/post/smile/`）保留原编号只换域名后缀，
  因为 `img4.nga.cn` 才有表情资源而 `img.nga.cn` 对该路径返回 404（详见 `design.md` §1.1）。

### R3 图片域名可选（必做）

- 设置页「域名与账号」分组内、「NGA 域名」**正下方**新增**单个**「图片域名」项，样式对齐既有「NGA 域名」
  （`ListSummaryPreference`）。
- **自定义输入框不占独立设置行**：它位于「图片域名」点开的选择页内，跟在「自定义」选项下方。
  设置主界面因此只多一行。
- 选择页内共三个单选项，顺序固定：

  | 序号 | 选项 | 实际地址 |
  |---|---|---|
  | 0 | `img.nga.cn`（默认） | `https://img.nga.cn` |
  | 1 | `img9.nga.cn` | `http://img9.nga.cn` |
  | 2 | `自定义`（排最后） | 由该选项下方的同页输入框提供 |

- **协议随选项固定**：`img.nga.cn` 走 https；`img9.nga.cn` 只能走 http——实测其 https 稳定返回 NGA 自己的
  404 页（三轮复测一致），若强行用 https 该选项等于不可用。
- 选「自定义」时由同页输入框取值；输入需容错：允许 `img.nga.cn`、`https://img.nga.cn`、`https://img.nga.cn/`
  等形式，内部统一归一；未写协议时默认 https。
- 输入框仅在「自定义」被选中时可编辑，其余选项下置灰，避免误以为随时生效。
  → **2026-08-06 二次修正后作废**：改为常开可编辑，点击/聚焦即自动选中「自定义」。
  见下「设置形态经用户复审后修正」第二条。
- 填了非法域名时**当场提示格式错误且不关闭选择页**，不静默保存一个坏值。
- 留空视为未配置，回退默认 `https://img.nga.cn`。任何情况都不得崩溃或产生 `http://null/attachments/...` 这类坏地址
  （运行期兜底仍保留，作为第二道防线）。
- 该项独立于既有的「NGA 域名」设置，不联动。

### R4 覆盖范围（必做）

改动需覆盖下列全部已知调用点，不允许只修其中一处：

| 位置 | 影响面 |
|---|---|
| `lib_core/.../decode/ForumImageDecoder.java:35` | 正文内联 `[img]./xxx[/img]` |
| `nga_phone_base_3.0/.../util/HttpUtil.java:25` | 下面四处的共用常量 |
| `nga_phone_base_3.0/.../util/HtmlUtils.java:177` | 附件「点击显示附件」 |
| `nga_phone_base_3.0/.../util/HtmlUtils.java:154,166` | 音频/视频附件链接 |
| `nga_phone_base_3.0/.../util/StringUtils.java:433-436` | 正文内联图另一条解码路径 |
| `nga_phone_base_3.0/.../util/StringUtils.java:484` | 图集 URL 收集 → `ImageZoomActivity` 大图 |
| `lib_core/.../decode/ForumVoteDecoder.java:63` | 投票的游戏标题图 |
| `lib_core/.../decode/ForumBasicDecoder.java:233,236` | `[flash=video]` / `[flash=audio]` |
| `nga_phone_base_3.0/.../util/StringUtils.java:651,665` | 音视频 html |
| `nga_phone_base_3.0/.../convert/MessageConvertFactory.java:124` | 短消息配图 |
| `lib_bu_message/.../MessageConvertFactory.java:123` | 短消息配图（第二份拷贝） |
| `lib_base_common/.../util/EmoticonUtils.java:19` | `PRR_EMOTION_URL`，实为死代码，一并清理避免误导 |

**执行期补录（2026-08-06）**——下列调用点原表漏收，均为生效代码：

| 位置 | 影响面 | 处置 |
|---|---|---|
| `lib_core/.../data/AttachmentData.java` + `corebuild/HtmlAttachmentBuilder.java:17,29,40` | 附件区显示。`ArticleConvertFactory:163` 把 `HttpUtil.NGA_ATTACHMENT_HOST` 灌进 `AttachmentData.mAttachmentHost`，再由 builder 拼接——整条只是把常量跨模块搬了一趟 | 拆掉该字段，builder 直接取 `NgaImageHost.attachmentsPrefix()` |
| `nga_phone_base_3.0/.../common/ApiConstants.java:5` | 板块图标 fid 版（`ForumBoardView.kt` 经 Glide 加载，**不走解码链**，归一化碰不到） | 改 `img4.nga.cn`，实测 200 |
| `nga_phone_base_3.0/.../common/ApiConstants.java:7` | 板块图标 stid 版（合集板块，同样经 Glide） | 改 `img4.nga.cn`，实测 200 |

> ⚠️ 验证 stid 版时要用 `assets/board_list.json` 里的**真实 stid**（8 位数，如 `12007887`）。
> 拿 1、2、100 之类的小数字去 curl 会一律 404 并返回同一个 146B 页面——那是「无此合集」，
> 不是「路径已废」。执行期曾据此误判为「服务端下掉了 `/proxy/cache_attach/`，换域名无意义」而准备放弃，
> 换真实 stid 后五个全部 200。**返回体大小完全一致**是「打到通用错误页」的信号，不是「路径不存在」的证据。

## Constraints

- 不得改动 `references/` 下的上游参考代码。
- `lib_core` 对 `lib_base_common` 是 `compileOnly` 依赖，共享代码的落点必须尊重这一约束。
- 不引入新的三方依赖。
- 图片最终由 WebView 内核发起请求，方案不得依赖 OkHttp 拦截器之类只作用于 API 请求的机制。
- 不做**失败自动回退**到备用域名（需要在 WebView 里注入 JS `onerror` 钩子，复杂度不成比例）。`img9.nga.cn`
  以**手动可选项**的形式提供，不做自动切换。

## Acceptance Criteria

- [ ] AC1 打开含图帖子，正文内联图片直接显示，无需点右上角「用浏览器打开」。
- [ ] AC2 附件区「点击显示附件」按钮点击后能正常出图。
- [ ] AC3 点击图片进入 `ImageZoomActivity` 大图浏览正常。
- [ ] AC4 正文里写死 `img.nga.178.com` / `img.ngacn.cc` 完整地址的历史帖子同样能显示。
- [ ] AC5 短消息里的图片能显示。
- [ ] AC6 设置「域名与账号」分组内，「图片域名」紧跟在「NGA 域名」之后，且**只多这一行**（自定义输入框不单独占行）；
      点开后的选择页含 `img.nga.cn` / `img9.nga.cn` / `自定义` 三个单选项且顺序如此，默认选中 `img.nga.cn`，
      「自定义」下方带输入框。
- [ ] AC7 三个选项分别生效：选 `img.nga.cn` 走 https 出图；选 `img9.nga.cn` 走 http 出图；选「自定义」并填 `img.nga.cn` 出图。
- [ ] AC8 选择页内输入框：**常开可编辑**，点它或聚焦它即自动选中「自定义」；填 `https://img.nga.cn/`、
      `  img.nga.cn  ` 均生效，留空回退默认，填 `乱码//` 当场报格式错误且选择页不关闭，均不崩溃。
- [ ] AC9 修改「图片域名」不影响「NGA 域名」设置，反之亦然。
- [ ] AC10 全仓 grep 不再有指向 `nga.178.com` / `ngacn.cc` 图床的**生效**代码路径（`references/` 除外）。
- [ ] AC11 `./gradlew assembleDebug` 通过；既有单测（含 `DefaultSettingsContractTest`、`EmoticonUtilsContractTest`）不回归。

## Out of Scope

- 主站「NGA 域名」设置项本身的调整。
- 图片缓存、预加载、画质策略的改动。
- 失败自动回退备用图床。
- 表情包域名体系的重构（仅清理 `PRR_EMOTION_URL` 死常量）。

## Notes

- 用户已确认：默认硬编码 + 设置可选，位置紧贴「NGA 域名」下方，名称「图片域名」。
- 设置形态经用户复审后修正（2026-08-06）：**不做两个并列设置行**。原方案把「自定义图片域名」做成紧邻的
  `EditTextPreference`，导致它在未选「自定义」时仍占一行且常灰着。改为**单行入口 + 二级选择页**，
  三个单选项与自定义输入框同处该页内。
- 二次修正（2026-08-06，5.3.0 真机查看后）：选择页内第三项**不显示「自定义」字样**，
  单选圈与输入框同处一横行——原方案把标签和输入框摞成两行，等于把一件事拆开看两遍。
  输入框 hint 改为可照抄的完整地址 `https://img.nga.cn`，而非描述格式的说明文字。
  输入框同时改为**常开可编辑**：它已经长在该选项上，再要求先点中旁边那个小圈才肯让人打字，
  比被否掉的两行方案更别扭。点击或聚焦输入框即自动选中「自定义」。
  实现代价：布局因此不能用 `RadioGroup`（它只对直接子节点做互斥，第三项的圈嵌进横向容器后就出了其管辖范围，
  会导致三项可同时选中），互斥改由 `ImageDomainDialogFragment` 手工维护。
- 复现路径：任意含图帖子；对照组为右上角 `menu_open_by_browser`（`ArticleTabFragment.java:172`）打开的内嵌浏览器，那里走 NGA 自己的 HTML，图片正常。
