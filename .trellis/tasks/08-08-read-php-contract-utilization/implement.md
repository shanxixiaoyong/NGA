# 执行计划：`THREAD.PAGE` 响应契约与请求定位基础

## 0. 实施前门禁

1. 用户明确批准本任务最新规划后，才运行 `task.py start`；在此之前只维护规划文档。
2. 读取 `trellis-before-dev` 注入的 backend/network/parser/UI 规范，并核对 foundation、reading-favorites、image-host-auto-mode 的当前 diff/ownership。
3. 从 `.temp` HAR 或任何真实响应提取字段时先脱敏；仓库只接受最小 JSON fixture，禁止把正文、Cookie、用户名、头像或 token 带入测试。

## 1. 建立最小契约与 parser 防护（R-01，推荐本轮）

- 在不改变公开模型的前提下，提取最小的 envelope/数值读取 helper；保留 `ArticleConvertFactory` 作为现有入口。
- 添加脱敏的完整核心页、最小页、类型变体和非成功 fixture，先断言当前真正消费的 `__T/__R/__R__ROWS/__ROWS/__U`、评论、附件 URL/thumb、vote 等字段。
- `__CU`、`encode/time`、未知扩展只记录契约/证据，不在本轮新增产品字段。
- 删除或屏蔽 parser/converter 的完整 body 日志，确认错误消息不携带正文。

## 2. 请求与深链（R-02）

- 提取 canonical `ThreadPageRequest` builder；修复 `searchpost` 传递，覆盖 `tid/pid/authorid/page/searchpost` 组合。
- 为 Intent/URI、搜索结果、帖子内 `[pid]` 链接和普通主题页添加 request-builder tests。
- 用 fixture 或已授权的低频 redacted observation 决定是否需要 `to=1`；没有证据则保留 deferred note，不扩大 URL。
- 让详情 UI 在缺失 `__T`/无楼主行时安全降级，并使用主题 metadata 优先识别作者。

## 3. 缓存风险与接入条件（R-03，默认后置）

- 记录 legacy `tid/page` 缓存的串条件/账号风险，并把完整 key 方案交给 foundation/page-store。
- 只有确认当前验收路径会读到错误 legacy cache 时，才增加最小 bypass 或版本隔离；不在本轮另建 Room/page-store。

## 4. 结构化边界记录（R-04，默认后置）

- 在 fixture 中记录附件完整 metadata 和行/评论身份锚点的 P1 接入断言；MVP 不扩展 `AttachmentData`/评论 UI。
- 保持现有 HTML、图片页面前缀和投票渲染行为；附件/热门回复/楼层导航 UI 另列 P1，不在本次实现。

## 5. 验证与交付

建议命令（按当前根工程可用任务调整）：

```text
./gradlew --no-daemon --console=plain :nga_phone_base_3.0:testDebugUnitTest
./gradlew --no-daemon --console=plain :nga_phone_base_3.0:lint
./scripts/secret-scan.sh
git diff --check
python3 .trellis/scripts/task.py validate read-php-contract-utilization
```

审查重点：字段/请求/缓存三层是否共用同一 key；是否误改 image-host 或 foundation；失败分类是否可观察；测试 fixture 是否脱敏。若新 foundation 接口与设计冲突，优先适配其 contract，记录 delta，不建立平行实现。

## 风险点与回滚点

- `ArticleConvertFactory.java` 正被 image-host 任务修改：冲突时只保留页面前缀调用边界，不回退对方改动。
- Fastjson 数值类型和旧 `ThreadData` 序列化兼容可能导致编译/运行时差异；先保留 adapter，逐项迁移。
- 旧缓存文件可能含真实正文；不自动上传或打印，必要时仅增加版本隔离和用户可触发清理。
- 若 fixture 无法证明服务器定位参数，回滚猜测性参数并将其标为 `unknown-or-unsupported`。
