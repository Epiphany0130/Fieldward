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

学生首次登录进入 [简历制作页](http://127.0.0.1:5178/resume/edit)。填写姓名和学校即可点击「创建简历」，经历、技能和联系方式可以稍后补充。首次提交会同时保存简历和创建 Mentor 主对话，成功后进入 [学生工作台](http://127.0.0.1:5178/workspace)。编辑器提供可添加和调整顺序的栏目、实时 A4 预览、缩放及版式设置。栏目顺序、版式和间距会随草稿保存。点击「保存草稿」可暂存未完成的内容，重新登录后继续编辑。提交会保存当前草稿的独立版本，重复提交同一份草稿会返回同一版本。

个人信息可补充求职意向、联系方式和照片，教育支持多段记录。经历与技能条目可排序，选填栏目可重命名和隐藏。三种简历版式支持字体、主题色、页眉对齐及 A4 边距设置。提交后点击「查看成稿」可浏览已提交版本，并通过浏览器打印或存为 PDF。
