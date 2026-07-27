# Android Kotlin 与 Jetpack Compose 迁移调研

## Goal

为当前 `NGA Just Works` Android 工程形成一份可执行的渐进迁移调研，使工程最终
收敛到 Kotlin + Jetpack Compose + MVVM，明确可复用资产、技术债、分阶段路线、
风险、工作量级别和启动条件，使后续迁移不依赖一次性猜测或全量重写。

## Background

- 用户确认采用原地渐进迁移，最终目标明确为 Android 原生 Kotlin + Jetpack
  Compose + MVVM；Java/XML/MVP 等共存只允许作为迁移期状态，不是长期目标架构。
- 用户接受在最终架构中保留少量有明确边界的 Android View 互操作，例如 WebView；
  “迁移完成”不以删除所有 View 类为目标，而以不再保留可由 Compose 合理承担的
  完整 XML 页面、且例外组件不越过 UI/安全边界为目标。
- 本任务独立于现有“NGA 安卓 App 前期调研与 MVP 规划”任务；前者研究当前已发布
  工程的技术迁移，后者面向产品功能和 MVP 规划。
- 当前仓库并非纯 Java/XML：排除构建产物后，约有 256 个 Java 文件、76 个 Kotlin
  文件、56 个布局 XML 和 18 个包含 `@Composable` 的 Kotlin 文件。
- Compose 已用于版面、搜索、导航抽屉、登录、账号管理、私信和调试页面；主应用
  仍有 177 个 Java 文件、55 个布局 XML，并混用 Fragment、MVP、LiveData、RxJava、
  ButterKnife 和 WebView/HTML 渲染。
- 现有 Compose 代码不能直接视为已完成目标迁移：部分 Composable 直接读取全局
  `UserManager` 或持有业务可变状态，部分 ViewModel 暴露可变 `LiveData`，UI 与
  ViewModel 之间仍存在多处可变数据双写。
- 构建已启用 Kotlin、Compose compiler、Compose UI/Material/Material 3，Java/Kotlin
  目标版本均为 17，因此迁移不是工具链从零接入问题。
- 历史研究曾为新 Android 产品推荐 Kotlin/Compose、模块化核心和单向 UI 状态；该
  结论可作候选目标架构，但不能替代对当前工程迁移成本和兼容边界的专项审计。
- 当前有价值的复用点包括 Compose/View 互操作宿主、私信的 Coroutine/Paging 路径、
  版面收藏的持久化与回滚语义；它们仍需去除全局对象和可变状态等架构问题。
- 当前自动化主要覆盖构建与发布，功能单测、Compose UI、导航/深链、Room 迁移、
  网络 fixture 和富文本渲染覆盖不足，因此行为特征测试属于迁移前置工作。

## Requirements

- 以仓库源码、构建配置、测试和现有 Trellis 研究为证据，建立 Java/Kotlin、
  XML/Compose、模块、架构模式及关键依赖的现状清单。
- 先从当前代码的真实调用链、状态所有权和模块依赖推导方案，再决定目标 MVVM 的
  具体落地形式；不得先套用模板式 Clean Architecture、单 Activity、Hilt 或
  Navigation Compose 方案。
- 明确 Kotlin 与 Compose 各自的迁移边界：业务/数据代码迁移和 UI 迁移分别评估，
  不把“改成 Kotlin”与“改成 Compose”混为同一操作。
- 以原地渐进迁移为主路线；将新壳/全量重写作为对照方案，说明不采用它的理由及
  只有在何种证据下才应重新考虑。
- 定义可验证的目标架构：应用代码以 Kotlin 为主，页面以 Compose 为主，ViewModel
  对外提供只读、不可变 `UiState` 和明确 UI 事件，数据通过 Repository 边界访问，
  异步状态逐步收敛到 Coroutines/Flow，Composable 不直接操作全局业务单例或持久层。
- 明确 MVVM 组件的职责、页面状态/一次性事件契约，以及旧 MVP、LiveData、RxJava
  和全局可变状态的退出标准，避免只做 Java-to-Kotlin 语法转换。
- 识别必须优先稳定的跨界契约，包括账号与 Cookie 隔离、NGA 网络/编码/解析、
  数据持久化、页面导航、富文本/WebView、发布签名和既有用户数据兼容。
- 给出按依赖顺序排列的迁移阶段；每一阶段说明范围、前置条件、可观察验收门禁、
  回滚方式和粗粒度工作量/风险，不只提供技术栈列表。
- 明确哪些现有 Kotlin/Compose 代码可以保留、整顿或替换，避免把已完成迁移的页面
  重复实现。
- 给出建议的目标架构与依赖治理方案，并区分“迁移必需项”和“可后续优化项”。
- 调研结论必须能拆成后续独立 Trellis 实施任务，但本任务本身不执行产品代码迁移。

## Acceptance Criteria

- [x] 交付一份带源码或配置证据定位的现状审计，覆盖模块、语言、UI、状态管理、
      异步模型、导航、持久化、网络和测试/构建边界。
- [x] 现状审计至少追踪主入口、主题列表、帖子详情、账号会话和一个现有 Compose
      页面从 UI 到数据源的调用链，并据此说明迁移顺序。
- [x] 交付迁移路线对比及单一推荐结论，清楚说明适用条件、成本、风险和放弃条件。
- [x] 交付 Kotlin + Compose + MVVM 目标架构定义及可检查的完成标准，而不是只列出
      框架名称或要求类名包含 `ViewModel`。
- [x] 交付分阶段迁移路线图，每一阶段有明确入口、出口、验证门禁和回滚边界。
- [x] 交付页面/模块迁移优先级矩阵，至少区分保留、先解耦、可直接迁移和暂缓区域。
- [x] 交付技术风险清单，覆盖 Java/Kotlin 互操作、View/Compose 互操作、状态双源、
      Rx/协程并存、WebView/富文本、Room 数据、账号会话和版本发布兼容。
- [x] 给出后续任务拆分建议和首个低风险迁移切片，足以进入单独实施任务规划。
- [x] 调研不以“大爆炸式重写即可”为结论，除非证据和用户选择明确支持该路线。
- [x] 对单 Activity、Navigation Compose、依赖注入框架、严格 Clean Architecture
      等常见附加方案逐项判断“现在采用、延后采用或无需采用”，避免无依据打包迁移。

## Out Of Scope

- 在本任务中批量转换 Java、重写页面、变更数据库或发布新 APK。
- 改变 NGA 产品功能、交互需求或既有账号/会话安全契约。
- 为迁移之外的目的全面升级所有依赖或重构全部模块。

## Constraints

- 受控 View 互操作例外必须由 Compose UI 层承载，只暴露类型化输入/输出；WebView
  的 host、Cookie、导航和销毁规则仍受项目 NGA 平台访问规范约束。
