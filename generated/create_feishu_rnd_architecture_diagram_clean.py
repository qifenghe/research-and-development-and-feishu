from html import escape
from pathlib import Path


OUT = Path("diagrams/飞书自建应用_研发样品管理平台_总体逻辑架构图_清晰版.svg")

W = 2400
H = 1320
FONT = "Arial, 'Microsoft YaHei', 'PingFang SC', sans-serif"
LAYER_X = 400
LAYER_W = 1640
LAYER_H = 130
LAYER_Y0 = 96
GAP_Y = 19
BOX_GAP = 18
BOX_W = (LAYER_W - 44 - BOX_GAP * 6) / 7
BOX_H = 66
BOX_Y_OFFSET = 48

COLORS = {
    "demand": "#2F6FED",
    "dispatch": "#6D5BD0",
    "sample": "#0E9F6E",
    "test": "#008577",
    "pricing": "#E64694",
    "shipment": "#D97706",
    "archive": "#64748B",
}

CHAINS = [
    ("需求链路", "demand"),
    ("任务链路", "dispatch"),
    ("打样链路", "sample"),
    ("测试链路", "test"),
    ("核价链路", "pricing"),
    ("寄样/转单链路", "shipment"),
    ("归档链路", "archive"),
]

LAYERS = [
    ("1. 用户层：谁发起和处理业务", "#EAF3FF", "#8AB8F5", [
        ("销售内勤", "样品需求/寄样/反馈"),
        ("研发总监", "审核/分发/停止确认"),
        ("研发人员", "接受任务/现场打样"),
        ("测试/品控", "试吃/检测/结果确认"),
        ("财务", "核价/报价反馈"),
        ("业务员/管理层", "客户反馈/下单决策"),
        ("系统管理员", "权限/模板/归档策略"),
    ]),
    ("2. 统一入口层：通过飞书进入研发平台", "#E9FBF6", "#67DCC2", [
        ("飞书工作台", "研发样品管理入口"),
        ("任务待办卡片", "分发/接受/转派"),
        ("移动端H5", "手机/平板现场录入"),
        ("飞书审批确认", "审核/测试/停止"),
        ("PC管理端", "总监/内勤/财务查看"),
        ("飞书消息卡片", "寄样/反馈/报价提醒"),
        ("飞书云盘/看板", "文件链接/进度看板"),
    ]),
    ("3. 平台控制层：入口请求落到哪些控制服务", "#FFF0F7", "#F5A3C8", [
        ("API网关 + RBAC", "飞书身份/权限/菜单"),
        ("任务分发服务", "指派/接受/转派"),
        ("流程引擎 + 状态机", "需求/打样/测试/寄样"),
        ("测试审批服务", "结果/复打样/停止"),
        ("版本锁定服务", "A0/A1/核价V1"),
        ("客户反馈服务", "通过/调整/取消"),
        ("文件归档服务", "照片/附件/Excel"),
    ]),
    ("4. 业务服务层：研发样品全生命周期", "#EBFFF0", "#79E79A", [
        ("样品需求服务", "录入/退回/补充"),
        ("研发任务服务", "分发/接受/进度"),
        ("打样实验单服务", "配方/工序/损耗/得率"),
        ("测试确认服务", "通过/复打样/停止"),
        ("核价文件服务", "Excel生成/版本提交"),
        ("寄样反馈服务", "快递/客户反馈/待下单"),
        ("工艺BOM准备", "通过后生成任务"),
    ]),
    ("5. 工具与模板层：业务服务调用哪些工具", "#FFF9E8", "#F6D783", [
        ("需求表单模板", "客户/规格/要求/时间"),
        ("任务规则工具", "人员/优先级/期限"),
        ("计算工具", "领料/损耗/得率/包数"),
        ("测试记录模板", "口味/复热/检测/结论"),
        ("Excel模板引擎", "按现有核价表生成"),
        ("看板工具", "进度/逾期/统计"),
        ("文件预览下载", "Excel/PDF/图片"),
    ]),
    ("6. 数据归档层：沉淀哪些数据资产", "#F1EFFF", "#B8A7FF", [
        ("样品需求库", "客户/规格/要求/时间"),
        ("研发任务库", "负责人/期限/状态"),
        ("实验单配方库", "原料/工序/称重"),
        ("测试记录库", "测试/结论/附件"),
        ("核价文件库", "A0-V1/A1-V1"),
        ("寄样反馈库", "快递/客户结果"),
        ("产品档案库", "版本/附件/工艺/BOM"),
    ]),
    ("7. 基础设施层：数据、文件和接口由哪些资源承载", "#FFF2E5", "#F5B475", [
        ("公司主机/内网", "部署前端和后端"),
        ("PostgreSQL", "业务数据库"),
        ("NAS/对象存储", "照片和附件文件"),
        ("飞书开放平台", "登录/消息/审批"),
        ("Excel生成服务", "模板填充/导出"),
        ("预留接口", "ERP/财务/电子秤"),
        ("定时备份", "数据库/文件备份"),
    ]),
]


def text(x, y, body, size=18, color="#111827", weight="400", anchor="middle"):
    return (
        f'<text x="{x}" y="{y}" text-anchor="{anchor}" '
        f'font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">{escape(body)}</text>'
    )


def multiline(x, y, lines, size=16, color="#0F172A", weight="500", line_h=20):
    out = [f'<text x="{x}" y="{y}" text-anchor="middle" font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">']
    for i, line in enumerate(lines):
        out.append(f'<tspan x="{x}" dy="{0 if i == 0 else line_h}">{escape(line)}</tspan>')
    out.append("</text>")
    return "\n".join(out)


def rect(x, y, w, h, fill, stroke, rx=16, sw=2):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'


def marker(name, color):
    return f'''
    <marker id="{name}" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="{color}" />
    </marker>'''


def layer_y(i):
    return LAYER_Y0 + i * (LAYER_H + GAP_Y)


def box_xy(layer_idx, col_idx):
    y = layer_y(layer_idx) + BOX_Y_OFFSET
    x = LAYER_X + 22 + col_idx * (BOX_W + BOX_GAP)
    return x, y


parts = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
    "<defs>",
    '<filter id="shadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="#64748B" flood-opacity="0.18"/></filter>',
]
for k, c in COLORS.items():
    parts.append(marker(f"arrow_{k}", c))
parts += ["</defs>", '<rect width="100%" height="100%" fill="#FFFFFF"/>']

parts.append(text(W / 2, 44, "预制食品研发样品管理平台 v1.0 - 飞书自建应用总体逻辑架构图（清晰版）", 34, "#111827", "700"))
parts.append(text(W / 2, 76, "飞书做入口与协同，自研系统负责流程、版本、Excel核价文件和归档", 18, "#475569"))

centers = {}
box_fill_by_layer = ["#D8ECFF", "#D7F7EE", "#F8D5E7", "#CFF7DC", "#FFF0BF", "#DED9FF", "#FFE1BF"]

for li, (title, fill, stroke, boxes) in enumerate(LAYERS):
    y = layer_y(li)
    parts.append(rect(LAYER_X, y, LAYER_W, LAYER_H, fill, stroke, 18, 2))
    parts.append(text(LAYER_X + LAYER_W / 2, y + 28, title, 22, "#111827", "600"))
    for ci, (head, sub) in enumerate(boxes):
        x, by = box_xy(li, ci)
        parts.append('<g filter="url(#shadow)">')
        parts.append(rect(x, by, BOX_W, BOX_H, box_fill_by_layer[li], "#64748B", 14, 2))
        parts.append("</g>")
        lines = [head, sub]
        parts.append(multiline(x + BOX_W / 2, by + 24, lines, 15.5, "#0F172A", "600", 20))
        centers[(li, ci)] = (x + BOX_W / 2, by + BOX_H / 2)

# vertical chain arrows
for ci, (label, key) in enumerate(CHAINS):
    color = COLORS[key]
    for li in range(len(LAYERS) - 1):
        x1, y1 = centers[(li, ci)]
        x2, y2 = centers[(li + 1, ci)]
        parts.append(f'<path d="M{x1},{y1 + BOX_H/2 - 4} L{x2},{y2 - BOX_H/2 + 8}" fill="none" stroke="{color}" stroke-width="4" marker-end="url(#arrow_{key})"/>')
    x0, y0 = centers[(0, ci)]
    parts.append(text(x0, y0 + 57, label, 14, color, "700"))

# controlled feedback and stop loops
parts.append(f'<path d="M{centers[(3,3)][0]+18},{centers[(3,3)][1]+42} C{centers[(3,3)][0]+60},760 {centers[(3,2)][0]+40},760 {centers[(3,2)][0]},{centers[(3,2)][1]+42}" fill="none" stroke="{COLORS["sample"]}" stroke-width="3" stroke-dasharray="9 8" marker-end="url(#arrow_sample)"/>')
parts.append(text(1060, 754, "测试不通过 -> 复打样", 15, COLORS["sample"], "700"))
parts.append(f'<path d="M{centers[(3,5)][0]+20},{centers[(3,5)][1]+42} C1850,770 1760,875 {centers[(3,2)][0]+50},{centers[(3,2)][1]+42}" fill="none" stroke="{COLORS["shipment"]}" stroke-width="3" stroke-dasharray="9 8" marker-end="url(#arrow_shipment)"/>')
parts.append(text(1828, 780, "客户不通过 -> 新版本打样", 15, COLORS["shipment"], "700"))
parts.append(f'<path d="M{centers[(3,3)][0]+70},{centers[(3,3)][1]} C1910,620 2025,725 1990,880" fill="none" stroke="{COLORS["archive"]}" stroke-width="3" stroke-dasharray="9 8" marker-end="url(#arrow_archive)"/>')
parts.append(text(2018, 718, "停止打样 -> 废弃池归档", 15, COLORS["archive"], "700", anchor="end"))

# legend
legend_y = H - 92
parts.append(rect(38, legend_y - 30, 1560, 58, "#F8FAFC", "#CBD5E1", 0, 1))
lx = 58
for label, key in CHAINS:
    color = COLORS[key]
    parts.append(f'<rect x="{lx}" y="{legend_y - 15}" width="22" height="22" fill="{color}" opacity="0.86"/>')
    parts.append(text(lx + 32, legend_y + 2, label, 16, "#111827", "600", anchor="start"))
    lx += 215
parts.append(text(W - 62, H - 34, "版本：v1.0  |  适用：研发样品管理飞书自建应用方案", 15, "#64748B", "400", anchor="end"))
parts.append("</svg>")

OUT.write_text("\n".join(parts), encoding="utf-8")
print(OUT)
