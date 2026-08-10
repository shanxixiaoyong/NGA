# 修复上游遗留的 11 个 Android Lint 错误

## Goal

清除 `nga_phone_base_3.0` 当前继承自固定 Justwen 上游基线的 11 个
Android Lint error，使后续任务能够把新增 Lint error 视为真实回归，同时保持现有
页面布局、导航与数据加载行为不变。

## Background

- 2026-08-10 重新运行
  `./gradlew :nga_phone_base_3.0:lintDebug --no-daemon` 后构建成功，报告为
  `11 errors, 721 warnings`。
- 这 11 个位置及触发模式都存在于固定上游版本 `5d807617`；当前工作区在相关
  8 个文件上没有未提交差异。
- `nga_phone_base_3.0/build.gradle:110-113` 当前使用
  `abortOnError false`，因此 Lint 进程成功不等于报告中没有 error。
- 历史基线按规则 ID 分为：`MissingSuperCall` 1 条、`WebViewLayout` 3 条、
  `UseRequireInsteadOfGet` 1 条、`FragmentLiveDataObserve` 6 条。

## Requirements

### R1. Activity result 分发

- 在
  `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ProfileActivity.java:385`
  保留现有签名和头像结果处理，并调用父类 `onActivityResult`。
- 不改变现有 request code、成功条件或 UI 更新逻辑。

### R2. Fragment 参数访问

- 在
  `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/ui/fragment/TopicListBaseFragment.kt:39`
  用 AndroidX `requireArguments()` 取代 `arguments!!`。
- 不改变缺失参数时应快速失败的既有契约，也不扩展到未被本任务 Lint 基线覆盖的
  参数读取重构。

### R3. LiveData 与 View 生命周期

- 将下列 6 个 `observe(this, ...)` 改为以 `getViewLifecycleOwner()` 为 owner：
  - `TopicCacheFragment.java:34` 1 处；
  - `TopicFavoriteFragment.java:28` 1 处；
  - `TopicSearchFragment.java:165,173,175,180` 4 处。
- Presenter/ViewModel 仍保持 Fragment 作用域；仅让直接操作 View/Adapter 的观察者
  随 `onDestroyView()` 停止，避免旧 View 收到回调。

### R4. 保留内容驱动的 WebView 布局

- 处理以下 3 个 `WebViewLayout` error：
  - `src/main/res/layout/dialog_signature.xml:12`；
  - `src/main/res/layout/dialog_vote.xml:12`；
  - `src/main/res/layout/list_message_content.xml:19`。
- 这些布局依赖 `wrap_content` 让签名/投票对话框和消息行按内容确定高度；不得为了
  迎合 Lint 将列表行或对话框内容机械改为占满可用高度。
- 使用元素级、带理由注释的 `tools:ignore="WebViewLayout"` 记录该兼容性例外；禁止
  在 Gradle/Lint 全局禁用该规则。

### R5. 保持任务边界

- 不处理其余约 721 条 warning、固定上游 JVM 示例测试缺陷或其他未被这 11 条
  error 覆盖的问题。
- 不修改 `abortOnError false`；是否把 Lint 变成阻断式质量门另行决策。
- 不进行 WebView 视觉重构、NGA 网络行为变更、发布配置变更或设备操作。
- 保留工作区已有且与本任务无关的未提交修改。

## Acceptance Criteria

- [x] `:nga_phone_base_3.0:lintDebug` 完成后，生成的 app Lint 报告为
      `0 errors`；warning 可保留且数量只作记录。
- [x] Lint 报告中不再出现 `MissingSuperCall`、`WebViewLayout`、
      `UseRequireInsteadOfGet` 或 `FragmentLiveDataObserve` error。
- [x] 三个 WebView XML 的运行时宽高值和布局层级保持不变；抑制仅位于对应元素，
      并有兼容性理由，不新增全局规则禁用。
- [x] `ProfileActivity` 的两条现有结果处理路径仍存在，并调用父类实现。
- [x] 6 个 LiveData observer 全部绑定 view lifecycle；Presenter/ViewModel 创建范围不变。
- [x] `./gradlew :nga_phone_base_3.0:assembleDebug` 成功。
- [x] `./gradlew :nga_phone_base_3.0:testDebugUnitTest` 成功，现有主题列表标题刷新契约测试
      继续通过。
- [x] 按项目质量规则运行 `./gradlew testDebugUnitTest --continue` 并将固定上游示例测试
      失败与本任务回归分开记录。
- [x] `git diff --check` 通过，且差异只包含本任务文件、任务产物和必要的质量规范更新。
- [x] 设备/ADB 检查按项目策略不运行；这不构成交付阻塞。

## Out of Scope

- 清理所有 Android Lint warning。
- 修改全局 Lint severity、`abortOnError` 或 CI 发布门。
- 重做签名、投票、消息内容的 WebView 尺寸策略。
- 修复 `TopicListBaseFragment` 中未计入当前 11 条基线的其他生命周期模式。
- 修复仓库其他模块已知的示例测试依赖/宿主 JVM 问题。
- 任何安装、真机/模拟器、ADB 或真实 NGA 流量验证。
