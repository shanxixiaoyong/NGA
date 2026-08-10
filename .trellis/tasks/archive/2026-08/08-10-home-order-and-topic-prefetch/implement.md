# 首页排序与主题预读取优化：集成计划

## Execution Order

- [x] 1. 用户批准本任务树的最终规划摘要。
- [x] 2. 启动并完成 `08-10-home-section-order`；执行其实现清单和完整子任务质量检查。
- [x] 3. 启动并完成 `08-10-topic-page-prefetch`；执行其实现清单和完整子任务质量检查。
- [x] 4. 核对两个子任务的变更范围没有交叉覆盖或状态耦合。
- [x] 5. 更新首页组件/状态规范与 `THREAD.PAGE` 预读取合同，确保新行为可被后续任务发现。
- [x] 6. 执行最终 Android 门禁：应用 assemble、应用单元测试、相关库测试、lint 报告人工检查；仓库级 `test` 作为已知上游失败的诊断基线记录。
- [x] 7. 不运行 ADB、安装或 instrumentation；除非用户在当前任务中另行明确授权。

## Integration Validation

```bash
./gradlew :lib_base_ui_compose:testDebugUnitTest
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :lib_base_ui_compose:lintDebug
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew test --continue
```

lint 命令退出码不足以判定通过，必须检查生成报告中的 error，并把既有 11 个应用 lint error 与本次新增问题分开记录。

## Rollback Points

- 首页子任务提交前：可删除首页顺序偏好/解析层，恢复 asset 原顺序与通用 Pager 原接口。
- 预读取子任务提交前：可删除候选页 LiveData/规划器与静默加载入口，恢复仅当前页 `onResume` 加载。
- 父任务没有数据库、服务端或不可逆迁移。
