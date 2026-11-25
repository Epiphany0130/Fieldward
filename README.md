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
