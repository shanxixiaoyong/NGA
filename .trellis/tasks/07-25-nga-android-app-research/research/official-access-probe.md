# NGA 官方入口与访问可行性探针

检索日期：2026-07-25（本地时区 Asia/Shanghai）

## 来源与方法

- `https://bbs.nga.cn/robots.txt`
- `https://ngabbs.com/robots.txt`
- `https://bbs.nga.cn/`
- `https://bbs.nga.cn/read.php?tid=1`
- `https://ngabbs.com/`
- `https://ngabbs.com/read.php?tid=1`

仅执行低频的 GET/HEAD 风格探针，没有尝试登录、绕过挑战或批量抓取。

## 可核验观察

1. 两个入口都返回 robots 文件。`bbs.nga.cn/robots.txt` 对所有 User-agent 禁止 `/admin/`、`/attachment/`、`/image/`、`/data/`、`/ipdata/`、`/template/`、`/require/`；`ngabbs.com/robots.txt` 也禁止这些敏感目录（部分规则略有差异）。robots 是爬虫约束信号，不等同于完整服务条款，但客户端不应把这些路径当作公开资源批量访问。
2. 直接访问首页时，本次探针在两个域名均得到 `403 Forbidden`，响应头包含 `Set-Cookie`（例如 `lastvisit`、`lastpath`、`ngaPassportUid`）和 `X-NGA-CONTENT-TYPE: short-message`，正文提示需要登录/等待跳转。访问 `read.php?tid=1` 则得到 `200`，但正文仍是同一类访客限制页面，而非可用帖子数据。
3. 响应使用 `text/html; charset=GBK` / `GB18030`，并设置 `Cache-Control: no-cache, no-store, must-revalidate`。因此“把网站当普通 JSON/UTF-8 公共 API”是未经验证的假设；编码、访客挑战、Cookie 生命周期和错误页面都必须在真实授权环境中验证。
4. `ngapost2md` 的 README（见 `research/opensource-android-clients.md` 或其来源）也明确提醒遵守站点规范、控制请求频率、不要研究绕过网页盾；这与本次探针观察相互印证，但该项目的做法不是 NGA 官方 API 承诺。
5. F-Droid 的 `OPEN NGA` 条目（<https://f-droid.org/packages/dev.mlzzen.androidnga/>，本次检索日期）将其标为 `NonFreeNet`，并列出 GPL-2.0-only、第三方客户端和 Android 8.0+ 的已发布构建。旧版 `NGA客户端开源版` 条目（<https://f-droid.org/packages/gov.anzong.androidnga/>）的描述转述了维护者关于“开放 API”和 Cookie 授权的历史声明；这是项目/打包方的陈述，不应当作当前 NGA 官方 SLA 或授权合同。

## 对 MVP 的影响

- 首个实验应先验证“获得授权的会话能否稳定读取一个版面和一个帖子”，再决定原生 API 客户端、受控 WebView 或混合方案；不能先承诺完整的公开浏览/发帖能力。
- 网络层需要显式处理 GBK/GB18030、Cookie、重定向/挑战、403/短消息页和速率限制，并把原始响应留在可控的诊断日志中（去除账号敏感信息）。
- 任何附件、图片、用户数据缓存都应遵守 robots、站方规则、版权和隐私要求；离线存储默认应可关闭并支持清理。
- 该探针没有证明任何具体接口获得官方授权，也没有验证登录、发帖或上传流程；这些都是阻塞性的产品/合规决策与后续实验，而不是实现细节。
