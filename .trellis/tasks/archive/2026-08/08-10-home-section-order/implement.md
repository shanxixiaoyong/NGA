# 首页栏次顺序与自定义排序：实施计划

## Ordered Checklist

- [x] 1. 在 `board_list.json` 把顶层栏次调整为 `other, games, wow, bliz, club`，保留全部栏次 ID、名称和 children；同步升级 `BOARD_LOCAL_VERSION_CURRENT`。
- [x] 2. 在 `PreferenceKey` 增加唯一的首页栏次顺序 key；新增纯逻辑 resolver/store，按稳定 ID 合并、去重、补全并在默认顺序时清除偏好。
- [x] 3. 为 `ForumBoardModel` 增加独立于 `localBoardList` 的显示顺序覆盖、快照/移动/恢复/持久化保护；确保收藏页固定在索引 0，远端 child 合并仍写基础列表而非用户顺序。
- [x] 4. 为 `ForumBoardViewModel` 增加首页顺序事务 API和 board LiveData 发布，处理保存失败、候选过期和提示。
- [x] 5. 扩展 `TabLayoutWithPager` 的可选稳定 key/排序 API；实现长按后横向拖动、边缘跨屏移动、终止清理、TalkBack 动作和选中 key 稳定，默认调用方不变。
- [x] 6. 在 `ForumBoardView` 接入 tab 排序；分别跟踪收藏卡片排序和首页 tab 排序，任一活动时禁用 Pager，仅收藏排序继续通知侧栏状态。
- [x] 7. 新增/更新测试：resolver 与事务单测、`TabLayoutWithPagerContractTest`、首页源码合同、asset 默认顺序与版本迁移合同、侧栏相邻页文案合同。
- [x] 8. 搜索所有依赖“收藏后第一栏为魔兽世界”的活合同与测试，更新为网事杂谈；不修改归档任务历史。
- [x] 9. 运行子任务验证并检查 lint 报告；确认产品文件没有覆盖并行任务的改动。

## Validation Commands

```bash
./gradlew :lib_base_ui_compose:testDebugUnitTest
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :lib_base_ui_compose:assembleDebug
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :lib_base_ui_compose:lintDebug
./gradlew :nga_phone_base_3.0:lintDebug
```

## Risk And Rollback Points

- `TabLayoutWithPager.kt` 是共享组件：排序路径必须由可选参数隔离；编译和合同测试是首个回滚点。
- `board_list.json` 体积大：只移动五个顶层对象，禁止重排或格式化 children；提交前按顶层 ID 比较内容完整性。
- 本地版本升级会删除旧的基础 board 缓存：用户自定义顺序存于独立偏好，不得随缓存删除。
- 若拖动交互无法保持选中 key 或终止清理，回滚 UI 接线而保留已验证的默认顺序/resolver，重新评估交互实现。
