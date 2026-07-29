# 首页侧栏入口与滑动手势一致化

## Goal

让用户在首页（包括默认显示的“我的收藏”分页）可以通过左上角菜单按钮或从屏幕左侧向右拖拽打开同一个侧边栏，使入口图标和交互语义一致。

## Background

- 首页使用 `NavigationDrawerFragment` 中的 `ModalNavigationDrawer`，左上角按钮已会调用同一 `drawerState.open()`：`nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerFragment.kt:293`。
- 首页内部使用 `HorizontalPager` 在“我的收藏”和其他版面分类之间水平分页，会与侧栏的水平拖拽竞争：`lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/TabLayoutWithPager.kt:51`。
- 共用顶栏当前将所有导航动作固定显示为返回箭头：`lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/ScaffoldApp.kt:116`。

## Requirements

- R1. 首页左上角的导航图标显示为三条横线的菜单图标，点击后打开现有侧边栏。
- R2. 用户在首页从屏幕左缘向右水平拖拽时，应拖出与左上角按钮相同的侧边栏；此行为在“我的收藏”分页上也必须可用。
- R3. 侧边栏已打开时，保留 Material 侧栏的拖拽关闭和点击遮罩关闭行为。
- R4. 保留首页现有分页滑动、收藏版面长按拖拽排序、版面点击和顶栏操作。
- R5. 菜单图标只用于首页侧边栏入口；搜索等子页的导航图标继续显示返回箭头。

## Acceptance Criteria

- [ ] AC1 (R1): 首页左上角显示三条横线菜单图标，辅助功能描述为打开侧边栏；点击可打开截图所示侧边栏。
- [ ] AC2 (R2): 在首页“我的收藏”分页，从屏幕左缘向右拖拽可连续拖出侧边栏，松手后按拖拽进度打开或回弹。
- [ ] AC3 (R2, R4): 非左缘的常规水平滑动仍用于首页分页切换；激活收藏项长按拖拽后仍只进行排序。
- [ ] AC4 (R3): 侧边栏打开后可向左拖回关闭，也可点击右侧遮罩关闭。
- [ ] AC5 (R5): 搜索等已有子页仍显示返回箭头，其点击返回行为不变。
- [ ] AC6: 受影响的 Android 模块可编译，相关自动化检查通过。

## Out of Scope

- 不重新设计侧边栏内容、宽度、颜色或个人信息区。
- 不改变首页分页顺序、收藏数据或排序持久化。
- 不把其他页面的返回箭头批量替换为菜单图标。

## Key Decisions

- “拖拽出左侧页面”定义为从屏幕左缘向右拖拽，避免与首页内部分页的常规水平手势混淆。
- 扩展共用顶栏的图标配置能力，默认仍为返回箭头，仅首页显式选择菜单图标。
- 本任务为边界明确的轻量 UI 交互修正，使用 PRD-only 规划。
