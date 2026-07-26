# 恢复 Justwen 多账号网页登录

## Goal

把登录架构收敛为“Justwen 多账号会话 + NGA 官方网页认证”：保留原项目的账号列表、当前账号选择和动态 Cookie 请求机制，只借鉴 NgaLite 对登录入口的界面表达，不再维护非官方原生账号密码协议。用户应能在一个符合现有 App 风格的登录入口中选择已有账号或进入受控网页登录，并且不会因为 WebView 中已有 Cookie 而在页面加载后立即退出。

## Background

- NgaLite 只有一个持久 Cookie 和账号名，新登录会覆盖旧会话；它不具备本项目所需的多账号模型，见 `references/nga-clients/NgaLite/app/src/main/java/com/ngalite/app/data/CookieStore.kt:14-47`。
- Justwen 的 Room 用户列表、当前索引、账号增删选和按当前账号生成 Cookie 的逻辑仍然存在，见 `lib_bu_account/src/main/java/com/justwent/androidnga/bu/UserManager.kt:12-160`；`RetrofitHelper` 会在每次请求时向 Cookie provider 读取当前值，见 `lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/RetrofitHelper.java:103-117`。
- 因此多账号机制没有丢失，也不是原生登录 403 的原因。403 出现在账号持久化之前；旧实现还把凭据 POST 和后续 `login_set_cookie_quick` 失败合并为同一错误，无法证明具体阶段。
- NgaLite 对 `login_set_cookie_quick` 的 HTTP 结果不做成功判断，取不到 Set-Cookie 时直接用 `uid/token` 构造会话，见 `references/nga-clients/NgaLite/app/src/main/java/com/ngalite/app/data/NgaApi.kt:129-151`。这解释了为什么照搬流程但把该请求视为致命步骤会产生新的 403 失败面。
- 右上角网页登录“打开即退出”的直接原因是后来新增的 `onPageFinished` Cookie 检查把 WebView 里已有的有效 Cookie 当作本次新登录成功。当前未提交补丁已把 `PAGE_FINISHED` 设为永不完成，见 `lib_bu_account/src/main/java/com/justwent/androidnga/bu/login/WebLoginPolicy.kt:28-39`，该修复必须保留并纳入最终设计。
- `.trellis/spec/backend/network-foundation-contract.md` 中的“Native NGA Password Login Acquisition”仍把原生密码登录规定为主流程，已与本任务的产品决定冲突，必须在完成实现后改为多账号 Web 登录合同。

## Requirements

- **R1 保留 Justwen 多账号运行机制：** 不替换 `UserManager` 的用户列表、活动索引、增删切换和 Room 持久化，不替换 `RetrofitHelper` 的活动账号 Cookie provider。登录结果仍以 `uid`、`cid`、`username` 进入现有账号模型。
- **R2 登录入口界面：** `LoginActivity` 改为无卡片、Material 2 的账号入口页。页面显示“登录新账号”操作及已保存账号列表；账号行复用现有头像、昵称和单选按钮语言。点击任意已有账号（包括当前账号）会选中该账号、返回 `RESULT_OK` 并关闭入口页。
- **R3 统一网页登录入口：** “登录新账号”与右上角现有 `btn_ic_browser` 地球加锁按钮必须启动同一个未导出的 `WebLoginActivity`。真实账号、密码和验证码只在 NGA 控制的网页中输入，原生入口页不提供密码表单。
- **R4 保留旧网页登录语义：** Web 登录加载 `https://ngabbs.com/nuke.php?__lib=login&__act=account&login`。普通页面加载和 `onPageFinished` 永远不能因已有 Cookie 自动完成；只有允许来源的旧登录成功信号或用户主动退出 Web 页时，才允许检查 Cookie 并交付结果。
- **R5 Web 边界与结果校验：** WebView 仅接受精确允许的 HTTPS NGA host、默认端口或 443、无 user-info 的 URL。只有正整数 `uid` 和长度受限、Cookie-safe 的 `cid` 才能返回；用户名解码失败时回退到 `uid`。`LoginActivity` 使用 `UserManager.addUserAndSelect` 完成新增、更新和选中。
- **R6 移除原生密码协议：** 删除原生账号类型/密码/CAPTCHA UI、`LoginViewModel` 原生请求状态机、RSA/响应解析/临时 CookieJar/直接登录客户端及其专用测试，并移除只为这些代码增加的模块依赖和测试依赖。不得保留隐藏或实验性的第二套密码协议。
- **R7 保留仍有价值的现有改动：** 保留共享的 `btn_ic_browser` 资源、顶栏图标的可访问名称、无隐藏菜单时不显示多余“三点”按钮、`UserManager.addUserAndSelect` 以及 `User.toString()` 对 `cid` 的脱敏。不得覆盖工作区内发布流程等无关未提交改动。
- **R8 项目原生视觉：** 使用现有棕/绿/黑主题、默认 Android/Material 2 字体、16 dp 页面内边距和稳定账号行尺寸；不复制 NgaLite 的 Material 3 Dialog、Cookie 粘贴面板、文案、资产或单账号状态模型，不增加卡片、渐变、插画或装饰动画。
- **R9 安全与验证：** 自动化测试不得访问真实 NGA 登录接口或使用真实凭据。保留精确来源、Cookie 解析、会话值校验和完成触发器的纯单元测试；运行 Android 构建、聚焦单测、lint 和残留扫描，并将真实网页登录列为人工设备验收。

## Acceptance Criteria

- [x] 打开 `/account/login` 后首先看到“登录新账号”和已保存账号列表，不再看到原生账号、密码、账号类型或 CAPTCHA 输入框。
- [x] 已保存账号以头像、昵称和单选状态显示；点击任意账号会调用现有活动账号选择逻辑、返回 `RESULT_OK`，后续请求使用该账号的 `uid/cid` Cookie，其他账号记录保持不变。
- [x] “登录新账号”行和右上角地球加锁图标都打开同一个未导出的 Web 登录页；共享 drawable 在仓库中只有一份。
- [x] Web 登录加载旧 Justwen URL；即使 WebView 已有有效 NGA Cookie，`onPageFinished` 也不会关闭页面或返回登录成功。
- [x] 旧登录成功信号或用户主动退出时，允许来源上的有效 `uid/cid` 能返回入口页；入口页新增或更新对应账号、选中它并返回 `RESULT_OK`。
- [x] HTTP、非白名单 host、非 443 自定义端口、user-info URL，以及无效/零/非数字 uid 或包含分隔符、控制字符、超长的 cid 均不能进入账号存储。
- [x] `lib_base_network` 中不再存在 `NgaLoginClient`、RSA 登录、登录响应解析或 CAPTCHA 会话代码；`lib_bu_account` 不再依赖该原生协议或其专用 ViewModel，相关专用依赖与测试已清理。
- [x] 现有 Room 多账号数据结构、账号删除/切换能力、动态 Cookie provider、共享顶栏图标、无空“三点”菜单和 cid 日志脱敏保持有效。
- [x] 登录入口在现有浅色、夜间、棕、绿和黑主题下可读，账号行尺寸稳定，长昵称不挤压或覆盖控件。
- [x] `WebLoginPolicy` 聚焦测试、相关模块单测、App debug assemble/test/lint、`git diff --check` 和敏感/残留扫描通过；仓库级已知基线失败如仍存在需单独报告，不得被本任务扩大。
- [x] `.trellis/spec/backend/network-foundation-contract.md` 删除“原生密码登录为主流程”的过时合同，记录本任务的 Web 认证与 Justwen 多账号边界。

## Out of Scope

- 重写 Room 用户表、账号加密存储或活动账号并发快照机制。
- 重设计“账号管理”设置页、删除账号交互、个人资料页或全局导航。
- 原生账号密码登录、Cookie 粘贴导入、QQ/微博 OAuth、注册、找回密码或自动验证码处理。
- 绕过 NGA 的挑战、限流或访问控制，以及使用未明确授权的账号做自动化真实登录。
- 为适配网页资源而开放任意第三方 host、HTTP、文件或内容 URL。

## Deferred Risk

- Web 登录仍依赖 NGA 页面、Cookie 名和旧成功提示；上游变化可能要求调整 Web 策略，但不会再要求维护 RSA/表单私有协议。用户主动退出时的 Cookie 检查保留旧 Justwen 兼容语义。
- `RetrofitHelper` 在请求构建时动态读取活动账号；账号切换与并发请求之间的快照一致性是既有技术债，本任务保持不变。
- 无真实凭据的自动化验证不能证明上游当前账号分支；最终 live 验收只在用户授权的设备上手动执行，并在验证码、挑战或限流出现时停止自动化。
