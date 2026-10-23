# 龙汇研发管理系统

用于样品需求、研发打样、工艺配方、寄样反馈、核价与 SOP 文件归集。电脑和平板使用 `/admin/`，手机使用 `/m/`；两套前端共用后端服务，但手机端不等于电脑端的全部编辑能力。

## 主要功能

- 样品需求录入、审核与任务分配。
- 试验方案、分层工艺步骤、物料投入与产出、得率记录。
- 物料分类与物料入库审核。
- 寄样、客户反馈、核价及文件导出、版本历史。
- 账号角色与权限控制。

手机端沿用龙汇品牌、水墨背景与青绿色界面，提供工作台、分组需求表单及适配小屏的业务页面。具体功能以当前账号权限为准。

## 项目结构

| 目录 | 内容 |
| --- | --- |
| `backend/` | Java 17、Spring Boot、数据库迁移及文件生成 |
| `frontend/apps/pc/` | Vue 3 + Ant Design Vue 电脑/平板端 |
| `frontend/apps/mobile/` | Vue 3 + Vant 手机端 |
| `frontend/packages/shared/` | 共用接口与类型 |
| `frontend/deploy/` | Nginx 示例配置 |
| `docs/` | 设计与配置文档 |

## 本地开发

准备 Java 17、Maven、Node.js 和 pnpm。首次安装依赖：

```bash
cd frontend
pnpm install
```

后端（在独立终端执行，仅用于本地开发）：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

前端（分别在独立终端执行）：

```bash
cd frontend
pnpm dev:pc
# 另一个终端：pnpm dev:mobile
```

开发入口通常为 `http://localhost:5173/admin/` 和 `http://localhost:5174/m/`，以启动日志为准。前端默认代理后端 8080；可用 `RND_BACKEND_URL` 指定其他后端地址。`localhost` 只指当前设备，不是手机可直接访问的服务器地址。

## 验证与构建

```bash
cd backend
mvn test
```

```bash
cd frontend
node --test tests/*.test.mts
pnpm typecheck
pnpm build
pnpm check:routes
```

使用支持项目 `.mts` 测试的 Node.js 版本。前端产物分别位于 `apps/pc/dist/` 与 `apps/mobile/dist/`，应部署至 `/admin/` 和 `/m/`。接口由同域 `/api/` 反向代理到后端。

## 服务器更新注意事项

本仓库源码不等同于已验收的生产离线部署包。历史生产交付包含独立的启动、安全初始化和 PostgreSQL 兼容修正；重新打包前必须核对这些修正是否已纳入，不要直接用开发配置覆盖生产配置。

1. 固定提交号，完成测试、构建及 PostgreSQL 升级验证。
2. 备份数据库、附件、密钥配置、Compose/Nginx 配置和旧程序。
3. 在临时目录准备新版本，保留旧版本供回退；不要覆盖生产密码。
4. 保持 Compose 项目名及数据卷指向不变，禁止使用 `docker compose down -v` 清理生产环境。
5. 更新后检查健康接口、登录、角色权限、附件及 SOP/核价导出。

不要上传 `.env`、口令、证书私钥、数据库、附件归档、备份或依赖目录。GitHub 推送不会自动更新服务器；数据库迁移也不会随程序回退自动撤销。

更多前端说明见 [frontend/README.md](frontend/README.md)。[域名配置文档](docs/阿里云正式域名部署配置.md) 是历史配置参考，不代表服务器当前状态；正式发布以现场部署配置和维护交接记录为准。
