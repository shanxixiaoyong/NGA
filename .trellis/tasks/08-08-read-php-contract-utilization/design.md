# 技术设计：`THREAD.PAGE` 响应契约与请求定位基础

## 1. 设计结论

采用“一个操作契约、一个解析入口、一个请求键”的收敛方案：

```text
ArticleListParam / deep link
          │
          ▼
  canonical ThreadPageRequest
          │  (query + account context)
          ▼
 foundation THREAD.PAGE transport/codec/classifier
          │ bounded raw response
          ▼
  envelope normalizer + typed parser
          │
          ├── ThreadPageResponse (page context + rows + extensions)
          └── typed failure (site/auth/challenge/parse/protocol)
          │
          ▼
 compatibility ThreadData / existing UI adapter
```

解析器不再把服务端 JSON 直接当成 UI 完整成功；请求和响应至少要以同一个不可变请求身份关联。MVP 不强制立刻引入一套新的公开 domain 层：可以先在 `ArticleConvertFactory` 内提取纯 JVM 可测的 normalize/parse helper，再由后续 foundation/reading-favorites 任务承接正式 DTO。这样能修复真实缺口而不提前重做架构。

本轮保留一个父任务而不再拆 P0 子任务：parser、请求定位和缓存 key 必须共享同一份 `ThreadPageRequest`/协议版本，拆开实现反而容易形成两个不一致的契约。P1 的附件/热门回复/楼层导航消费者另行建任务。

## 2. 契约分层

### 2.1 Envelope

长期目标是定义 `ThreadPageEnvelope`（或等价内部 DTO）包含；本轮只把其中对现有 parser 有影响的部分固定成测试契约：

- `encode`、`time`：可选的协议/服务端时间元数据；不参与业务成功判断。
- `data`：合法 JSON 对象才进入页面解析。
- `failure`：由 foundation classifier 先判断 HTTP、HTML/challenge、站点 `error` 和 JSON parse failure；不得用空 `data` 代表成功。

未知顶层键不影响解析，也不复制到日志。raw bytes/body 的保留、解码和账号绑定遵循 foundation 的 `RawNgaResponse`，本任务不新增全量字符串存储。

### 2.2 Page data（MVP 只稳定现有消费字段）

现有 `ThreadData` 已能消费大部分核心字段，MVP 不要求一次性把下表全部暴露成新的产品模型。先对当前消费字段做 null/type-safe 解析和 fixture 断言；其余字段记录为后续扩展边界。

长期 `ThreadPageResponse` 的已知边界如下：

| 字段 | 级别 | 兼容策略 | 第一消费者 |
| --- | --- | --- | --- |
| `__T` | optional-but-preferred | 对象缺失时仍可交付行列表，元数据为空并带降级标记 | 详情标题/主题锚点 |
| `__ROWS` | page count | integer/long/string → bounded long/int adapter；缺失按实际行数降级 | 分页/回复数 |
| `__R__ROWS` | row count | 同上；不得依赖 Fastjson 具体数值类 | 行表遍历 |
| `__R` | rows map | 缺失/null → empty list + `partial`；坏行跳过并计数 | 文章列表 |
| `__U` | user map | 缺失时保留行自身作者字段，用户增强信息为空 | 作者/头像/签名 |
| `__CU` | capability/context | 先保留为有界、类型可辨识的 opaque 值；具体位含义未证实，不能替代服务端授权 | 后续操作可见性 |
| `__GLOBAL` | page context | 只保留 allow-listed context；`_ATTACH_BASE_VIEW` 交给 image-host resolver | HTML/媒体上下文 |
| row `comment`, `attachs`, `vote`, `alterinfo`, `17` | nested/extension | null-safe 解析，保持 pid/lou/uid 等身份锚点 | 当前渲染与 P1 适配器 |

数值读取统一经过 `readIntLike/readLongLike`（名称可按现有工具调整），拒绝溢出、浮点和带控制字符的字符串；字段缺失和字段类型非法是不同的诊断原因。字符串正文不做无界日志或异常 message 拼接。

### 2.3 Extension policy

只保存有明确下游用途的 allow-list 字段；未知键只产生 bounded counter/field name。若后续确需扩展，先加 fixture、字段语义和版本号，再扩展 DTO。这样既能“吃干净”已知高价值字段，也避免把服务端任意内容复制进缓存或日志。

## 3. 请求与定位

引入纯函数 `ThreadPageRequest`/builder（可先作为 `ArticleListModel` 内部 adapter），canonical query 顺序固定为 `page`, `__output=8`, `noprefix`, `v2`, `tid`, `pid`, `authorid`, `searchpost`。只发送非默认筛选值；编码、host、Cookie 和 redirect 由 foundation 统一处理。

输入链路测试：

```text
web/deep link -> ArticleListActivity -> ArticleListParam
search result -> TopicSearchFragment -> ArticleListParam
ArticleListParam -> ThreadPageRequest -> /read.php query
```

`pid + searchpost` 的服务端定位参数（例如 `to=1`）作为证据驱动的兼容开关：先用脱敏 fixture/响应断言验证；没有证据不添加猜测字段。若服务端只能通过 `pid` 定位，响应中的 `__T.page/__T.position` 作为后续 UI 跳转依据，而不是由首行 `lou` 推断。

## 4. 缓存设计（后续接入条件）

长期应定义稳定、可序列化的 `ThreadPageRequestKey`：

```text
schemaVersion | operation=THREAD.PAGE | accountScope
| tid | page | pid | authorid | searchpost | codecVersion
```

规范化规则：默认值统一为 0/标准页；字段顺序固定；协议/解析版本变化使旧 entry 失效。缓存实现优先接入 foundation/reading-favorites 的 page-store。本轮默认只记录风险和接入条件；只有当前验收路径会读到错误 legacy cache 时，才加最小 bypass/版本隔离。缓存 body 只作为受控本地数据，不进入日志/导出。

迁移采用“双读一次写新 key”或版本化目录，失败可删除新 namespace 并回退网络读取；不覆盖其他任务的 Room schema、账号 vault 或收藏数据。

## 5. 兼容 UI 与模型边界

- `ThreadData` 继续提供旧 getter；MVP 不要求把所有 JSON 直接反序列化到 UI bean。新增字段应在后续通过独立 page context/metadata DTO 或兼容 adapter 暴露。
- `ArticleListFragment` 对 null `threadInfo`、空行和过滤页做安全降级；主题作者优先来自 `__T` 的 author/authorId，首行 `lou == 0` 仅为 fallback。
- 附件 parser→adapter 的完整 `aid/subid/name/dscp/size/ext/type/url/thumb` 保留是 P1 接入条件；MVP 只确保现有 URL/thumb 渲染不回归。页面 `_ATTACH_BASE_VIEW` 的解析结果只走 image-host 任务定义的 `NgaImageHost`/`HtmlData` 链路。
- `rawData` 的现有兼容字段不扩散到日志、Room 或分享；foundation 接管后由 bounded raw response 取代。

## 6. 测试与 fixture 设计

至少准备四个脱敏 fixture：

1. 完整正常页：主题、两行、用户表、评论、附件、vote、`__CU`/`__GLOBAL`。
2. 最小正常页：只有合法 `data`、空/缺失可选对象。
3. 类型变体页：数值为 Long/字符串/null，未知字段和坏行并存。
4. 非成功响应：HTML、站点 error、challenge/截断 JSON。

JVM 测试断言 parsed DTO、failure taxonomy、request key 和 URL query；不把正文写入 failure snapshot。现有 image-host 测试继续由其任务负责，避免重复断言同一 URL 策略。

## 7. 依赖、发布与回滚

- 依赖 `THREAD.PAGE` operation registry、network foundation 的 raw transport/codec/classifier、reading-favorites 的 page-store/AST 方向。
- 推荐顺序是先落地 `searchpost`、parser 防崩溃/隐私和 contract tests，再由 foundation 任务接管缓存 key 与正式 DTO；任一阶段失败都可保留旧 `ArticleConvertFactory` 入口。
- 不改变其他 operation、登录/写操作、图床设置或 UI 大改；实现前须检查并发任务对 `ArticleConvertFactory` 的增量修改，采用最小兼容 patch。
