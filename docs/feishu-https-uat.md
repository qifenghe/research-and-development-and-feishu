# 飞书 HTTPS 联调指南

## 前置条件

1. 企业管理员已创建「研发样品管理」飞书自建应用
2. 配置 OAuth 重定向 URL：
   - Mobile: `https://<domain>/m/login/callback`
   - PC: `https://<domain>/admin/login/callback`
3. Nginx 已配置 HTTPS 反向代理（参考 `frontend/deploy/nginx.conf`）

## 联调步骤

1. 启动后端：`scripts/start-local-backend.sh`
2. 启动前端：`pnpm --filter @rnd/mobile dev` 与 `pnpm --filter @rnd/pc dev`
3. 绑定飞书用户：`POST /api/v1/feishu/users/bind`
4. 从飞书工作台打开应用，验证 OAuth 免登
5. 触发任务分发后检查消息卡片跳转
6. 生成核价文件后验证财务通知

## 自检接口

- `GET /api/v1/feishu/integration/status` — 集成状态（公开，无需登录）
- `GET /api/v1/admin/audit-logs` — 审计日志（需 `SYSTEM_ADMIN`）

`integration/status` 返回示例字段：`mode`、`baseUrl`、`appIdConfigured`、`appSecretConfigured`、`readyForOpenApi`。本地 MOCK 模式下 `readyForOpenApi` 为 `false` 属正常；生产 HTTPS 联调需设置 `FEISHU_MODE=OPENAPI` 并配置 `FEISHU_APP_ID` / `FEISHU_APP_SECRET`。

## 文件存储（MinIO 延后）

当前归档使用本地目录 `target/rnd-archive`（`rnd.archive.storage=local`）。`application.yml` 已预留 `rnd.minio.*` 配置项，生产切换时设置 `ARCHIVE_STORAGE=minio` 并部署 MinIO 服务（见任务清单 RND-014）。本阶段不阻塞飞书 HTTPS 联调。

## 验收

参见 [`mobile-uat-checklist.md`](mobile-uat-checklist.md) 第 8 节。
