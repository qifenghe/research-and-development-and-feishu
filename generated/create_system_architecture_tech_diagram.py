from html import escape
from pathlib import Path


OUT = Path("diagrams/飞书自建应用_研发样品管理系统_技术系统架构图.svg")

W = 2200
H = 1280
FONT = "Arial, 'Microsoft YaHei', 'PingFang SC', sans-serif"


def rect(x, y, w, h, fill="#FFFFFF", stroke="#CBD5E1", sw=2, rx=12, dash=None):
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{dash_attr}/>'


def text(x, y, body, size=18, color="#111827", weight="400", anchor="middle"):
    return (
        f'<text x="{x}" y="{y}" text-anchor="{anchor}" '
        f'font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">{escape(body)}</text>'
    )


def multiline(x, y, lines, size=16, color="#111827", weight="500", anchor="middle", line_h=23):
    out = [f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">']
    for i, line in enumerate(lines):
        out.append(f'<tspan x="{x}" dy="{0 if i == 0 else line_h}">{escape(line)}</tspan>')
    out.append("</text>")
    return "\n".join(out)


def box(x, y, w, h, title, lines, fill, stroke="#64748B", title_color="#0F172A", icon=None):
    parts = [f'<g filter="url(#shadow)">', rect(x, y, w, h, fill, stroke, 2, 14), "</g>"]
    if icon:
        parts.append(text(x + 26, y + 35, icon, 24, title_color, "700"))
        tx = x + w / 2 + 12
    else:
        tx = x + w / 2
    parts.append(text(tx, y + 33, title, 19, title_color, "700"))
    if lines:
        parts.append(multiline(x + w / 2, y + 66, lines, 15, "#334155", "500", line_h=22))
    return "\n".join(parts)


def arrow(x1, y1, x2, y2, color="#475569", sw=3, dash=None, label=None, label_pos=0.5):
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    path = f'<path d="M{x1},{y1} C{(x1+x2)/2},{y1} {(x1+x2)/2},{y2} {x2},{y2}" fill="none" stroke="{color}" stroke-width="{sw}" marker-end="url(#arrow)"{dash_attr}/>'
    if label:
        lx = x1 + (x2 - x1) * label_pos
        ly = y1 + (y2 - y1) * label_pos - 10
        return path + "\n" + text(lx, ly, label, 14, color, "700")
    return path


parts = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
    "<defs>",
    '<filter id="shadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="3" stdDeviation="3" flood-color="#64748B" flood-opacity="0.18"/></filter>',
    '<marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth"><path d="M0,0 L0,6 L9,3 z" fill="#475569"/></marker>',
    "</defs>",
    '<rect width="100%" height="100%" fill="#FFFFFF"/>',
]

parts.append(text(W / 2, 46, "研发样品管理飞书自建应用 - 技术系统架构图 v1.0", 34, "#111827", "700"))
parts.append(text(W / 2, 78, "手机/平板现场录入 + Excel核价文件生成 + 样品版本归档 + 飞书协同通知", 18, "#64748B"))

# Outer zones
parts.append(rect(52, 116, 260, 500, "#F8FAFC", "#CBD5E1", 2, 18))
parts.append(text(182, 150, "用户与终端", 23, "#0F172A", "700"))

parts.append(rect(350, 116, 1510, 880, "#F8FBFF", "#A8C7FA", 2, 18))
parts.append(text(1105, 150, "公司内网 / 云主机部署区", 24, "#0F172A", "700"))

parts.append(rect(1890, 116, 250, 880, "#FAFAF9", "#D6D3D1", 2, 18))
parts.append(text(2015, 150, "外部平台", 23, "#0F172A", "700"))

parts.append(rect(350, 1020, 1510, 160, "#FFF7ED", "#FDBA74", 2, 18))
parts.append(text(1105, 1054, "运维、安全与备份", 24, "#9A3412", "700"))

# User terminal
parts.append(box(82, 185, 200, 96, "飞书工作台", ["销售内勤/研发总监", "研发/测试/财务"], "#E0F2FE", "#7DD3FC", icon="👥"))
parts.append(box(82, 322, 200, 96, "手机/平板", ["实验现场录入", "拍照/附件上传"], "#DCFCE7", "#86EFAC", icon="📱"))
parts.append(box(82, 459, 200, 96, "PC浏览器", ["管理端查看", "导出/审核/归档"], "#EDE9FE", "#C4B5FD", icon="💻"))

# Access layer
parts.append(rect(390, 180, 1430, 132, "#EFF6FF", "#93C5FD", 2, 16))
parts.append(text(1105, 212, "1. 接入层", 22, "#1D4ED8", "700"))
parts.append(box(420, 232, 235, 58, "Nginx", ["HTTPS反向代理"], "#DBEAFE", "#60A5FA"))
parts.append(box(690, 232, 260, 58, "飞书免登/OAuth", ["Feishu OAuth + 用户身份"], "#DBEAFE", "#60A5FA"))
parts.append(box(985, 232, 245, 58, "API网关", ["REST API / RBAC权限"], "#DBEAFE", "#60A5FA"))
parts.append(box(1265, 232, 245, 58, "前端静态资源", ["Vue3 H5 / PC管理端"], "#DBEAFE", "#60A5FA"))
parts.append(box(1545, 232, 245, 58, "内网访问控制", ["公司网络/VPN/白名单"], "#DBEAFE", "#60A5FA"))

# Application layer
parts.append(rect(390, 340, 1430, 162, "#ECFDF5", "#86EFAC", 2, 16))
parts.append(text(1105, 372, "2. 应用层", 22, "#047857", "700"))
parts.append(box(420, 395, 260, 78, "移动端H5", ["Vue3 + Vant", "现场打样/拍照上传"], "#DCFCE7", "#4ADE80"))
parts.append(box(715, 395, 260, 78, "PC管理端", ["Vue3 + Ant Design Vue", "审核/看板/文件归档"], "#DCFCE7", "#4ADE80"))
parts.append(box(1010, 395, 260, 78, "飞书应用壳", ["自建应用菜单", "消息卡片跳转"], "#DCFCE7", "#4ADE80"))
parts.append(box(1305, 395, 220, 78, "权限视图", ["按角色显示菜单", "待办/任务过滤"], "#DCFCE7", "#4ADE80"))
parts.append(box(1555, 395, 235, 78, "文件预览下载", ["Excel/PDF/图片", "飞书云盘链接"], "#DCFCE7", "#4ADE80"))

# Service layer
parts.append(rect(390, 530, 1430, 208, "#FFF7ED", "#FDBA74", 2, 16))
parts.append(text(1105, 562, "3. 服务层", 22, "#C2410C", "700"))
parts.append(box(420, 585, 205, 92, "后端API服务", ["Java + Spring Boot", "REST接口/权限校验"], "#FFEDD5", "#FB923C"))
parts.append(box(650, 585, 205, 92, "流程状态机", ["样品需求->打样", "测试->寄样->转单"], "#FFEDD5", "#FB923C"))
parts.append(box(880, 585, 205, 92, "打样实验服务", ["配方/工序/损耗", "版本锁定"], "#FFEDD5", "#FB923C"))
parts.append(box(1110, 585, 205, 92, "Excel生成服务", ["EasyExcel/Apache POI", "核价文件版本"], "#FFEDD5", "#FB923C"))
parts.append(box(1340, 585, 205, 92, "文件归档服务", ["照片/附件/Excel", "产品档案目录"], "#FFEDD5", "#FB923C"))
parts.append(box(1570, 585, 205, 92, "异步任务服务", ["Spring Task + Redis", "通知/上传/导出"], "#FFEDD5", "#FB923C"))

# Data layer
parts.append(rect(390, 765, 1430, 180, "#F5F3FF", "#C4B5FD", 2, 16))
parts.append(text(1105, 797, "4. 数据层", 22, "#6D28D9", "700"))
parts.append(box(420, 825, 230, 82, "PostgreSQL", ["样品/任务/实验单", "版本/状态/权限"], "#EDE9FE", "#A78BFA"))
parts.append(box(690, 825, 230, 82, "Redis", ["缓存/验证码", "异步队列Broker"], "#EDE9FE", "#A78BFA"))
parts.append(box(960, 825, 230, 82, "MinIO 或 NAS", ["照片/附件/Excel", "工艺/BOM文件"], "#EDE9FE", "#A78BFA"))
parts.append(box(1230, 825, 230, 82, "归档索引", ["产品档案", "版本文件关系"], "#EDE9FE", "#A78BFA"))
parts.append(box(1500, 825, 290, 82, "Excel模板库", ["现有核价表模板", "按产品类型维护"], "#EDE9FE", "#A78BFA"))

# External platforms
parts.append(box(1918, 190, 194, 86, "飞书开放平台", ["登录/通讯录", "消息/审批/云盘"], "#F1F5F9", "#94A3B8"))
parts.append(box(1918, 320, 194, 86, "飞书消息", ["任务通知", "按钮跳转"], "#F1F5F9", "#94A3B8"))
parts.append(box(1918, 450, 194, 86, "飞书审批", ["需求审核", "测试/停止确认"], "#F1F5F9", "#94A3B8"))
parts.append(box(1918, 580, 194, 86, "飞书云盘", ["文件同步", "Excel链接"], "#F1F5F9", "#94A3B8"))
parts.append(box(1918, 710, 194, 86, "多维表格", ["进度看板", "统计展示 可选"], "#F1F5F9", "#94A3B8"))
parts.append(box(1918, 840, 194, 86, "预留系统", ["ERP/财务", "电子秤/生产"], "#F1F5F9", "#94A3B8"))

# Ops and security
parts.append(box(405, 1080, 230, 70, "Docker Compose", ["前端/后端/Worker部署"], "#FED7AA", "#FB923C"))
parts.append(box(665, 1080, 230, 70, "HTTPS/TLS", ["证书/内网域名"], "#FED7AA", "#FB923C"))
parts.append(box(925, 1080, 230, 70, "日志监控", ["应用日志", "Prometheus/Grafana可选"], "#FED7AA", "#FB923C"))
parts.append(box(1185, 1080, 230, 70, "定时备份", ["数据库备份", "文件增量备份"], "#FED7AA", "#FB923C"))
parts.append(box(1445, 1080, 230, 70, "审计追踪", ["谁改了什么", "版本不可覆盖"], "#FED7AA", "#FB923C"))
parts.append(box(1705, 1080, 110, 70, "权限", ["RBAC"], "#FED7AA", "#FB923C"))

# Arrows
parts.append(arrow(282, 233, 420, 261, "#2563EB", label="飞书入口"))
parts.append(arrow(282, 370, 420, 420, "#16A34A", label="现场录入"))
parts.append(arrow(282, 507, 420, 420, "#7C3AED", label="管理查看"))
parts.append(arrow(1790, 260, 1918, 233, "#475569", label="API调用"))
parts.append(arrow(1775, 632, 1918, 363, "#475569", label="消息/审批"))
parts.append(arrow(1448, 632, 1918, 623, "#475569", label="文件上传"))
parts.append(arrow(1080, 677, 1080, 825, "#C2410C"))
parts.append(arrow(1442, 677, 1075, 825, "#C2410C"))
parts.append(arrow(1670, 677, 805, 825, "#C2410C", dash="8 8"))
parts.append(arrow(1125, 473, 1125, 585, "#047857"))
parts.append(arrow(1110, 290, 1110, 395, "#1D4ED8"))
parts.append(arrow(1125, 907, 1300, 1080, "#6D28D9", label="备份/审计"))
parts.append(arrow(2112, 884, 1815, 1115, "#64748B", dash="8 8", label="后续扩展"))

# Notes
parts.append(rect(64, 656, 248, 224, "#FFFFFF", "#CBD5E1", 2, 14))
parts.append(text(188, 690, "推荐第一版技术栈", 20, "#0F172A", "700"))
parts.append(multiline(188, 728, [
    "前端：Vue3 + Vant",
    "PC端：Ant Design Vue",
    "后端：Spring Boot",
    "数据库：PostgreSQL",
    "缓存队列：Redis",
    "文件：MinIO/NAS",
    "部署：Nginx + Docker",
], 15, "#334155", "500", line_h=25))

parts.append(rect(64, 910, 248, 206, "#FFFFFF", "#CBD5E1", 2, 14))
parts.append(text(188, 944, "核心设计原则", 20, "#0F172A", "700"))
parts.append(multiline(188, 982, [
    "飞书：入口与协同",
    "系统：主数据和流程",
    "Excel：核价交付物",
    "版本：提交后锁定",
    "归档：产品/版本维度",
    "扩展：预留ERP接口",
], 15, "#334155", "500", line_h=25))

parts.append(text(W - 60, H - 34, "版本：v1.0 | 面向飞书自建应用研发样品管理系统", 15, "#64748B", "400", anchor="end"))
parts.append("</svg>")

OUT.write_text("\n".join(parts), encoding="utf-8")
print(OUT)
