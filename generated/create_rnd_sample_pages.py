from html import escape
from pathlib import Path


OUT_DIR = Path("prototypes/pages")
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


def text(x, y, body, size=22, color=INK, weight="400", anchor="start"):
    return (
        f'<text x="{x}" y="{y}" text-anchor="{anchor}" '
        f'font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">{escape(body)}</text>'
    )


def rect(x, y, w, h, fill=CARD, stroke=BORDER, sw=2, rx=18):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'


def line(x1, y1, x2, y2, color=BORDER, sw=2):
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{sw}"/>'


def pill(x, y, label, fill="#EEF4FF", color=BLUE, w=None):
    w = w or max(88, 17 * len(label) + 36)
    return rect(x, y, w, 36, fill, "none", 0, 18) + text(x + w / 2, y + 25, label, 17, color, "700", "middle")


def svg_start(w, h, title):
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">',
        "<defs>",
        '<filter id="shadow" x="-25%" y="-25%" width="150%" height="150%"><feDropShadow dx="0" dy="8" stdDeviation="10" flood-color="#334155" flood-opacity="0.16"/></filter>',
        "</defs>",
        f'<rect width="100%" height="100%" fill="{BG}"/>',
        text(w / 2, 50, title, 32, INK, "800", "middle"),
    ]


def svg_end():
    return ["</svg>"]


def phone_shell(title, subtitle):
    w, h = 520, 980
    p = svg_start(w, h, title)
    p += [
        '<g filter="url(#shadow)">',
        rect(52, 92, 416, 820, "#101828", "#101828", 0, 42),
        rect(68, 110, 384, 784, "#F8FAFC", "#E2E8F0", 2, 32),
        "</g>",
        rect(202, 124, 116, 14, "#0F172A", "#0F172A", 0, 7),
        text(92, 176, title, 27, INK, "800"),
        text(92, 208, subtitle, 17, MUTED, "500"),
    ]
    return p


def phone_nav(p):
    p.append(rect(68, 822, 384, 72, "#FFFFFF", "#E5EAF2", 1, 0))
    for i, (label, color) in enumerate([("首页", BLUE), ("任务", MUTED), ("档案", MUTED), ("我的", MUTED)]):
        cx = 122 + i * 88
        p.append(f'<circle cx="{cx}" cy="850" r="10" fill="{color}"/>')
        p.append(text(cx, 878, label, 15, color, "700", "middle"))


def input_row(p, y, label, value, required=False):
    p.append(text(92, y + 20, ("*" if required else "") + label, 16, RED if required else MUTED, "700"))
    p.append(rect(92, y + 30, 336, 50, "#FFFFFF", "#E2E8F0", 2, 12))
    p.append(text(110, y + 62, value, 17, INK, "500"))


def pc_shell(title):
    w, h = 1280, 860
    p = svg_start(w, h, title)
    p += [
        '<g filter="url(#shadow)">',
        rect(64, 96, 1152, 700, "#FFFFFF", "#D5DFEC", 2, 22),
        "</g>",
        rect(64, 96, 1152, 72, "#F8FBFF", "#D5DFEC", 2, 22),
        text(94, 142, title, 26, INK, "800"),
        text(1080, 142, "研发总监 · 赵新武", 18, MUTED, "600"),
        rect(64, 168, 200, 628, "#F8FAFC", "#E2E8F0", 1, 0),
    ]
    for i, (label, active) in enumerate([("首页看板", title == "PC首页看板"), ("样品需求", "需求" in title), ("研发任务", "任务" in title), ("待测试", "测试" in title), ("核价文件", "核价" in title), ("产品档案", "档案" in title), ("系统配置", False)]):
        y = 205 + i * 58
        p.append(rect(88, y, 152, 42, "#EAF2FF" if active else "#F8FAFC", "none", 0, 12))
        p.append(text(112, y + 28, label, 17, BLUE if active else MUTED, "800" if active else "600"))
    return p


def save(name, parts):
    path = OUT_DIR / f"{name}.svg"
    path.write_text("\n".join(parts), encoding="utf-8")
    return path


def mobile_home():
    p = phone_shell("研发样品管理", "飞书工作台入口 · 张丽 / 销售内勤")
    p.append(pill(92, 232, "今日待办 6", "#EAF2FF", BLUE, 126))
    p.append(pill(232, 232, "逾期 1", "#FFF4E5", ORANGE, 90))
    cards = [("新样品需求", "500g香卤大肠头 · 客户A", "期望完成：06-25  样品：6袋", "待审核", ORANGE),
             ("待寄样", "黑椒鸡柳料理包 A1", "快递信息未登记", "寄样", GREEN),
             ("待财务核价", "调理鸡排 A2", "Excel已生成，待财务确认", "核价", BLUE)]
    for i, (a, b, c, tag, color) in enumerate(cards):
        y = 288 + i * 118
        p.append(rect(92, y, 336, 98, "#FFFFFF", "#E2E8F0", 2, 18))
        p.append(text(114, y + 34, a, 20, INK, "800"))
        p.append(text(114, y + 62, b, 16, MUTED, "600"))
        p.append(text(114, y + 84, c, 15, MUTED))
        p.append(pill(332, y + 22, tag, "#ECFDF5" if color == GREEN else "#FFF7ED" if color == ORANGE else "#EAF2FF", color, 72))
    p.append(text(92, 670, "快捷入口", 20, INK, "800"))
    for i, (label, color) in enumerate([("录需求", BLUE), ("我的任务", GREEN), ("待测试", TEAL), ("核价文件", ORANGE)]):
        x = 92 + (i % 2) * 176
        y = 700 + (i // 2) * 62
        p.append(rect(x, y, 158, 48, "#FFFFFF", "#E2E8F0", 2, 14))
        p.append(f'<circle cx="{x+28}" cy="{y+24}" r="12" fill="{color}"/>')
        p.append(text(x + 52, y + 31, label, 17, INK, "700"))
    phone_nav(p)
    return save("01_手机端_飞书首页", p + svg_end())


def mobile_request():
    p = phone_shell("新建样品需求", "销售内勤录入 · 客户需求")
    for idx, row in enumerate([
        ("客户名称", "客户A", True),
        ("产品名称", "500g香卤大肠头", True),
        ("产品类型", "腊制品 / 酱卤肉制品", True),
        ("规格要求", "500g/袋，20袋/箱", True),
        ("样品数量", "6袋", True),
        ("期望完成日期", "2026-06-25", True),
    ]):
        input_row(p, 232 + idx * 90, *row)
    p.append(text(92, 784, "附件：客户图片、口味参考、包装要求", 16, MUTED, "600"))
    p.append(rect(92, 820, 160, 50, "#FFFFFF", BLUE, 2, 14))
    p.append(text(172, 853, "保存草稿", 17, BLUE, "800", "middle"))
    p.append(rect(268, 820, 160, 50, BLUE, BLUE, 2, 14))
    p.append(text(348, 853, "提交审核", 17, "#FFFFFF", "800", "middle"))
    return save("02_手机端_样品需求录入", p + svg_end())


def mobile_experiment():
    p = phone_shell("打样实验单", "任务：香卤大肠头 A0 · 研发：黄丽金")
    p.append(pill(92, 232, "打样中", "#ECFDF5", GREEN, 92))
    p.append(pill(198, 232, "草稿已保存", "#EAF2FF", BLUE, 126))
    for idx, row in enumerate([
        ("产品名称", "500g香卤大肠头", True),
        ("产品规格", "500g/袋，20袋/箱", True),
        ("工序节点", "清洗 / 保水 / 卤制", True),
        ("研发参考出成", "89 kg", True),
    ]):
        input_row(p, 284 + idx * 86, *row)
    p.append(text(92, 646, "原料明细", 20, INK, "800"))
    for i, (name, kg, util) in enumerate([("冻猪大肠头（预煮）", "100kg", "95%"), ("仲景去腥粉", "0.03kg", "100%")]):
        y = 670 + i * 56
        p.append(rect(92, y, 336, 46, "#FFFFFF", "#E2E8F0", 2, 12))
        p.append(text(108, y + 30, name, 15, INK, "700"))
        p.append(text(316, y + 30, kg, 14, MUTED, "600"))
        p.append(text(378, y + 30, util, 14, BLUE, "700"))
    p.append(rect(92, 804, 160, 50, "#FFFFFF", BLUE, 2, 14))
    p.append(text(172, 837, "上传照片", 17, BLUE, "800", "middle"))
    p.append(rect(268, 804, 160, 50, BLUE, BLUE, 2, 14))
    p.append(text(348, 837, "提交实验单", 17, "#FFFFFF", "800", "middle"))
    return save("03_手机端_打样实验单", p + svg_end())


def mobile_test():
    p = phone_shell("样品测试确认", "测试/品控 · 黑椒鸡柳 A1")
    p.append(pill(92, 232, "待测试", "#EAF2FF", BLUE, 92))
    for idx, row in enumerate([
        ("复热方式", "微波 / 水浴", True),
        ("口味评分", "8.5 / 10", True),
        ("口感评价", "鸡肉嫩度合适，汤汁略少", True),
        ("出水情况", "轻微出水，可接受", False),
    ]):
        input_row(p, 286 + idx * 96, *row)
    p.append(text(92, 696, "测试结论", 20, INK, "800"))
    for i, (label, color) in enumerate([("通过", GREEN), ("复打样", ORANGE), ("停止", RED)]):
        x = 92 + i * 112
        p.append(rect(x, 724, 96, 46, "#FFFFFF", color, 2, 14))
        p.append(text(x + 48, 754, label, 17, color, "800", "middle"))
    p.append(rect(92, 812, 336, 50, BLUE, BLUE, 2, 14))
    p.append(text(260, 845, "提交测试结果", 17, "#FFFFFF", "800", "middle"))
    return save("04_手机端_测试确认", p + svg_end())


def pc_dashboard():
    p = pc_shell("PC首页看板")
    for i, (label, val, color) in enumerate([("待审核需求", "12", BLUE), ("打样中", "8", GREEN), ("待测试", "5", TEAL), ("待财务核价", "3", ORANGE)]):
        x = 300 + i * 220
        p.append(rect(x, 210, 190, 96, "#FFFFFF", "#E2E8F0", 2, 18))
        p.append(text(x + 22, 244, label, 17, MUTED, "700"))
        p.append(text(x + 22, 288, val, 40, color, "900"))
    p.append(text(300, 360, "样品任务进度", 24, INK, "800"))
    p.append(rect(300, 386, 860, 300, "#FFFFFF", "#E2E8F0", 2, 18))
    headers = ["产品", "版本", "负责人", "当前状态", "期限", "操作"]
    xs = [330, 560, 660, 780, 980, 1100]
    for i, h in enumerate(headers):
        p.append(text(xs[i], 426, h, 16, MUTED, "800"))
    p.append(line(320, 444, 1140, 444))
    rows = [
        ("500g香卤大肠头", "A0", "黄丽金", "待测试", "06-20", "查看"),
        ("黑椒鸡柳料理包", "A1", "李工", "待寄样", "06-21", "查看"),
        ("调理鸡排", "A2", "王工", "复打样", "06-24", "查看"),
        ("腊肠新口味", "A0", "陈工", "待核价", "06-25", "查看"),
    ]
    for r, row in enumerate(rows):
        y = 484 + r * 50
        for i, val in enumerate(row):
            p.append(text(xs[i], y, val, 16, BLUE if i == 5 else INK, "800" if i in (0, 3, 5) else "500"))
        p.append(line(320, y + 18, 1140, y + 18, "#EDF2F7", 1))
    return save("05_PC端-首页看板", p + svg_end())


def pc_assign():
    p = pc_shell("研发任务分发")
    p.append(text(300, 230, "待分发样品需求", 24, INK, "800"))
    p.append(rect(300, 260, 860, 420, "#FFFFFF", "#E2E8F0", 2, 18))
    p.append(text(330, 306, "500g香卤大肠头", 24, INK, "900"))
    p.append(pill(532, 282, "资料完整", "#ECFDF5", GREEN, 100))
    labels = [("客户", "客户A"), ("规格", "500g/袋，20袋/箱"), ("样品数量", "6袋"), ("期望日期", "2026-06-25"), ("产品类型", "腊制品 / 酱卤肉制品")]
    for i, (k, v) in enumerate(labels):
        y = 350 + i * 46
        p.append(text(330, y, k, 16, MUTED, "700"))
        p.append(text(440, y, v, 17, INK, "600"))
    p.append(text(760, 350, "指派研发人员", 18, MUTED, "700"))
    p.append(rect(760, 368, 250, 48, "#FFFFFF", "#E2E8F0", 2, 12))
    p.append(text(780, 400, "黄丽金", 17, INK, "700"))
    p.append(text(760, 452, "任务期限", 18, MUTED, "700"))
    p.append(rect(760, 470, 250, 48, "#FFFFFF", "#E2E8F0", 2, 12))
    p.append(text(780, 502, "2026-06-20", 17, INK, "700"))
    p.append(rect(760, 574, 120, 48, "#FFFFFF", BLUE, 2, 14))
    p.append(text(820, 606, "退回补充", 17, BLUE, "800", "middle"))
    p.append(rect(900, 574, 120, 48, BLUE, BLUE, 2, 14))
    p.append(text(960, 606, "分发任务", 17, "#FFFFFF", "800", "middle"))
    return save("06_PC端-研发任务分发", p + svg_end())


def pc_pricing():
    p = pc_shell("核价文件")
    p.append(text(300, 230, "核价文件版本", 24, INK, "800"))
    p.append(rect(300, 260, 860, 420, "#FFFFFF", "#E2E8F0", 2, 18))
    headers = ["产品", "样品版本", "核价版本", "生成时间", "状态", "操作"]
    xs = [330, 540, 660, 800, 960, 1080]
    for i, h in enumerate(headers):
        p.append(text(xs[i], 310, h, 16, MUTED, "800"))
    p.append(line(320, 328, 1140, 328))
    rows = [
        ("500g香卤大肠头", "A1", "A1-核价V1", "06-17 15:20", "已提交财务", "下载"),
        ("黑椒鸡柳料理包", "A1", "A1-核价V2", "06-16 11:10", "已报价", "下载"),
        ("调理鸡排", "A2", "A2-核价V1", "06-15 17:35", "待提交", "生成"),
    ]
    for r, row in enumerate(rows):
        y = 372 + r * 62
        for i, val in enumerate(row):
            p.append(text(xs[i], y, val, 15.5, BLUE if i == 5 else INK, "800" if i in (0, 4, 5) else "500"))
        p.append(line(320, y + 20, 1140, y + 20, "#EDF2F7", 1))
    p.append(rect(300, 590, 220, 50, BLUE, BLUE, 2, 14))
    p.append(text(410, 623, "按模板生成Excel", 17, "#FFFFFF", "800", "middle"))
    return save("07_PC端-核价文件", p + svg_end())


def pc_archive():
    p = pc_shell("产品档案")
    p.append(text(300, 230, "产品档案：500g香卤大肠头", 24, INK, "800"))
    p.append(pill(682, 204, "客户通过", "#ECFDF5", GREEN, 104))
    p.append(pill(802, 204, "待下单", "#EAF2FF", BLUE, 90))
    p.append(rect(300, 270, 260, 390, "#F8FAFC", "#E2E8F0", 2, 18))
    p.append(text(326, 312, "版本时间线", 20, INK, "800"))
    for i, (ver, desc, color) in enumerate([("A0", "小试 · 测试不通过", ORANGE), ("A1", "复打样 · 测试通过", GREEN), ("A1-核价V1", "已提交财务", BLUE), ("BOM-V1", "待录入", MUTED)]):
        y = 360 + i * 72
        p.append(f'<circle cx="340" cy="{y}" r="10" fill="{color}"/>')
        if i < 3:
            p.append(line(340, y + 12, 340, y + 58, "#CBD5E1", 3))
        p.append(text(365, y - 2, ver, 16, INK, "800"))
        p.append(text(365, y + 24, desc, 14, MUTED, "500"))
    p.append(text(600, 312, "A1 版本资料", 22, INK, "800"))
    cards = [("打样实验单", "配方/工序/损耗/得率", BLUE), ("现场照片", "12张 · 原料/称重/成品", GREEN), ("测试记录", "复热口感通过", TEAL), ("核价文件", "Excel · A1-核价V1", ORANGE), ("寄样记录", "顺丰 SF123456", MUTED), ("工艺/BOM任务", "待创建/待录入", RED)]
    for i, (title, meta, color) in enumerate(cards):
        x = 600 + (i % 3) * 190
        y = 340 + (i // 3) * 120
        p.append(rect(x, y, 170, 86, "#FFFFFF", "#E2E8F0", 2, 18))
        p.append(f'<circle cx="{x+28}" cy="{y+28}" r="12" fill="{color}"/>')
        p.append(text(x + 48, y + 34, title, 17, INK, "800"))
        p.append(text(x + 18, y + 64, meta, 13.5, MUTED, "500"))
    p.append(rect(600, 610, 560, 58, "#F8FAFC", "#E2E8F0", 2, 14))
    for i, (label, val) in enumerate([("研发出成", "89kg"), ("参考包数", "178袋"), ("得率", "84.55%"), ("文件数", "23个")]):
        x = 630 + i * 130
        p.append(text(x, 634, label, 13, MUTED, "600"))
        p.append(text(x, 660, val, 19, INK, "900"))
    return save("08_PC端-产品档案", p + svg_end())


def overview(files):
    w, h = 2200, 1650
    p = svg_start(w, h, "研发样品管理系统 - 页面总览")
    positions = [(60, 110), (610, 110), (1160, 110), (1710, 110), (60, 860), (610, 860), (1160, 860), (1710, 860)]
    for (x, y), path in zip(positions, files):
        label = path.stem
        is_phone = "手机端" in label
        fw, fh = (420, 720) if is_phone else (460, 310)
        p.append(rect(x, y, 500, 700, "#FFFFFF", "#D8E0EA", 2, 20))
        p.append(text(x + 24, y + 42, label, 22, INK, "800"))
        # Embed as image by relative path for SVG consumers.
        href = escape(path.name)
        p.append(f'<image href="{href}" x="{x+32}" y="{y+70}" width="{fw}" height="{fh}" preserveAspectRatio="xMidYMid meet"/>')
    p.append(text(w / 2, h - 36, "每张页面已单独导出 PNG，可用于评审、飞书沟通或放入方案文档。", 20, MUTED, "600", "middle"))
    return save("00_页面总览", p + svg_end())


OUT_DIR.mkdir(parents=True, exist_ok=True)
files = [
    mobile_home(),
    mobile_request(),
    mobile_experiment(),
    mobile_test(),
    pc_dashboard(),
    pc_assign(),
    pc_pricing(),
    pc_archive(),
]
overview(files)
print("\n".join(str(p) for p in [OUT_DIR / "00_页面总览.svg", *files]))
