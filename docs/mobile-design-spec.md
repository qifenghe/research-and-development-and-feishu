# 研发样品管理系统 — 移动端设计规范

版本：v1.0  
适用范围：飞书自建应用 H5 端（Vue 3 + Vant）  
PC 端主色保持一致：`#246BFE`

## 1. 设计原则

- **任务优先**：首页以待办和「继续处理」Hero 卡驱动操作，减少层级跳转。
- **现场录入**：表单分段 Collapse，底部固定操作栏，支持单手操作。
- **状态可读**：样品/任务状态通过 Badge 色彩区分，禁止仅用灰色文字表达状态。
- **与原型一致**：视觉以 `prototypes/pages/png/` 导出稿为验收基准。

## 2. 色彩 Token

| CSS 变量 | 色值 | 用途 |
|----------|------|------|
| `--mobile-primary` | `#246BFE` | 主按钮、Tab 激活、Hero 卡背景 |
| `--mobile-primary-soft` | `#EAF2FF` | 主色浅底、stat-chip |
| `--mobile-bg` | `#F6F8FB` | 页面背景 |
| `--mobile-card` | `#FFFFFF` | 卡片背景 |
| `--mobile-border` | `#E2E8F0` | 边框、分割线 |
| `--mobile-text` | `#111827` | 主文字 |
| `--mobile-muted` | `#64748B` | 次要文字、标签 |
| `--mobile-success` | `#16A34A` | 通过、已锁定 |
| `--mobile-success-soft` | `#ECFDF5` | 成功浅底 |
| `--mobile-warning` | `#F59E0B` | 逾期、复打样、待接受 |
| `--mobile-warning-soft` | `#FFF4E5` | 警告浅底 |
| `--mobile-danger` | `#EF4444` | 停止、拒绝 |

PC Ant Design Vue：`colorPrimary: #246BFE`，与移动端主色统一。

## 3. 字体与间距

| 层级 | 字号 | 字重 | 用途 |
|------|------|------|------|
| 页面标题 | 27px | 800 | `.page-title` |
| 页面副标题 | 17px | 400 | `.page-subtitle` |
| 卡片标题 | 18–20px | 800 | `.task-card__title`、`.hero-card__title` |
| 正文 | 15px | 400–600 | 列表 meta、info-row |
| 分区标题 | 13px | 700 | `.section-title` |

**间距：** 页面内边距 `12px 16px`；卡片间距 `12px`；区块间距 `16–18px`。  
**圆角：** 卡片 `--mobile-radius: 18px`；小卡片/按钮 `--mobile-radius-sm: 14px`。

## 4. 组件清单

| 类名 / 组件 | 说明 |
|-------------|------|
| `PageHeader` | 标题 + 副标题 + stat-row |
| `HeroCard` | 蓝色继续处理主卡 |
| `TaskCard` | 待办列表项 + 右侧操作按钮 |
| `RoleEntryGrid` | 2×2 角色快捷入口 |
| `StatusBadge` | 状态徽章（primary / success / warning / danger） |
| `ConclusionButtons` | 测试/反馈三结论按钮组 |
| `InfoCard` | 键值对详情块 |
| `VersionTimeline` | A0/A1/A2 版本时间线 |
| `FixedActionBar` | 底部固定操作栏（含 safe-area） |
| `ProcessStepEditor` | 工序结构化录入（前重/后重/损耗率） |

## 5. 样品状态 → Badge 映射

| 业务状态 | Badge 变体 | 示例文案 |
|----------|------------|----------|
| 待审核 / 待分发 | `warning` | 待审核 |
| 待接受 / 打样中 | `primary` | 打样中 |
| 待测试 | `primary` | 待测试 |
| 样品完成 / 已通过 | `success` | 已通过 |
| 逾期 | `warning` | 逾期 |
| 已停止 / 废弃 | `danger` | 已停止 |

## 6. Vant 覆盖规则

```css
/* Tabbar */
.van-tabbar-item--active { color: var(--mobile-primary); }

/* Button primary */
.van-button--primary { background: var(--mobile-primary); border-color: var(--mobile-primary); }

/* Collapse */
.van-collapse-item__content { padding-top: 0; }

/* Field 在 mobile-form 内 */
.mobile-form .van-cell-group--inset { margin-left: 0; margin-right: 0; }
```

## 7. 页面布局规范

### 7.1 Tab 信息架构（3 Tab）

| Tab | 路由 | 说明 |
|-----|------|------|
| 待办 | `/todo` | Hero + 角色入口 + 待办列表 |
| 样品 | `/samples` | 搜索 + 状态筛选 |
| 我的 | `/profile` | 个人信息 |

「录需求」不作为 Tab，通过待办页角色入口进入。

### 7.2 固定底栏

- 高度：`padding 12px 16px` + `env(safe-area-inset-bottom)`
- 有 Tabbar 时：`.fixed-footer--with-tabbar { bottom: 50px }`
- 主操作：`van-button type="primary" block`
- 次要操作：`plain type="primary" block`，位于主操作上方

## 8. 文件结构

```
frontend/apps/mobile/src/
├── components/     # 可复用 Vue 组件
├── styles/
│   ├── tokens.css
│   ├── components.css
│   ├── vant-overrides.css
│   └── global.css
└── views/
```

## 9. 验收标准

- 色值、圆角、字号与 `prototypes/pages/png/` 一致（允许 ±2px 布局偏差）。
- 所有状态均有对应 Badge 色彩。
- 实验单、测试确认、寄样反馈页具备固定底栏，不被 Tabbar 遮挡。
