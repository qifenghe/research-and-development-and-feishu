from html import escape
from pathlib import Path


OUT = Path("diagrams/飞书自建应用_研发样品管理平台_总体逻辑架构图.svg")

W = 2200
H = 1280
M = 44
LAYER_X = 500
LAYER_W = 1420
LAYER_H = 128
GAP = 18
TITLE_Y = 42

FONT = "Arial, 'Microsoft YaHei', 'PingFang SC', sans-serif"

COLORS = {
    "blue": "#2F6FED",
    "green": "#0E9F6E",
    "teal": "#008577",
    "pink": "#E64694",
    "orange": "#D97706",
    "gray": "#64748B",
    "purple": "#7C3AED",
}

LAYERS = [
    {
        "title": "1. 用户层：谁发起业务协作",
        "fill": "#EAF3FF",
        "stroke": "#8AB8F5",
        "boxes": [
            ("销售内勤", "样品需求/寄样/反馈"),
            ("研发总监", "审核/分发/停止确认"),
            ("研发人员", "接受任务/现场打样"),
            ("测试/品控", "试吃/检测/结果确认"),
            ("财务", "核价/报价反馈"),
            ("业务员/管理层", "客户反馈/下单决策"),
        ],
    },
    {
        "title": "2. 统一入口层：通过飞书进入研发平台",
        "fill": "#E9FBF6",
        "stroke": "#67DCC2",
        "boxes": [
            ("飞书工作台", "研发样品管理入口"),
            ("移动端H5", "手机/平板现场录入"),
            ("PC管理端", "总监/内勤/财务查看"),
            ("飞书消息卡片", "待办/按钮/跳转"),
            ("飞书审批确认", "审核/测试/停止"),
            ("飞书云盘/看板", "文件链接/进度看板"),
        ],
    },
    {
        "title": "3. 平台控制层：入口请求落到哪些控制服务",
        "fill": "#FFF0F7",
        "stroke": "#F5A3C8",
        "boxes": [
            ("API网关 + RBAC", "飞书身份/权限/菜单"),
            ("流程引擎 + 状态机", "需求/打样/测试/寄样"),
            ("任务分发服务", "指派/接受/转派"),
            ("版本锁定服务", "A0/A1/核价V1"),
            ("文件归档服务", "照片/附件/Excel"),
            ("飞书集成服务", "消息/审批/云盘/API"),
        ],
    },
    {
        "title": "4. 业务服务层：研发样品全生命周期",
        "fill": "#EBFFF0",
        "stroke": "#79E79A",
        "boxes": [
            ("样品需求服务", "录入/退回/补充"),
            ("研发任务服务", "分发/接受/进度"),
            ("打样实验单服务", "配方/工序/损耗/得率"),
            ("测试确认服务", "通过/复打样/停止"),
            ("寄样反馈服务", "快递/客户反馈"),
            ("核价文件服务", "Excel生成/版本提交"),
            ("工艺BOM准备", "通过后生成任务"),
        ],
    },
    {
        "title": "5. 工具与模板层：业务服务调用哪些工具",
        "fill": "#FFF9E8",
        "stroke": "#F6D783",
        "boxes": [
            ("Excel模板引擎", "按现有核价表生成"),
            ("工艺模板库", "冷冻即热/腊制/调理"),
            ("计算工具", "领料/损耗/得率/包数"),
            ("文件预览下载", "Excel/PDF/图片"),
            ("消息通知工具", "飞书卡片/提醒"),
            ("看板工具", "进度/逾期/统计"),
        ],
    },
    {
        "title": "6. 数据归档层：沉淀哪些数据资产",
        "fill": "#F1EFFF",
        "stroke": "#B8A7FF",
        "boxes": [
            ("样品需求库", "客户/规格/要求/时间"),
            ("产品物料库", "分类/NS编码/单位"),
            ("实验单配方库", "原料/工序/称重"),
            ("版本档案库", "A0/A1/A2/锁定"),
            ("文件归档库", "照片/附件/Excel"),
            ("测试寄样反馈库", "测试/快递/客户结果"),
            ("核价/BOM/工艺库", "核价版本/工艺文件"),
        ],
    },
    {
        "title": "7. 基础设施层：数据、文件和接口由哪些资源承载",
        "fill": "#FFF2E5",
        "stroke": "#F5B475",
        "boxes": [
            ("公司主机/内网", "部署后端和前端"),
            ("PostgreSQL", "业务数据库"),
            ("NAS/对象存储", "附件和归档文件"),
            ("定时备份", "数据库/文件备份"),
            ("飞书开放平台", "登录/消息/审批/云盘"),
            ("预留接口", "ERP/财务/电子秤"),
        ],
    },
]

COLUMNS = [
    ("需求链路", COLORS["blue"], 585, ["销售内勤", "飞书工作台", "API网关 + RBAC", "样品需求服务", "消息通知工具", "样品需求库", "公司主机/内网"]),
    ("打样链路", COLORS["green"], 820, ["研发人员", "移动端H5", "任务分发服务", "打样实验单服务", "计算工具", "实验单配方库", "PostgreSQL"]),
    ("测试链路", COLORS["teal"], 1030, ["测试/品控", "飞书审批确认", "流程引擎 + 状态机", "测试确认服务", "工艺模板库", "测试寄样反馈库", "PostgreSQL"]),
    ("核价链路", COLORS["pink"], 1245, ["财务", "PC管理端", "版本锁定服务", "核价文件服务", "Excel模板引擎", "核价/BOM/工艺库", "NAS/对象存储"]),
    ("寄样反馈链路", COLORS["orange"], 1470, ["业务员/管理层", "飞书消息卡片", "流程引擎 + 状态机", "寄样反馈服务", "看板工具", "测试寄样反馈库", "飞书开放平台"]),
    ("归档链路", COLORS["gray"], 1685, ["研发总监", "飞书云盘/看板", "文件归档服务", "工艺BOM准备", "文件预览下载", "文件归档库", "定时备份"]),
]


def wrap(text, max_chars=12):
    parts = []
    line = ""
    for ch in text:
        if len(line) >= max_chars:
            parts.append(line)
            line = ch
        else:
            line += ch
    if line:
        parts.append(line)
    return parts


def svg_text(x, y, text, size=20, fill="#111827", weight="400", anchor="middle"):
    return (
        f'<text x="{x}" y="{y}" text-anchor="{anchor}" '
        f'font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{fill}">{escape(text)}</text>'
    )


def multiline_text(x, y, lines, size=17, fill="#111827", weight="400", anchor="middle", line_h=22):
    out = [f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{fill}">']
    for i, line in enumerate(lines):
        dy = 0 if i == 0 else line_h
        out.append(f'<tspan x="{x}" dy="{dy}">{escape(line)}</tspan>')
    out.append("</text>")
    return "\n".join(out)


def rounded_rect(x, y, w, h, fill, stroke, sw=2, rx=16):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'


def arrow_marker(color, name):
    return f'''
    <marker id="{name}" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="{color}" />
    </marker>'''


def layer_y(idx):
    return 92 + idx * (LAYER_H + GAP)


box_centers = {}
parts = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
    "<defs>",
    '<filter id="shadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="#64748B" flood-opacity="0.18"/></filter>',
]
for key, color in COLORS.items():
    parts.append(arrow_marker(color, f"arrow_{key}"))
parts.extend(["</defs>", '<rect width="100%" height="100%" fill="#FFFFFF"/>'])

parts.append(svg_text(W / 2, TITLE_Y, "预制食品研发样品管理平台 v1.0 - 飞书自建应用总体逻辑架构图（链路细化版）", 34, "#111827", "700"))
parts.append(svg_text(W / 2, TITLE_Y + 34, "飞书做入口与协同，自研系统负责流程、版本、Excel核价文件和归档", 18, "#475569", "400"))

for idx, layer in enumerate(LAYERS):
    y = layer_y(idx)
    parts.append(rounded_rect(LAYER_X, y, LAYER_W, LAYER_H, layer["fill"], layer["stroke"], 2, 18))
    parts.append(svg_text(LAYER_X + LAYER_W / 2, y + 28, layer["title"], 22, "#111827", "600"))
    n = len(layer["boxes"])
    box_gap = 22
    box_w = (LAYER_W - 44 - box_gap * (n - 1)) / n
    box_h = 66
    box_y = y + 48
    for j, (title, sub) in enumerate(layer["boxes"]):
        x = LAYER_X + 22 + j * (box_w + box_gap)
        parts.append(f'<g filter="url(#shadow)">')
        parts.append(rounded_rect(x, box_y, box_w, box_h, "#D8ECFF" if idx == 0 else "#D7F7EE" if idx == 1 else "#F8D5E7" if idx == 2 else "#CFF7DC" if idx == 3 else "#FFF0BF" if idx == 4 else "#DED9FF" if idx == 5 else "#FFE1BF", "#64748B", 2, 15))
        parts.append("</g>")
        lines = [title] + wrap(sub, 12)
        start_y = box_y + 24 if len(lines) <= 2 else box_y + 18
        parts.append(multiline_text(x + box_w / 2, start_y, lines, size=15.5, fill="#0F172A", weight="600" if len(lines) == 1 else "500", line_h=19))
        box_centers[title] = (x + box_w / 2, box_y + box_h / 2)

# Flow arrows through columns
for name, color, x, labels in COLUMNS:
    points = []
    for label in labels:
        cx, cy = box_centers[label]
        points.append((cx, cy))
    for a, b in zip(points, points[1:]):
        x1, y1 = a
        x2, y2 = b
        parts.append(
            f'<path d="M{x1},{y1 + 34} C{x1},{(y1+y2)/2} {x2},{(y1+y2)/2} {x2},{y2 - 36}" '
            f'fill="none" stroke="{color}" stroke-width="4" marker-end="url(#arrow_{[k for k,v in COLORS.items() if v==color][0]})"/>'
        )
    # small label near top of first link
    parts.append(svg_text(points[0][0], points[0][1] + 60, name, 13.5, color, "700"))

# Cross-process dotted feedback loops
dash = "10 10"
parts.append(f'<path d="M1510,570 C1880,570 2000,705 1815,820" fill="none" stroke="{COLORS["orange"]}" stroke-width="3" stroke-dasharray="{dash}" marker-end="url(#arrow_orange)"/>')
parts.append(svg_text(1940, 670, "客户不通过 -> 复打样", 15, COLORS["orange"], "700"))
parts.append(f'<path d="M1110,570 C820,620 760,700 830,820" fill="none" stroke="{COLORS["green"]}" stroke-width="3" stroke-dasharray="{dash}" marker-end="url(#arrow_green)"/>')
parts.append(svg_text(720, 670, "测试不通过 -> 下一版", 15, COLORS["green"], "700"))
parts.append(f'<path d="M1710,720 C1900,755 1900,875 1660,960" fill="none" stroke="{COLORS["gray"]}" stroke-width="3" stroke-dasharray="{dash}" marker-end="url(#arrow_gray)"/>')
parts.append(svg_text(1900, 810, "文件同步/归档", 15, COLORS["gray"], "700"))

# Legend
legend_y = H - 94
parts.append(rounded_rect(34, legend_y - 32, 1300, 58, "#F8FAFC", "#CBD5E1", 1, 0))
lx = 50
for name, color, _, _ in COLUMNS:
    parts.append(f'<rect x="{lx}" y="{legend_y - 16}" width="22" height="22" fill="{color}" opacity="0.82"/>')
    parts.append(svg_text(lx + 30, legend_y + 1, name, 16, "#111827", "600", anchor="start"))
    lx += 205

parts.append(svg_text(W - 52, H - 32, "版本：v1.0  |  适用：研发样品管理飞书自建应用方案", 15, "#64748B", "400", anchor="end"))
parts.append("</svg>")

OUT.write_text("\n".join(parts), encoding="utf-8")
print(OUT)
