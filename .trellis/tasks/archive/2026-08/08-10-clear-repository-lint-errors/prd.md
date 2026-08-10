# 清理仓库所有历史 Android Lint 错误

## Goal

审计所有 Android 模块并清除仓库残留的历史 Lint `Error` / `Fatal`，使模块级
`lintDebug` 不再被上游遗留错误阻断，而不是只保证应用模块报告为零错误。

## Background

- 已归档任务 `08-10-fix-upstream-lint-errors` 清除了
  `nga_phone_base_3.0` 的 11 个继承错误；其当前报告为
  `0 Error / 0 Fatal / 721 Warning`。
- `lib_base_common` 仍有独立报告：`1 Error / 33 Warning`。唯一错误位于
  `ConfirmDialog.kt:22`，规则为 `UseRequireInsteadOfGet`。
- 全量运行 13 个模块的 `lintDebug` 后，仓库合计为
  `1 Error / 0 Fatal / 822 Warning`；除 `ConfirmDialog` 外没有其他阻断错误。
- 该行最初来自 Justwen 提交 `f8c59cbc`；fork 提交 `7c227349` 曾改成
  `requireContext()`，随后 `45b777ae` 在恢复上游兼容路径时重新引入
  `context!!`。
- 过去会话已经记录过同一错误，但当时按“与任务无关的既有错误”延期处理；本任务
  不再沿用这种豁免。

## Requirements

- 对 `settings.gradle` 中所有 Android application/library 模块运行或解析各自的
  debug Lint 报告，建立完整 Error/Fatal 清单。
- 对每个 Error/Fatal 追溯当前代码、调用语义和上游来源；优先真实修复，只有通用
  Lint 规则与明确兼容契约冲突时才允许局部、带理由的抑制。
- 至少修复 `ConfirmDialog.kt:22` 的 `context!!`，恢复为
  `requireContext()`，并保持确认框业务行为不变。
- 不通过模块级或全局 `abortOnError false`、规则禁用或基线文件隐藏错误。
- 保留工作区中其他任务的并行修改。

## Out of Scope

- 本任务不清理当前 822 条 Lint Warning；Warning 数量仅作为诊断信息记录，不影响
  本次零 Error/Fatal 验收。
- 本任务不处理 `lib_base_ui`、`lib_bu_statistics`、`lib_core` 和
  `lib_module_debug` 中已知的四类上游 JVM fixture/示例测试失败。
- 不运行 ADB、安装、instrumentation 或设备测试；本次修复可由设备无关的 Gradle
  检查完整验证。

## Acceptance Criteria

- [x] 所有 Android 模块的最新 debug Lint 报告均为 `0 Error / 0 Fatal`。
- [x] 仓库级模块 Lint 命令不再因历史错误失败。
- [x] `ConfirmDialog` 的正常确认/取消行为和 Fragment 前置条件保持不变。
- [x] `:lib_base_common:testDebugUnitTest`、`:lib_base_common:lintDebug` 和
  `git diff --check` 通过。
- [x] 仓库级 `lintDebug --continue` 完成后，13 个 Android 模块的 XML 报告均经
  解析确认没有 `Error` 或 `Fatal`。
- [x] 未新增全局/模块级 Lint 错误豁免。
