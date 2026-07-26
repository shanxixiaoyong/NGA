# 浏览渲染与收藏排序实施计划

1. 在 Justwen 现有模块内建立/迁移 Room 业务实体、DAO、repositories、PagingSource/RemoteMediator 和 ThreadPageStore；不创建第二套 `:app/:core:*` 产品工程。
2. 保留 Justwen 的主题、导航和现有页面，在缺失处补充三档 adaptive 行为，不替换视觉基线。
3. 实现版面、主题、帖子、搜索、历史和状态页面。
4. 实现 BBCode AST、HTML fallback、Compose renderer、图片/内部链接路由。
5. 实现收藏 membership/order merge；在“我的收藏”网格卡片上直接长按拖动，短按仍打开版面，不使用版面页 `menu_add_bookmark` 或页面级排序入口。
6. 为 `HorizontalPager` 做手势仲裁：长按前横滑切分类，长按成立后临时禁用 Pager 用户滑动并消费拖动，结束/取消后恢复；加入事务持久化、回滚、TalkBack actions 和上下边缘自动滚动。
7. 增加离线、账号隔离、进程恢复、窗口变化、解析异常、服务端刷新和 Pager/drag 冲突测试。
8. 保持 `minSdk 30`、`compile/target 35`，在 Android 15/API 35 实体设备运行启动/滚动/解析/拖动 benchmark 并优化 bounded caches/recomposition；API 30 最低 smoke 与 API 36 上 `targetSdk 35` 前向 smoke 仅在已有匹配实体设备时补充，不启动模拟器。

Validation:

```bash
./gradlew testDebugUnitTest lint
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api35-serial> ./gradlew :benchmark:connectedCheck
```

可选设备命令仅在用户提供对应实体设备时运行：`ANDROID_SERIAL=<api30-serial> ./gradlew connectedDebugAndroidTest` 和 `ANDROID_SERIAL=<api36-serial> ./gradlew connectedDebugAndroidTest`。缺少 API 30/36 设备不阻塞；不得启动模拟器，API 36 仍验证 `targetSdk 35`。

Rollback：schema/contract 变更通过 migration；解析失败降级为安全文本，不回退到通用高权限 WebView。
