from html import escape
from pathlib import Path


OUT = Path("prototypes/飞书自建应用研发样品管理系统_界面原型图.svg")

W = 2600
H = 1700
FONT = "Arial, 'Microsoft YaHei', 'PingFang SC', sans-serif"

BLUE = "#246BFE"
GREEN = "#16A34A"
TEAL = "#0F9F9A"
ORANGE = "#F59E0B"
RED = "#EF4444"
INK = "#111827"
MUTED = "#64748B"
BORDER = "#D8E0EA"
BG = "#F6F8FB"
CARD = "#FFFFFF"


def text(x, y, body, size=24, color=INK, weight="400", anchor="start"):
    return (
        f'<text x="{x}" y="{y}" text-anchor="{anchor}" '
        f'font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">{escape(body)}</text>'
    )


def multi(x, y, lines, size=22, color=INK, weight="400", anchor="start", line_h=32):
    out = [f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">']
    for i, line in enumerate(lines):
        out.append(f'<tspan x="{x}" dy="{0 if i == 0 else line_h}">{escape(line)}</tspan>')
    out.append("</text>")
    return "\n".join(out)


def rect(x, y, w, h, fill=CARD, stroke=BORDER, sw=2, rx=18, dash=None):
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{dash_attr}/>'


def line(x1, y1, x2, y2, color=BORDER, sw=2):
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{sw}"/>'


def pill(x, y, label, fill="#EEF4FF", color=BLUE, w=None):
    w = w or max(96, 18 * len(label) + 38)
    return rect(x, y, w, 38, fill, "none", 0, 19) + text(x + w / 2, y + 26, label, 18, color, "700", "middle")


def shadow():
    return '<filter id="shadow" x="-25%" y="-25%" width="150%" height="150%"><feDropShadow dx="0" dy="8" stdDeviation="10" flood-color="#334155" flood-opacity="0.15"/></filter>'


def phone_frame(x, y, title, subtitle):
    parts = [
        f'<g filter="url(#shadow)">',
        rect(x, y, 430, 860, "#101828", "#101828", 0, 42),
        rect(x + 16, y + 18, 398, 824, "#F8FAFC", "#E2E8F0", 2, 32),
        "</g>",
        rect(x + 164, y + 32, 102, 14, "#0F172A", "#0F172A", 0, 7),
        text(x + 38, y + 84, title, 27, INK, "800"),
        text(x + 38, y + 116, subtitle, 18, MUTED, "500"),
    ]
    return parts


def phone_nav(x, y):
    labels = [("首页", BLUE), ("任务", MUTED), ("档案", MUTED), ("我的", MUTED)]
    parts = [rect(x + 16, y + 766, 398, 76, "#FFFFFF", "#E5EAF2", 1, 0)]
    for i, (label, color) in enumerate(labels):
        cx = x + 68 + i * 98
        parts.append(f'<circle cx="{cx}" cy="{y+793}" r="11" fill="{color}" opacity="0.95"/>')
        parts.append(text(cx, y + 823, label, 16, color, "700", "middle"))
    return parts


def input_row(x, y, label, value, required=False):
    parts = [
        text(x, y + 24, ("*" if required else "") + label, 17, RED if required else MUTED, "600"),
        rect(x, y + 34, 348, 52, "#FFFFFF", "#E2E8F0", 2, 12),
        text(x + 18, y + 68, value, 18, INK, "500"),
    ]
    return parts


def pc_panel(x, y, w, h, title):
    return [
        f'<g filter="url(#shadow)">',
        rect(x, y, w, h, "#FFFFFF", "#D5DFEC", 2, 22),
        "</g>",
        rect(x, y, w, 72, "#F8FBFF", "#D5DFEC", 2, 22),
        text(x + 30, y + 45, title, 26, INK, "800"),
    ]


parts = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
    "<defs>",
    shadow(),
    "</defs>",
    f'<rect width="100%" height="100%" fill="{BG}"/>',
    text(W / 2, 58, "飞书自建应用研发样品管理系统 - 界面原型图 v1.0", 40, INK, "800", "middle"),
    text(W / 2, 96, "手机/平板现场录入 + PC管理看板 + 样品档案归档 + Excel核价文件", 22, MUTED, "500", "middle"),
]

# Phone 1: Feishu app home
px, py = 90, 150
parts += phone_frame(px, py, "研发样品管理", "飞书工作台入口 · 张丽 / 销售内勤")
parts.append(pill(px + 38, py + 142, "今日待办 6", "#EAF2FF", BLUE, 130))
parts.append(pill(px + 184, py + 142, "逾期 1", "#FFF4E5", ORANGE, 96))
parts.append(rect(px + 38, py + 198, 354, 112, "#FFFFFF", "#E2E8F0", 2, 18))
parts.append(text(px + 62, py + 232, "新样品需求", 22, INK, "800"))
parts.append(text(px + 62, py + 262, "500g香卤大肠头 · 客户A", 18, MUTED, "500"))
parts.append(pill(px + 280, py + 221, "待审核", "#FFF7ED", ORANGE, 82))
parts.append(text(px + 62, py + 292, "期望完成：06-25   样品：6袋", 17, MUTED))
parts.append(rect(px + 38, py + 330, 354, 112, "#FFFFFF", "#E2E8F0", 2, 18))
parts.append(text(px + 62, py + 364, "待寄样", 22, INK, "800"))
parts.append(text(px + 62, py + 394, "黑椒鸡柳料理包 A1", 18, MUTED, "500"))
parts.append(pill(px + 282, py + 352, "寄样", "#ECFDF5", GREEN, 72))
parts.append(text(px + 62, py + 424, "快递信息未登记", 17, MUTED))
parts.append(text(px + 38, py + 492, "快捷入口", 21, INK, "800"))
for i, (label, color) in enumerate([("录需求", BLUE), ("我的任务", GREEN), ("待测试", TEAL), ("核价文件", ORANGE)]):
    bx = px + 38 + (i % 2) * 182
    by = py + 520 + (i // 2) * 84
    parts.append(rect(bx, by, 164, 62, "#FFFFFF", "#E2E8F0", 2, 16))
    parts.append(f'<circle cx="{bx+32}" cy="{by+31}" r="15" fill="{color}" opacity="0.9"/>')
    parts.append(text(bx + 58, by + 39, label, 19, INK, "700"))
parts += phone_nav(px, py)

# Phone 2: Experiment form
px2, py2 = 570, 150
parts += phone_frame(px2, py2, "打样实验单", "任务：香卤大肠头 A0 · 研发：黄丽金")
parts.append(pill(px2 + 38, py2 + 142, "打样中", "#ECFDF5", GREEN, 94))
parts.append(pill(px2 + 146, py2 + 142, "草稿已保存", "#EAF2FF", BLUE, 128))
for idx, (label, value, required) in enumerate([
    ("产品名称", "500g香卤大肠头", True),
    ("产品规格", "500g/袋，20袋/箱", True),
    ("工序节点", "清洗 / 保水 / 卤制", True),
    ("研发参考出成", "89 kg", True),
]):
    parts += input_row(px2 + 42, py2 + 194 + idx * 98, label, value, required)
parts.append(text(px2 + 42, py2 + 608, "原料明细", 21, INK, "800"))
for i, (name, kg, util) in enumerate([("冻猪大肠头（预煮）", "100kg", "95%"), ("仲景去腥粉", "0.03kg", "100%")]):
    y = py2 + 630 + i * 62
    parts.append(rect(px2 + 42, y, 348, 52, "#FFFFFF", "#E2E8F0", 2, 12))
    parts.append(text(px2 + 58, y + 33, name, 17, INK, "700"))
    parts.append(text(px2 + 300, y + 33, kg, 16, MUTED, "600"))
    parts.append(text(px2 + 358, y + 33, util, 16, BLUE, "700"))
parts.append(rect(px2 + 42, py2 + 748, 164, 50, "#FFFFFF", BLUE, 2, 14))
parts.append(text(px2 + 124, py2 + 781, "上传照片", 18, BLUE, "800", "middle"))
parts.append(rect(px2 + 226, py2 + 748, 164, 50, BLUE, BLUE, 2, 14))
parts.append(text(px2 + 308, py2 + 781, "提交实验单", 18, "#FFFFFF", "800", "middle"))

# PC dashboard
cx, cy = 1050, 150
parts += pc_panel(cx, cy, 1420, 620, "PC管理端：首页看板")
parts.append(text(cx + 1260, cy + 45, "研发总监 · 赵新武", 20, MUTED, "600"))
parts.append(rect(cx, cy + 72, 210, 548, "#F8FAFC", "#E2E8F0", 1, 0))
for i, (label, active) in enumerate([("首页看板", True), ("样品需求", False), ("研发任务", False), ("待测试", False), ("核价文件", False), ("产品档案", False), ("系统配置", False)]):
    y = cy + 108 + i * 58
    parts.append(rect(cx + 22, y, 166, 42, "#EAF2FF" if active else "#F8FAFC", "none", 0, 12))
    parts.append(text(cx + 48, y + 28, label, 18, BLUE if active else MUTED, "800" if active else "600"))
for i, (label, val, color) in enumerate([("待审核需求", "12", BLUE), ("打样中", "8", GREEN), ("待测试", "5", TEAL), ("待财务核价", "3", ORANGE)]):
    x = cx + 240 + i * 280
    parts.append(rect(x, cy + 110, 250, 112, "#FFFFFF", "#E2E8F0", 2, 18))
    parts.append(text(x + 24, cy + 146, label, 19, MUTED, "700"))
    parts.append(text(x + 24, cy + 194, val, 42, color, "900"))
parts.append(text(cx + 240, cy + 270, "样品任务进度", 24, INK, "800"))
parts.append(rect(cx + 240, cy + 294, 1110, 296, "#FFFFFF", "#E2E8F0", 2, 18))
headers = ["产品", "版本", "负责人", "当前状态", "期限", "操作"]
col_x = [cx + 268, cx + 560, cx + 690, cx + 845, cx + 1080, cx + 1250]
for i, h in enumerate(headers):
    parts.append(text(col_x[i], cy + 334, h, 17, MUTED, "800"))
parts.append(line(cx + 260, cy + 350, cx + 1330, cy + 350))
rows = [
    ("500g香卤大肠头", "A0", "黄丽金", "待测试", "06-20", "查看"),
    ("黑椒鸡柳料理包", "A1", "李工", "待寄样", "06-21", "查看"),
    ("调理鸡排", "A2", "王工", "复打样", "06-24", "查看"),
    ("腊肠新口味", "A0", "陈工", "待核价", "06-25", "查看"),
]
for r, row in enumerate(rows):
    y = cy + 386 + r * 50
    for i, val in enumerate(row):
        color = BLUE if i == 5 else INK
        weight = "800" if i in (0, 3, 5) else "500"
        parts.append(text(col_x[i], y, val, 17, color, weight))
    parts.append(line(cx + 260, y + 16, cx + 1330, y + 16, "#EDF2F7", 1))

# PC archive detail
dx, dy = 1050, 825
parts += pc_panel(dx, dy, 1420, 660, "产品档案：500g香卤大肠头")
parts.append(pill(dx + 1060, dy + 20, "客户通过", "#ECFDF5", GREEN, 106))
parts.append(pill(dx + 1180, dy + 20, "待下单", "#EAF2FF", BLUE, 96))
parts.append(rect(dx + 30, dy + 96, 280, 510, "#F8FAFC", "#E2E8F0", 2, 18))
parts.append(text(dx + 56, dy + 136, "版本时间线", 22, INK, "800"))
timeline = [("A0", "小试 · 测试不通过", ORANGE), ("A1", "复打样 · 测试通过", GREEN), ("A1-核价V1", "已提交财务", BLUE), ("BOM-V1", "待录入", MUTED)]
for i, (ver, desc, color) in enumerate(timeline):
    y = dy + 184 + i * 84
    parts.append(f'<circle cx="{dx+70}" cy="{y}" r="12" fill="{color}"/>')
    if i < len(timeline) - 1:
        parts.append(line(dx + 70, y + 14, dx + 70, y + 70, "#CBD5E1", 3))
    parts.append(text(dx + 96, y - 2, ver, 18, INK, "800"))
    parts.append(text(dx + 96, y + 26, desc, 16, MUTED, "500"))
parts.append(text(dx + 350, dy + 126, "A1 版本资料", 24, INK, "800"))
for i, (title, meta, color) in enumerate([
    ("打样实验单", "配方/工序/损耗/得率", BLUE),
    ("现场照片", "12张 · 原料/称重/成品", GREEN),
    ("测试记录", "复热口感通过", TEAL),
    ("核价文件", "Excel · A1-核价V1", ORANGE),
    ("寄样记录", "顺丰 SF123456", MUTED),
    ("工艺/BOM任务", "待创建/待录入", RED),
]):
    x = dx + 350 + (i % 3) * 330
    y = dy + 160 + (i // 3) * 140
    parts.append(rect(x, y, 300, 106, "#FFFFFF", "#E2E8F0", 2, 18))
    parts.append(f'<circle cx="{x+34}" cy="{y+34}" r="15" fill="{color}" opacity="0.9"/>')
    parts.append(text(x + 62, y + 40, title, 20, INK, "800"))
    parts.append(text(x + 24, y + 78, meta, 17, MUTED, "500"))
parts.append(text(dx + 350, dy + 478, "关键数据", 24, INK, "800"))
parts.append(rect(dx + 350, dy + 510, 960, 76, "#F8FAFC", "#E2E8F0", 2, 16))
for i, (label, val) in enumerate([("研发出成", "89kg"), ("参考包数", "178袋"), ("得率", "84.55%"), ("文件数", "23个")]):
    x = dx + 390 + i * 230
    parts.append(text(x, dy + 540, label, 16, MUTED, "600"))
    parts.append(text(x, dy + 570, val, 24, INK, "900"))

parts.append(text(W / 2, H - 48, "原型说明：最终系统会以飞书为入口，手机端负责现场动作，PC端负责管理和归档。", 20, MUTED, "600", "middle"))
parts.append("</svg>")

OUT.write_text("\n".join(parts), encoding="utf-8")
print(OUT)
