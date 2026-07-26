# NGA 平台契约 Bootstrap 执行计划

## Ordered Checklist

- [x] 校验原始 reference checkout、`upstream-justwen/master` 和固定基线均为 `5d807617f8058950f7ea81dda405e38fb0cc37ec`，并保存当前工作树状态但不把它作为原始证据。
- [x] 只在原始 Justwen checkout 中枚举全部生产网络入口和调用方：Retrofit、OkHttp、`HttpURLConnection`、`HttpPostClient`、WebView Cookie、JavaScript bridge 和上传 host。
- [x] 为每个原始命中项分配 operation ID 或 wired/dormant/link-only/excluded 状态，读取 request builders、Cookie/session owners、charset converters、response parsers、调用方和原始 tests/fixtures。
- [x] 完成 `research/original-justwen-nga-network-inventory.md`；对关键文件用固定 upstream Git object 复核，现有研究只用于查漏。
- [x] 编写 `.trellis/spec/backend/nga-platform-access-rules.md`，分开记录 original behavior、migration rule 和 do-not-copy 安全边界。
- [x] 编写 `.trellis/spec/backend/nga-platform-operation-registry.md`，覆盖 PRD R1 的全部原始领域及固定快照源码锚点。
- [x] 在原始 registry 完成后比较当前根工程，为各 operation 标记 unchanged/modified/removed/fork-only/unresolved；不得由 fork delta 补写原始事实。
- [x] 收敛 `.trellis/spec/backend/network-foundation-contract.md`：保留原始 Retrofit/legacy transport、Web 登录、多账号 Cookie 事实和项目安全规则，移除把 fork-only native password flow 当成原始合同的内容。
- [x] 更新 `.trellis/spec/backend/index.md` 和仍 active 的 NGA 相关任务 manifests，让后续实现/检查加载原项目事实与迁移边界。
- [x] 对照原始 checkout 的初始 `rg` 命中清单做反向覆盖检查，并执行文档、placeholder、JSONL 与 diff 检查。

## Validation Commands

```bash
JUSTWEN_REF=references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen
git -C "$JUSTWEN_REF" rev-parse HEAD
git rev-parse upstream-justwen/master

rg -l --glob '!**/build/**' --glob '*.{kt,java,js}' \
  'RetrofitHelper|getService\(|getServiceKt\(|HttpPostClient|OkHttpClient\.Builder|HttpURLConnection|uploadFile\(|addJavascriptInterface' \
  "$JUSTWEN_REF"/lib_* "$JUSTWEN_REF"/nga_phone_base_3.0/src/main | sort

rg -n --glob '!**/build/**' \
  'app_api\.php|thread\.php|read\.php|post\.php|nuke\.php|attach\.php|__lib|__act|ngaPassportUid|ngaPassportCid' \
  "$JUSTWEN_REF"/lib_* "$JUSTWEN_REF"/nga_phone_base_3.0/src/main

rg -n 'To fill|TBD|TODO: fill|placeholder' \
  .trellis/spec/backend/network-foundation-contract.md \
  .trellis/spec/backend/nga-platform-access-rules.md \
  .trellis/spec/backend/nga-platform-operation-registry.md

python3 ./.trellis/scripts/task.py validate .trellis/tasks/07-26-bootstrap-nga-platform-contracts
git diff --check
git status --short
```

不得运行会连接真实 NGA host 的测试或探针。当前和原始产品代码都只读；文档任务不需要 Gradle build。

## Review Gates

- **Source gate**：每个原始事实锚定固定 checkout，并抽查固定 upstream object；当前根工程不能成为原始字段来源。
- **Coverage gate**：原始网络入口清单与 ledger 一一对应；dormant、link-only、excluded 有明确理由。
- **Evidence gate**：没有 `original-source-observed => live-supported` 或 `current-fork-delta => original-contract` 的错误升级。
- **Mutation gate**：每个状态变更操作都记录原始成功/失败逻辑和未知结果/重复风险；legacy 文本匹配不成为推荐合同。
- **Login gate**：原始登录来自固定 Justwen WebView/Cookie flow；当前 fork 的 native/login 改动只出现在 delta。
- **Injection gate**：active NGA 任务的 manifests 指向新 Spec，路径存在且 JSONL 校验通过。
- **Scope gate**：最终 diff 不包含当前或 reference 产品源码、真实响应、Cookie、token、私信或帖子正文。

## Risky Files and Rollback Points

- `.trellis/spec/backend/network-foundation-contract.md`：现有内容混有 fork-only native login；只修文档，不触碰并行登录任务代码。
- `.trellis/spec/backend/index.md`：必须与实际文件集一致；新文件回滚时同步恢复索引。
- `.trellis/tasks/*/{implement,check}.jsonl`：只更新 active 且确实接触 NGA 的任务；不重写其他上下文。
- `research/original-justwen-nga-network-inventory.md`：允许保留 unknown/dormant，但最终 Spec 不得把它们写成当前支持。
