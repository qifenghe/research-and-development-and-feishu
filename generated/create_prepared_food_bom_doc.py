from docx import Document
from docx.enum.section import WD_SECTION_START
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Inches, Pt, RGBColor


OUT = "docs/预制食品研发BOM采购询价与成本核价系统_三部门沟通稿.docx"

BLUE = RGBColor(46, 116, 181)
DARK_BLUE = RGBColor(31, 77, 120)
GRAY = RGBColor(89, 89, 89)
LIGHT_GRAY = "F2F4F7"
LIGHT_BLUE = "E8EEF5"
CALLOUT = "F4F6F9"
BLACK = RGBColor(0, 0, 0)


def set_run_font(run, size=None, bold=None, color=None, name="Microsoft YaHei"):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:ascii"), name)
    run._element.rPr.rFonts.set(qn("w:hAnsi"), name)
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if color is not None:
        run.font.color.rgb = color


def set_cell_shading(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=80, start=120, bottom=80, end=120):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for m, v in {"top": top, "start": start, "bottom": bottom, "end": end}.items():
        node = tc_mar.find(qn(f"w:{m}"))
        if node is None:
            node = OxmlElement(f"w:{m}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(v))
        node.set(qn("w:type"), "dxa")


def set_table_borders(table, color="D9DEE7", sz="6"):
    tbl_pr = table._tbl.tblPr
    borders = tbl_pr.first_child_found_in("w:tblBorders")
    if borders is None:
        borders = OxmlElement("w:tblBorders")
        tbl_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        tag = f"w:{edge}"
        element = borders.find(qn(tag))
        if element is None:
            element = OxmlElement(tag)
            borders.append(element)
        element.set(qn("w:val"), "single")
        element.set(qn("w:sz"), sz)
        element.set(qn("w:space"), "0")
        element.set(qn("w:color"), color)


def set_table_width(table, widths):
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    for row in table.rows:
        for idx, width in enumerate(widths):
            cell = row.cells[idx]
            cell.width = width
            set_cell_margins(cell)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            for p in cell.paragraphs:
                p.paragraph_format.space_after = Pt(0)
                for run in p.runs:
                    set_run_font(run, size=9.5)


def set_paragraph_border_bottom(paragraph, color="2E74B5", sz="12"):
    p = paragraph._p
    p_pr = p.get_or_add_pPr()
    p_bdr = p_pr.find(qn("w:pBdr"))
    if p_bdr is None:
        p_bdr = OxmlElement("w:pBdr")
        p_pr.append(p_bdr)
    bottom = p_bdr.find(qn("w:bottom"))
    if bottom is None:
        bottom = OxmlElement("w:bottom")
        p_bdr.append(bottom)
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), sz)
    bottom.set(qn("w:space"), "6")
    bottom.set(qn("w:color"), color)


def add_para(doc, text="", style=None, size=11, bold=False, color=BLACK, before=0, after=6, align=None):
    p = doc.add_paragraph(style=style)
    p.paragraph_format.space_before = Pt(before)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.10
    if align is not None:
        p.alignment = align
    if text:
        run = p.add_run(text)
        set_run_font(run, size=size, bold=bold, color=color)
    return p


def add_heading(doc, text, level=1):
    style = f"Heading {level}"
    p = doc.add_paragraph(style=style)
    p.paragraph_format.keep_with_next = True
    if level == 1:
        p.paragraph_format.space_before = Pt(16)
        p.paragraph_format.space_after = Pt(8)
        size, color = 16, BLUE
    elif level == 2:
        p.paragraph_format.space_before = Pt(12)
        p.paragraph_format.space_after = Pt(6)
        size, color = 13, BLUE
    else:
        p.paragraph_format.space_before = Pt(8)
        p.paragraph_format.space_after = Pt(4)
        size, color = 12, DARK_BLUE
    run = p.add_run(text)
    set_run_font(run, size=size, bold=True, color=color)
    return p


def add_bullets(doc, items):
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.left_indent = Inches(0.5)
        p.paragraph_format.first_line_indent = Inches(-0.25)
        run = p.add_run(item)
        set_run_font(run, size=10.5)


def add_numbered(doc, items):
    for item in items:
        p = doc.add_paragraph(style="List Number")
        p.paragraph_format.space_after = Pt(5)
        p.paragraph_format.left_indent = Inches(0.5)
        p.paragraph_format.first_line_indent = Inches(-0.25)
        run = p.add_run(item)
        set_run_font(run, size=10.5)


def add_callout(doc, title, body):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(table, color="DDE3EA", sz="4")
    cell = table.cell(0, 0)
    set_cell_shading(cell, CALLOUT)
    set_cell_margins(cell, top=130, bottom=130, start=160, end=160)
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(3)
    r = p.add_run(title)
    set_run_font(r, size=10.5, bold=True, color=DARK_BLUE)
    p2 = cell.add_paragraph()
    p2.paragraph_format.space_after = Pt(0)
    r2 = p2.add_run(body)
    set_run_font(r2, size=10.2, color=BLACK)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def add_table(doc, headers, rows, widths, header_fill=LIGHT_GRAY, font_size=9.3):
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    set_table_borders(table)
    hdr = table.rows[0].cells
    for i, h in enumerate(headers):
        set_cell_shading(hdr[i], header_fill)
        set_cell_margins(hdr[i])
        p = hdr[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(0)
        run = p.add_run(h)
        set_run_font(run, size=9.2, bold=True, color=BLACK)
    for row in rows:
        cells = table.add_row().cells
        for i, val in enumerate(row):
            p = cells[i].paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            if i > 0 and len(str(val)) <= 10:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run(str(val))
            set_run_font(run, size=font_size)
    set_table_width(table, widths)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return table


def setup_document():
    doc = Document()
    section = doc.sections[0]
    section.top_margin = Inches(1)
    section.bottom_margin = Inches(1)
    section.left_margin = Inches(1)
    section.right_margin = Inches(1)
    section.header_distance = Inches(0.492)
    section.footer_distance = Inches(0.492)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Microsoft YaHei"
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    normal.font.size = Pt(11)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.10

    for style_name, size, color in [
        ("Heading 1", 16, BLUE),
        ("Heading 2", 13, BLUE),
        ("Heading 3", 12, DARK_BLUE),
    ]:
        style = styles[style_name]
        style.font.name = "Microsoft YaHei"
        style._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
        style.font.size = Pt(size)
        style.font.color.rgb = color
        style.font.bold = True

    header = section.header.paragraphs[0]
    header.text = "预制食品研发 BOM、采购询价与成本核价系统"
    header.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    for run in header.runs:
        set_run_font(run, size=9, color=GRAY)

    footer = section.footer.paragraphs[0]
    footer.text = "三部门沟通稿 | 内部讨论使用"
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for run in footer.runs:
        set_run_font(run, size=9, color=GRAY)
    return doc


doc = setup_document()

# Cover / memo masthead
add_para(doc, "内部沟通稿", size=11, bold=True, color=GRAY, after=8)
p = doc.add_paragraph()
p.paragraph_format.space_after = Pt(4)
r = p.add_run("预制食品研发 BOM、采购询价与成本核价系统")
set_run_font(r, size=24, bold=True, color=BLACK)
p2 = doc.add_paragraph()
p2.paragraph_format.space_after = Pt(16)
r2 = p2.add_run("面向研发部、采购部、财务部的全流程使用说明与征询意见稿")
set_run_font(r2, size=13.5, color=GRAY)

meta = [
    ("适用范围", "冷冻即热菜、腊制品、生制调理品及后续扩展品类"),
    ("沟通对象", "研发部、采购部、财务部、管理层"),
    ("文档目的", "统一三部门对系统流程、数据责任、成本核算口径和试点范围的理解"),
    ("建议试点产品", "冷冻即热黑椒鸡柳料理包，250g/袋，20袋/箱"),
]
for label, value in meta:
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(2)
    rr = p.add_run(f"{label}：")
    set_run_font(rr, size=10.5, bold=True)
    rv = p.add_run(value)
    set_run_font(rv, size=10.5)
rule = doc.add_paragraph()
set_paragraph_border_bottom(rule)

add_callout(
    doc,
    "核心结论",
    "第一阶段不建议直接做大而全的生产 ERP，而应先打通“研发真实配方 -> 采购真实报价 -> 财务真实成本 -> 管理层确认是否量产”的闭环。"
)

add_heading(doc, "1. 建设背景与当前痛点", 1)
add_para(
    doc,
    "当前研发打样主要依赖纸质记录，配方修改频繁，实际用量、备料量、处理后重量和成品重量容易混在一起。录入人员与实验人员之间容易因字迹、口径和反推逻辑产生歧义，采购无法及时获得准确物料清单，财务也难以及时核算真实成本。",
)
add_bullets(doc, [
    "研发痛点：纸质记录不清、配方版本不可追溯、泡发/解冻/熟化损耗需要人工反推。",
    "采购痛点：不能及时拿到结构化配方，供应商报价、MOQ、梯度价、交期与配方脱节。",
    "财务痛点：物料、人工、水电、折旧、品控、管理费用、利润和税点缺少统一核价模型。",
    "管理痛点：一个产品是否值得量产，缺少及时、可复算、可追溯的数据依据。",
])

add_heading(doc, "2. 系统定位", 1)
add_para(
    doc,
    "建议建设一套部署在公司主机上的内网 Web 系统，名称暂定为“预制食品研发 BOM、采购询价与成本核价系统”。研发、采购、财务通过浏览器访问同一套数据，按权限完成各自录入和审核动作。",
)
add_table(
    doc,
    ["部门", "主要职责", "系统内关键动作"],
    [
        ["研发部", "负责配方、工艺、实际用量、阶段得率和感官评价", "新建产品、录入打样单、记录工序重量、提交采购询价、发起成本核算"],
        ["采购部", "负责供应商、货源、MOQ、梯度价格、交期和资质资料", "维护供应商报价、上传资质、选择当前核价价格、维护报价有效期"],
        ["财务部", "负责成本项目、费用分摊、利润率、税点和出厂价", "选择核价口径、生成标准成本、锁定价格版本、输出报价建议"],
        ["管理层", "负责产品立项、量产决策和价格确认", "查看成本构成、毛利测算和版本差异，确认是否进入试生产"],
    ],
    [Cm(2.0), Cm(4.1), Cm(9.8)],
)

add_heading(doc, "3. 产品分类与工艺模板", 1)
add_para(doc, "系统不应把所有预制食品强行放进一张固定表，而应采用“产品大类 + 工艺模板 + 配方版本 + 成本模型”的方式。")
add_table(
    doc,
    ["产品大类", "典型产品", "重点记录", "重点成本"],
    [
        ["冷冻即热菜", "料理包、熟制菜肴、复热即食产品", "炒制/蒸煮时间、中心温度、冷却时间、速冻后重量、复热效果", "主料、酱汁、熟化损耗、速冻成本、冷库成本、包材"],
        ["腊制品", "腊肉、腊肠、酱卤腌腊类", "腌制周期、盐度、水分损耗、烘干/风干参数、添加剂使用记录", "原肉、腌料、风干/烘干损耗、人工、设备占用"],
        ["生制调理品", "调理鸡排、腌制肉片、调理鱼片、裹粉半成品", "腌制比例、滚揉时间、吸浆率、裹粉率、冻后滴水损失", "主料、腌料、裹粉、冷冻损耗、包材"],
    ],
    [Cm(2.2), Cm(3.0), Cm(6.2), Cm(4.5)],
    header_fill=LIGHT_BLUE,
    font_size=8.8,
)

add_heading(doc, "4. 全流程总览", 1)
add_para(doc, "标准流程建议如下，先用于研发打样到报价，稳定后再扩展到生产批次、库存和追溯。")
add_numbered(doc, [
    "研发新建产品项目，选择产品大类和工艺模板。",
    "研发录入小试配方、备料量、实际投入量、工序后重量和感官评价。",
    "系统自动计算各工序损耗、成品得率和研发样品成本。",
    "研发提交待询价清单，采购在同一产品版本下补充供应商报价。",
    "采购维护 MOQ、梯度价格、含税/不含税、交期、报价有效期和资质文件。",
    "财务选择核价基准，录入人工、水电燃气、速冻/冷库、折旧、品控、管理费用等模板。",
    "系统生成单袋、单箱、单公斤成本，以及建议出厂价和毛利测算。",
    "研发根据成本构成优化配方或工艺，生成新版本并保留历史版本。",
    "中试或试生产后录入真实得率和真实费用，财务复算并锁定标准成本。",
    "管理层根据成本、价格、交期和风险决定是否进入量产。",
])

add_heading(doc, "5. 示例产品：冷冻即热黑椒鸡柳料理包", 1)
add_para(doc, "以下示例用于说明系统实际操作方式。示例数据仅用于流程讨论，正式上线时需替换为企业真实工艺、价格和财务口径。")

add_heading(doc, "5.1 研发创建产品", 2)
add_table(
    doc,
    ["字段", "示例填写"],
    [
        ["产品名称", "黑椒鸡柳料理包"],
        ["产品类型", "冷冻即热菜"],
        ["产品规格", "250g/袋，20袋/箱"],
        ["保存方式", "-18℃冷冻"],
        ["目标保质期", "12个月"],
        ["研发阶段", "小试"],
        ["工艺模板", "原料解冻 -> 修整切条 -> 腌制 -> 炒制/熟化 -> 冷却 -> 分装 -> 速冻 -> 装箱"],
    ],
    [Cm(3.2), Cm(12.6)],
)

add_heading(doc, "5.2 研发录入小试配方", 2)
add_table(
    doc,
    ["物料", "备料量", "实际投入", "单位", "类型"],
    [
        ["鸡腿肉", "8.00", "7.60", "kg", "主料"],
        ["洋葱丝", "1.20", "1.00", "kg", "辅料"],
        ["青椒丝", "0.80", "0.70", "kg", "辅料"],
        ["黑椒酱", "1.50", "1.30", "kg", "调味料"],
        ["食用油", "0.50", "0.35", "kg", "辅料"],
        ["淀粉", "0.30", "0.25", "kg", "辅料"],
        ["250g蒸煮袋", "45", "40", "个", "包材"],
    ],
    [Cm(4.0), Cm(2.4), Cm(2.4), Cm(1.8), Cm(3.0)],
    font_size=9,
)
add_callout(
    doc,
    "录入原则",
    "研发端必须区分“备料量”和“实际投入量”。备料多、泡发多、解冻多但未实际使用的部分不应进入单位成本，避免后续靠人工反推。"
)

add_heading(doc, "5.3 研发记录工序重量", 2)
add_table(
    doc,
    ["工序", "重量/数量", "系统计算或说明"],
    [
        ["冻鸡腿肉备料", "8.00kg", "作为原始备料基准"],
        ["解冻后", "7.70kg", "解冻损耗 0.30kg"],
        ["修整切条后", "6.80kg", "修整损耗 0.90kg"],
        ["腌制后", "7.20kg", "腌制吸收 0.40kg"],
        ["炒制后", "5.95kg", "熟化损耗 1.25kg"],
        ["加入酱汁和配菜后", "10.20kg", "进入分装前总量"],
        ["最终分装", "40袋 x 250g", "成品净含量 10.00kg"],
    ],
    [Cm(4.2), Cm(3.2), Cm(8.4)],
)
add_para(doc, "研发还应记录关键参数：炒制温度、炒制时间、中心温度、冷却时间、速冻时间、复热方式和感官评价。")

add_heading(doc, "5.4 系统生成研发成本初算", 2)
add_table(
    doc,
    ["成本项", "示例金额"],
    [
        ["鸡腿肉", "136.80元"],
        ["洋葱", "3.00元"],
        ["青椒", "4.20元"],
        ["黑椒酱", "18.20元"],
        ["食用油", "3.15元"],
        ["淀粉", "1.25元"],
        ["蒸煮袋", "12.00元"],
        ["合计", "178.60元"],
    ],
    [Cm(6.5), Cm(4.0)],
)
add_para(doc, "若本次做出 40 袋，则研发样品阶段单袋物料成本 = 178.60 / 40 = 4.47 元/袋。该结果只是研发估算成本，不能直接作为正式报价。")

add_heading(doc, "5.5 采购补充供应商报价", 2)
add_table(
    doc,
    ["物料", "供应商", "规格", "MOQ", "梯度价", "交期", "备注"],
    [
        ["鸡腿肉", "A供应商", "去骨鸡腿肉", "100kg", "100kg:18.5/kg；500kg:17.8/kg；1吨:17.2/kg", "2天", "需检疫证明"],
        ["鸡腿肉", "B供应商", "去骨鸡腿肉", "300kg", "100kg:18.0/kg；500kg:17.5/kg；1吨:16.9/kg", "5天", "价格低但交期长"],
        ["蒸煮袋", "C包装厂", "250g蒸煮袋", "10000个", "0.28元/个", "7天", "需确认耐温参数"],
        ["蒸煮袋", "D包装厂", "250g蒸煮袋", "50000个", "0.22元/个", "12天", "适合量产"],
    ],
    [Cm(2.0), Cm(2.2), Cm(2.5), Cm(1.6), Cm(4.2), Cm(1.4), Cm(2.5)],
    font_size=8.2,
)

add_heading(doc, "5.6 财务完整核价", 2)
add_para(doc, "财务选择核价基准，例如按 1000 袋进行成本测算。系统按研发得率反推原料需求，并使用采购确认的当前可用价格。")
add_table(
    doc,
    ["费用项目", "示例口径"],
    [
        ["单袋物料成本", "4.35元/袋"],
        ["单袋包材成本", "0.43元/袋"],
        ["直接人工", "0.45元/袋"],
        ["水电燃气", "0.18元/袋"],
        ["速冻成本", "0.22元/袋"],
        ["冷库存储", "0.08元/袋"],
        ["设备折旧", "0.12元/袋"],
        ["品控检测", "0.06元/袋"],
        ["生产管理费用", "0.20元/袋"],
        ["损耗摊销", "0.15元/袋"],
        ["单袋总成本", "6.24元/袋"],
    ],
    [Cm(5.0), Cm(5.8)],
)
add_para(doc, "若目标利润率为 25%，税率按财务配置，则系统可生成建议出厂价。示例：建议出厂价约 7.80 元/袋，约 156.00 元/箱。正式报价需由财务按含税/不含税、进项抵扣和客户报价政策确认。")

add_heading(doc, "5.7 研发优化、中试确认与标准成本锁定", 2)
add_bullets(doc, [
    "如果系统显示鸡腿肉、黑椒酱或包材占比过高，研发可建立 V1.1 配方进行优化，不覆盖 V1.0。",
    "中试阶段必须录入更接近量产的真实得率，例如实际投料、熟化后重量、最终袋数、包材损耗和人工工时。",
    "财务根据中试数据复算标准成本，形成 V1.2 标准成本版本。",
    "标准成本锁定后，研发修改配方、采购修改价格、财务修改费用模板都应生成新版本。",
])

add_heading(doc, "6. 关键计算口径", 1)
add_table(
    doc,
    ["计算项", "公式或说明"],
    [
        ["解冻损耗率", "(冻品备料重量 - 解冻后重量) / 冻品备料重量"],
        ["修整损耗率", "(解冻后重量 - 修整后重量) / 解冻后重量"],
        ["腌制吸收率", "(腌制后重量 - 修整后重量) / 修整后重量"],
        ["熟化损耗率", "(腌制后重量 - 熟化后重量) / 腌制后重量"],
        ["成品得率", "成品净含量总重量 / 主要投入物料重量，可按产品类型配置口径"],
        ["单位物料成本", "Σ(单位成品耗用量 x 当前采购单价)"],
        ["单位总成本", "物料 + 包材 + 人工 + 水电燃气 + 速冻/冷库 + 折旧 + 品控 + 管理 + 损耗摊销"],
        ["建议出厂价", "单位总成本 x (1 + 利润率) x (1 + 税率)，税务口径由财务配置"],
    ],
    [Cm(4.0), Cm(11.8)],
    header_fill=LIGHT_BLUE,
    font_size=8.9,
)

add_heading(doc, "7. 第一版建议功能范围", 1)
add_table(
    doc,
    ["优先级", "功能", "说明"],
    [
        ["P0", "电子研发打样单", "替代纸质记录，解决字迹、及时性和版本问题"],
        ["P0", "产品分类与工艺模板", "先支持冷冻即热菜、腊制品、生制调理品三类"],
        ["P0", "工序重量与损耗计算", "支持解冻、修整、腌制、熟化、分装等节点"],
        ["P0", "原料/包材/供应商库", "统一编码、规格、单位、供应商资质和报价"],
        ["P0", "采购询价与梯度价", "记录 MOQ、梯度价格、交期、报价有效期"],
        ["P0", "财务成本核算", "输出单袋、单箱、单公斤成本和建议出厂价"],
        ["P1", "配方版本锁定", "保留小试、中试、试生产、量产版本"],
        ["P1", "Excel 导入导出", "便于现阶段与各部门现有表格过渡"],
        ["P2", "食品安全与标签字段", "后续扩展添加剂、标签、营养成分、保质期和批次追溯"],
    ],
    [Cm(1.8), Cm(4.0), Cm(10.0)],
    font_size=8.8,
)

add_heading(doc, "8. 三部门征询意见清单", 1)
add_para(doc, "建议组织一次三部门评审会，按以下问题逐项征询意见。")
add_heading(doc, "研发部需要确认", 2)
add_bullets(doc, [
    "现有产品是否都能归入冷冻即热菜、腊制品、生制调理品三类；是否还需要新增类别。",
    "每类产品必须记录哪些工序重量和关键工艺参数。",
    "小试、中试、试生产、量产之间，哪些字段必须保留，哪些字段可以选填。",
    "泡发、解冻、焯水、熟化、风干、滚揉、裹粉等特殊场景的计算口径。",
])
add_heading(doc, "采购部需要确认", 2)
add_bullets(doc, [
    "原料、辅料、包材的编码和规格字段是否足够。",
    "供应商报价需要记录哪些字段：含税/不含税、税率、运费、冷链费、MOQ、梯度价、报价有效期、交期。",
    "哪些供应商资质和检验资料必须上传并与物料绑定。",
    "是否需要记录替代供应商和历史价格曲线。",
])
add_heading(doc, "财务部需要确认", 2)
add_bullets(doc, [
    "成本项目分类是否符合公司核算习惯：物料、包材、人工、能源、折旧、品控、管理费用等。",
    "费用分摊按袋、按公斤、按批次、按工时还是按设备时间。",
    "含税价、不含税价、利润率、税点和出厂价的计算规则。",
    "哪些成本版本可以锁定，谁有权限解锁或生成新版本。",
])

add_heading(doc, "9. 实施路径建议", 1)
add_table(
    doc,
    ["阶段", "周期建议", "交付内容", "验收标准"],
    [
        ["第一阶段：MVP", "4-6周", "电子打样单、工艺模板、损耗计算、采购报价、基础核价", "完成 1-2 个真实产品从研发到核价闭环"],
        ["第二阶段：采购协同", "4-6周", "供应商资质、梯度价、报价有效期、比价表、替代供应商", "采购能直接基于研发配方生成询价和比价"],
        ["第三阶段：财务核价", "3-5周", "成本费用模板、标准成本锁定、报价方案、毛利测算", "财务能输出可复算的单袋/单箱/单公斤成本"],
        ["第四阶段：扩展", "持续迭代", "批次追溯、标签、营养成分、电子秤、ERP/进销存对接", "系统从研发核价延伸到生产和追溯"],
    ],
    [Cm(3.0), Cm(2.0), Cm(6.0), Cm(4.8)],
    header_fill=LIGHT_BLUE,
    font_size=8.5,
)

add_heading(doc, "10. 注意事项", 1)
add_bullets(doc, [
    "第一阶段目标是打通研发样品到财务报价，不建议一开始替代完整 ERP 或生产管理系统。",
    "食品安全、添加剂、标签、营养成分和保质期字段应预留，但具体限量和标签规则必须以最新国家标准、监管要求和企业内控为准。",
    "研发打样数据要尽量现场录入；若先写纸再补录，仍会存在及时性和歧义问题。",
    "所有价格、配方和成本都应有版本，不应覆盖历史数据。",
])

add_heading(doc, "附录：参考资料方向", 1)
add_para(doc, "本方案借鉴了成熟 ERP/MRP 系统中 BOM、RFQ 和成本卷算的常见设计，并结合预制食品研发打样、采购询价和财务核价场景进行了裁剪。")
add_bullets(doc, [
    "Odoo Manufacturing 文档：Bill of Materials / Manufacturing costs / Requests for Quotation。",
    "Microsoft Dynamics 365 Supply Chain Management 文档：BOM calculations and cost management。",
    "食品合规字段建议结合现行 GB 7718、GB 28050、GB 2760、GB 14881 及相关预制菜监管口径，由质量或法规负责人最终确认。",
])

doc.save(OUT)
print(OUT)
