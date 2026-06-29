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

后端默认代理到 `http://127.0.0.1:8080`。

### 手机局域网联调

**方式 A（推荐，单端口）**

```bash
# 在项目根目录
node scripts/run-feishu-dev.mjs
node scripts/print-lan-urls.mjs
# 手机浏览器打开：http://<电脑局域网IP>:8787/m/todo
```

**方式 B（直连 Vite）**

```bash
pnpm dev:mobile
node scripts/print-lan-urls.mjs
# 手机：http://<电脑局域网IP>:5174/m/
```

确保 Mac 防火墙允许 5174 / 8787 端口。MOCK 模式下需先在电脑完成演示用户 bind，再在手机选择对应角色登录。

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
