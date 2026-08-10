# `THREAD.PAGE` / `read.php` 研究账本（脱敏）

本账本只记录仓库代码、规范和本地抓包的结构性事实，不复制 HAR body、Cookie、用户名、头像或帖子正文。所有字段语义都按证据等级表达：网页 HAR 不是原生 `__output=8` JSON 的字段全集。

## 1. 请求证据

| 事实 | 证据 | 结论 |
| --- | --- | --- |
| 原项目帖子请求为 `/read.php?page=<page>&__output=8&noprefix&v2` | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:49-66` | 操作 ID 为 `THREAD.PAGE`，请求 builder 的最小固定参数是 page/output/noprefix/v2 |
| 当前 builder 只追加 `tid`、`pid`、`authorid` | 同上 | `searchpost` 是已存在的请求意图但当前未发送 |
| 搜索结果跳转会保存 `pid`、`authorId`、`searchPost` | `TopicSearchFragment.java:272-281` | 搜索回复→详情的值丢失发生在 URL 构造层 |
| URI/Intent 入口已经读取 `searchpost` | `ArticleListActivity.java:47-67` | 不需要另改深链入口才能修复这条链 |

## 2. 已消费的数据层

| 层级 | 当前读取的字段 | 当前处理 |
| --- | --- | --- |
| envelope | `data` | `ArticleConvertFactory` 直接取 JSON `data`；外层 `encode/time` 未形成模型 |
| page | `__T`, `__ROWS`, `__R__ROWS`, `__R`, `__U` | 主题元数据、行数、行表、用户表映射到旧 DTO |
| page context | `__GLOBAL._ATTACH_BASE_VIEW` | 现有并发 image-host 任务已负责解析；本任务不得复制 resolver |
| row | tid/fid/author/authorid/subject/content/postdate/pid/lou、匿名/禁言/签名/声望/客户端等 | 映射到 `ThreadRowInfo` 并生成 HTML |
| nested row | `comment`、`17` | 评论树已渲染；热门回复列表已解析但没有产品消费者 |
| media/interaction | `attachs`、`vote`、`alterinfo` | 附件只向 `AttachmentData` 下沉 URL/thumb；vote/alterinfo 主要用于当前 HTML |

主要代码证据：`ArticleConvertFactory.java:43-100,118-140,145-201,204-235,262-324`、`ThreadData.java:9-59`、`ThreadPageInfo.java:7-187`、`Attachment.java`、`lib_core/.../AttachmentData.java:3-24`、`lib_core/.../CommentData.java:3-44`。

## 3. 已确认的损失与脆弱点

1. `__ROWS` 与 `__R__ROWS` 直接强转 `Integer`；长整型、字符串数字或缺失会进入整页异常路径（`ArticleConvertFactory.java:65,94`）。
2. `__T` 缺失时返回 null，详情页仍在 `ArticleListFragment.java:293-295` 直接访问 `getSubject()`。
3. 楼主识别依赖当前页首行 `lou == 0`（`ArticleListFragment.java:297-305`），搜索/作者过滤页可能没有楼主行。
4. 文件缓存路径仅由 `tid/page` 构成（`ArticleListModel.java:125-129,141-145`），没有 pid、authorid、searchpost、账号或协议/解析版本。
5. parser 与共享 converter 存在完整 body 日志（`ArticleConvertFactory.java:61,71-74` 及 `JsonStringConvertFactory.java`），与 network-foundation 隐私契约冲突。

## 4. HAR 边界

`.temp/bbs.nga.cn.har` 中的 `/read.php?tid=...` 是网页 HTML 响应，包含网页脚本变量（可见 `_ATTACH_BASE_VIEW` 等），并非 `__output=8` JSON。它可用于验证网页 URL/HTML fallback 的存在，不能用于推断原生 JSON 的 `__CU`、`__T` 或行字段全集。原生 JSON fixture 必须由用户授权的本地脱敏捕获或人工构造的最小形状提供；真实 HAR 保留在本地，不进入 Git。

## 5. 授权低频原生请求观察（2026-08-08）

用户明确授权后，使用无 Cookie 的低频只读请求验证了原生形态：

```text
https://bbs.nga.cn/read.php?&page=1&__output=8&noprefix&v2&tid=<redacted-local-thread>
```

仅记录结构，不保存 body：

- HTTP `200`；`Content-Type: text/javascript; charset=GBK`；正文首个非空字符为 `{`。
- 严格 `json.loads` 在一个字符串控制字符处失败，说明这是 NGA 的 JSON-like/JavaScript 数据，不是可直接假定为 RFC JSON 的 REST 响应。
- 脱敏键扫描确认存在 `data`、`data.__GLOBAL`、`data.__GLOBAL._ATTACH_BASE_VIEW`、`data.__U`、`data.__GROUPS`、`data.__R` 以及行级 `content/tid/pid/lou` 等结构。
- 服务端返回了会话相关 `Set-Cookie`，请求端没有保存、回传或写入项目文件；后续 fixture 不得包含这些值。
- 对后续低频样本套用原项目 `ArticleConvertFactory` 的现有修补规则并使用项目同版 Fastjson `1.1.71.android` 时，仍出现 `unclosed str` 解析失败。该观察只证明这个匿名样本不能被当前 parser 稳定消费，不代表所有响应都失败；但它确认“HTTP 200 + `{` 开头”不能当作成功，parser 必须保留 typed failure/fallback。

已观察到的脱敏字段层级如下，值均未保留：

| 层级 | 字段 |
| --- | --- |
| 页面上下文 | `__GLOBAL._ATTACH_BASE_VIEW` |
| 用户表特殊项 | `__GROUPS`、`__MEDALS`、`__REPUTATIONS` |
| 用户记录 | `uid`、`username`、`credit`、`medal`、`reputation`、`groupid`、`memberid`、`avatar`、`yz`、`site`、`honor`、`regdate`、`mute_time`、`postnum`、`rvrc`、`money`、`thisvisit`、`signature`、`nickname`、`bit_data`，以及可选 `buffs/group_bit_buf/remark` |
| 楼层记录 | `content`、`subject`、`tid/fid/pid/authorid/lou`、`type`、`score/score_2`、`recommend`、`postdate/postdatetimestamp`、`content_length`、`alterinfo`、`from_client`、可选 `reply_to/attachs` |
| 附件记录 | `attachurl`、`size`、`type`、`subid`、`url_utf8_org_name`、`dscp`、`path`、`name`、`ext`、`thumb`、`hash` |

`__T`、`__ROWS`、`__R__ROWS`、`__CU` 等仍有原项目代码证据和用户先前响应证据，但本次匿名响应在完整尾部结构可验证前已经进入 malformed/parse-failure 边界，因此不能把它用作这些字段的当前完整样本。

### 当前利用矩阵

| 数据 | 当前利用 | 明显损失/空缺 | 建议优先级 |
| --- | --- | --- | --- |
| `__T` 主题元数据 | 已映射到 `ThreadPageInfo`，详情页主要只读标题 | author/authorId、page/pid/position、fid/type、postDate/lastPoster 等没有成为详情页可靠语义来源 | 高：先用于楼主识别和定位，其他后置 |
| `__R` 楼层核心字段 | content/subject、tid/fid/pid/uid/lou、postdate、score、attachs、客户端等已渲染 | `reply_to`、`postdatetimestamp`、`content_length`、`score_2`、row `type/recommend` 未形成稳定模型 | 中高：引用回溯/时间锚点优先，其余需证实语义 |
| `__U` 用户表 | username/avatar、禁言、声望、签名、发帖数、组名、buff mute 已使用 | medal/`__MEDALS`、`__REPUTATIONS`、site/honor/regdate/nickname/remark/bit_data 等未消费 | 低到中：有明确资料 UI 再接入 |
| `comment` | 可递归生成 HTML | 下沉到 `CommentData` 后只剩作者、正文、头像、时间；pid/uid/lou/引用关系不可导航 | 中：评论折叠/跳转任务再补 |
| `attachs` | `Attachment` 已解析完整字段，HTML 层只保留 URL/thumb | 原文件名、描述、大小、类型、aid/subid 等在展示 DTO 丢失 | 中高：下载/分享/媒体 UI 的必要基础，但非当前渲染必需 |
| row `17` 热门回复 | 已解析成 `hotReplies` | 没有任何消费者 | 中：产品入口独立评估 |
| vote | 作为原始字符串进入 HTML renderer | 没有稳定结构化投票模型 | 中低：只有重做投票展示/交互时需要 |
| `__GLOBAL` | `_ATTACH_BASE_VIEW` 已由 image-host 任务接入 | 其他键没有当前证据/消费者 | 已覆盖当前高价值字段 |
| `__CU`、外层 `encode/time` | `THREAD.PAGE` 当前不保留 | 能力提示/诊断元数据未利用，但语义不完整且不能作为授权 | 低：先记录，不产品化 |
| 错误/畸形响应 | parse failure 退化为 null、换账号重试或 WebView | HTTP 200、HTML/challenge、malformed JSON 没有可靠 typed failure | 高：属于稳定性和隐私底座 |

这次观察把 `__GLOBAL._ATTACH_BASE_VIEW` 从“用户提供的原生响应事实”提升为一次明确授权、低频、脱敏的当前观察；它仍不代表字段全集或接口稳定性。

## 6. 依赖与边界

- `THREAD.PAGE` 注册表：`.trellis/spec/backend/nga-platform-operation-registry.md:36-43`。
- 网络身份、编码、raw response、错误分类：`.trellis/spec/backend/network-foundation-contract.md` 与 `.trellis/spec/backend/nga-platform-access-rules.md`。
- 读模型/分页/Room 的后续消费者：`.trellis/tasks/07-25-nga-android-reading-favorites/`。
- 图片页面级基址：`.trellis/tasks/08-08-image-host-auto-mode/`。

本任务不做新的线上请求、ADB 操作或真实数据提交；任何未被 fixture/规范支持的字段均保持未知并 fail-safe。
