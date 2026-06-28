# Vue 双前端

PC 与手机端为两套独立 Vue 3 应用，共用 `@rnd/shared` 包对接 Spring Boot `/api/v1`。

## 目录

- `apps/pc` — Vue 3 + Ant Design Vue，部署路径 `/admin/`
- `apps/mobile` — Vue 3 + Vant，部署路径 `/m/`
- `packages/shared` — API 客户端、类型、Token 工具
- `deploy/nginx.conf` — 生产 Nginx 示例

## 本地开发

```bash
cd frontend
pnpm install
pnpm dev:pc      # http://localhost:5173/admin/
pnpm dev:mobile  # http://localhost:5174/m/
```

后端默认代理到 `http://localhost:8080`。

## 飞书主页配置

- 桌面端：`https://{domain}/admin/dashboard`
- 移动端：`https://{domain}/m/todo`

## 构建

```bash
pnpm build
node scripts/check-routes.mjs
```

## MOCK 登录

后端 `FEISHU_MODE=MOCK` 时，登录页使用 `mock:{feishuUserId}` 作为 OAuth code。
