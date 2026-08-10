# Implementation Plan: 修复上游遗留的 11 个 Android Lint 错误

## 1. Baseline and conflict guard

- [x] 保存/复核当前 app Lint 的 11 条 error ID 与位置。
- [x] 再次确认 8 个目标产品文件没有来自其他未提交任务的差异；若出现并发修改，
      先合并意图，不覆盖他人工作。

## 2. Product changes

- [x] 在 `ProfileActivity.onActivityResult` 调用父类实现，保留现有本地分支。
- [x] 在 `TopicListBaseFragment` 改用 `requireArguments()`。
- [x] 将 `TopicSearchFragment`、`TopicCacheFragment`、
      `TopicFavoriteFragment` 的 6 个 observer 改为 view lifecycle owner。
- [x] 在三个 WebView XML 添加局部 `tools:ignore="WebViewLayout"` 与兼容性注释，
      不改变运行时层级或尺寸属性。

## 3. Focused review

- [x] 搜索目标规则和旧模式，确认 11 个位置全部处理且没有全局规则禁用。
- [x] 复核 `TopicSearchFragment` 的标题点击/刷新逻辑及 Presenter 作用域未变化。
- [x] 复核 cache/favorite 的 removed-topic observer 仍在基础列表 observer 之后注册，
      保持 View 重建时的既有重放顺序。
- [x] 复核签名、投票、消息布局的非 `tools:*` XML 差异为零。

## 4. Validation

- [x] `./gradlew :nga_phone_base_3.0:assembleDebug --no-daemon`
- [x] `./gradlew :nga_phone_base_3.0:testDebugUnitTest --no-daemon`
- [x] `./gradlew :nga_phone_base_3.0:lintDebug --no-daemon`
- [x] 解析 `nga_phone_base_3.0/build/reports/lint-results-debug.xml`，断言 error/fatal
      数量为 0，并记录 warning 数量。
- [x] `./gradlew testDebugUnitTest --continue --no-daemon`，按规范分类固定上游失败。
- [x] `git diff --check`
- [x] 设备/ADB 检查记录为“未运行（项目策略，当前无明确授权）”。

## 5. Documentation and review

- [x] 更新 `.trellis/spec/backend/android-quality-guidelines.md`，移除“当前固定基线仍有
      11 个 app lint error”的过时描述，改为要求报告保持 0 error。
- [x] 运行 Trellis quality review，修复发现的本任务问题后重跑受影响检查。
- [x] 汇总修改、验证结果、已知的非本任务 warning/测试基线与回滚点，提交用户复核。
