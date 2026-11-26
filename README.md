# 循职 Fieldward

面向准备实习或校招的大学生的求职工作台。功能范围见 [开发 Spec](./开发%20Spec.md)。

## 本地启动

安装 Java 21、Maven 3.9 和 Node.js 22.12+。数据库运行在服务器，Web 和 API 在本地启动。项目数据库端口只监听服务器回环地址。

1. 建立 SSH 隧道，保持终端运行。

   ```sh
   ssh -o ExitOnForwardFailure=yes -N -L 127.0.0.1:55432:127.0.0.1:55432 dev@62.234.18.82
   ```

2. 将 `.env.example` 复制为项目根目录的 `.env`，填写专用数据库账号密码。如果本地已生成 `.env`，可直接使用。
3. 在另一个终端启动 API。首次启动由 Flyway 执行迁移。

   ```sh
   cd api
   mvn spring-boot:run
   ```

4. 在另一个终端启动 Web。

   ```sh
   cd web
   npm ci
   npm run dev
   ```

打开 [本地 Web](http://127.0.0.1:5178)。Web 将 `/api` 转发给本地 18080 端口上的 Java 服务。健康检查为 [GET /api/health](http://127.0.0.1:5178/api/health)，OpenAPI 为 [GET /api/openapi](http://127.0.0.1:5178/api/openapi)。

如需另建数据库，请使用含 pgvector 的 PostgreSQL 17，并由数据库管理员预先执行 `CREATE EXTENSION IF NOT EXISTS vector`。应用账号需要数据库连接权限及 `public` schema 的 `USAGE`、`CREATE` 权限。

## 账号入口

- 学生在 [注册页](http://127.0.0.1:5178/register) 用邮箱和密码注册，然后登录。刷新可恢复 PostgreSQL 中的会话。
- 管理员使用同一个 [登录页](http://127.0.0.1:5178/login)，登录后进入 [用户管理中心](http://127.0.0.1:5178/admin/users)，可以搜索、分页查看以及停用或恢复学生账号。停用会使已登录的学生会话失效。
- 首次初始化管理员时，在 `.env` 中填写 `FIELDWARD_ADMIN_EMAIL` 和 `FIELDWARD_ADMIN_PASSWORD`，再启动 API。初始密码至少 12 个字符且最多 72 字节。已有管理员重启时沿用原密码。
- 本地 HTTP 在 `.env` 中设 `FIELDWARD_SECURE_COOKIE=false`。HTTPS 环境使用默认的 `true`。所有写请求先从 `GET /api/auth/csrf` 获取本会话的令牌。

## 简历制作

学生首次登录进入 [简历制作页](http://127.0.0.1:5178/resume/edit)。填写姓名和学校即可点击「创建简历」，经历、技能和联系方式可以稍后补充。首次提交会保存正式简历并创建 Mentor 主对话，随后进入职业方向选择。编辑器提供可添加和调整顺序的栏目、实时 A4 预览、缩放及版式设置。栏目顺序、版式和间距会随草稿保存。点击「保存草稿」可暂存未完成的内容，重新登录后继续编辑。提交会保存当前草稿的独立版本，重复提交同一份草稿会返回同一版本。

已有简历可点击「上传已有简历」，选择或拖入 5 MB 以内的 PDF 或 Word（.docx）。在「查看成稿」可阅读提取出的文字和下载原件。扫描版 PDF 可通过下载原件查看，工作台的「上传新版本」可保存后续修改。

个人信息可补充求职意向、联系方式和照片，教育支持多段记录。经历与技能条目可排序，选填栏目可重命名和隐藏。三种简历版式支持字体、主题色、页眉对齐及 A4 边距设置。提交后点击「查看成稿」可浏览已提交版本，并通过浏览器打印或存为 PDF。

## 求职方向与 Mentor

首次提交后从八个技术求职方向中选择。匹配分析按阶段列出据黑马公开大纲整理的技术点，并由小米 MiMo 根据简历原文给出初评，包含可推断的必要前置基础。学生逐项确认「会」「接触过」「不会」后进入 Mentor 核验旁聊。Mentor 对绿项和黄项逐项出题评分，报告更新路线状态，并按顺序主动带学待补技术。分析和旁聊均关联本次简历版本，旧报告支持发起新版分析。[黑马程序员方向参考](https://go.itheima.com/flow/flow.html)

在项目根目录的 `.env` 设置按量付费的 `MIMO_API_KEY`，重启 API 后即可与 Mentor 对话。默认模型是 `mimo-v2.6-pro`，使用小米官方按量 `https://api.xiaomimimo.com/v1/chat/completions`。项目配置可用 `FIELDWARD_MENTOR_ENDPOINT` 与 `FIELDWARD_MENTOR_MODEL` 覆盖。每轮先保存学生消息，再通过 SSE 返回增量内容。完整回复提交后才进入历史。打开工作台可以恢复历史，失败时可重试同一条学生消息。

在工作台点击「复盘当前简历」即可让 Mentor 依据正式版本提问，也可以直接指定版本号或文件名。可按话题创建旁聊，主对话和旁聊各自保留记录、共用记忆和资料。Mentor 会自动保存符合提取规则的学生原话，例如明确说出的目标、参与经历、偏好或「请记住」指令。学生也可以在 [Mentor 记忆库](http://127.0.0.1:5178/workspace/memory) 手动保存笔记、翻看所有正式简历版本与原件、添加独立的 PDF 或 DOCX 求职资料。回复下方的「查看本轮资料来源」可核对本轮检索的版本、笔记和对话摘录。

对话保存在 PostgreSQL。模型请求使用当前线程近期消息及检索出的相关旧对话、简历和文件。检索采用中文双字词和英文词索引。正式简历发给模型前去掉排版和照片，并遮盖常见联系方式。Mentor 会标注扫描件识别文本和缺失资料。

要启用扫描 PDF 的本地识别，先安装带 `chi_sim` 和 `eng` 语言包的 Tesseract，例如 macOS 上的 `brew install tesseract-lang`，再将 `FIELDWARD_OCR_ENABLED=true` 写入 `.env` 并重启 API。识别任务以账号和文件摘要去重，处理没有文本层的 PDF 的前 10 页。原件始终可以下载核对。[Tesseract 安装说明](https://tesseract-ocr.github.io/tessdoc/Installation.html)
