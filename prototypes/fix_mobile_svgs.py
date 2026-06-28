#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Write UTF-8 mobile SVG prototypes (no external deps)."""

from pathlib import Path

ROOT = Path(__file__).resolve().parent
PAGES = ROOT / "pages"

FONT = "PingFang SC, Microsoft YaHei, sans-serif"
PHONE = """<defs><filter id="shadow" x="-25%" y="-25%" width="150%" height="150%"><feDropShadow dx="0" dy="8" stdDeviation="10" flood-color="#334155" flood-opacity="0.16"/></filter></defs>
<rect width="100%" height="100%" fill="#F6F8FB"/>
<g filter="url(#shadow)"><rect x="52" y="92" width="416" height="820" rx="42" fill="#101828"/><rect x="68" y="110" width="384" height="784" rx="32" fill="#F8FAFC" stroke="#E2E8F0" stroke-width="2"/></g>
<rect x="202" y="124" width="116" height="14" rx="7" fill="#0F172A"/>"""

SVG_FILES = {
    "01_手机端_我的待办.svg": f"""<svg xmlns="http://www.w3.org/2000/svg" width="520" height="980" viewBox="0 0 520 980">{PHONE}
<text x="260" y="50" text-anchor="middle" font-family="{FONT}" font-size="32" font-weight="800" fill="#111827">我的待办</text>
<text x="92" y="176" font-family="{FONT}" font-size="27" font-weight="800" fill="#111827">我的待办</text>
<text x="92" y="208" font-family="{FONT}" font-size="17" fill="#64748B">黄丽金 / 研发人员</text>
<rect x="92" y="232" width="126" height="36" rx="18" fill="#EAF2FF"/><text x="155" y="257" text-anchor="middle" font-family="{FONT}" font-size="17" font-weight="700" fill="#246BFE">今日待办 5</text>
<rect x="232" y="232" width="90" height="36" rx="18" fill="#FFF4E5"/><text x="277" y="257" text-anchor="middle" font-family="{FONT}" font-size="17" font-weight="700" fill="#F59E0B">逾期 1</text>
<text x="92" y="296" font-family="{FONT}" font-size="20" font-weight="800" fill="#111827">继续处理</text>
<rect x="92" y="308" width="336" height="118" rx="18" fill="#246BFE"/>
<text x="114" y="342" font-family="{FONT}" font-size="20" font-weight="800" fill="#FFFFFF">500g香卤大肠头 A0</text>
<text x="114" y="370" font-family="{FONT}" font-size="16" fill="#DBEAFE">打样中实验单 · 草稿已保存</text>
<rect x="268" y="378" width="136" height="40" rx="20" fill="#FFFFFF"/><text x="336" y="404" text-anchor="middle" font-family="{FONT}" font-size="16" font-weight="800" fill="#246BFE">继续填写</text>
<text x="92" y="456" font-family="{FONT}" font-size="13" font-weight="700" fill="#64748B">待接受任务</text>
<rect x="92" y="468" width="336" height="98" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
<text x="114" y="502" font-family="{FONT}" font-size="20" font-weight="800" fill="#111827">黑椒鸡柳料理包</text>
<text x="114" y="530" font-family="{FONT}" font-size="16" fill="#64748B">A0 · 待接受 · 截止 06-22</text>
<rect x="268" y="510" width="72" height="36" rx="18" fill="#EAF2FF"/><text x="304" y="535" text-anchor="middle" font-family="{FONT}" font-size="16" font-weight="700" fill="#246BFE">接受</text>
<rect x="68" y="822" width="384" height="72" fill="#FFFFFF" stroke="#E5EAF2"/>
<text x="122" y="878" text-anchor="middle" font-family="{FONT}" font-size="15" font-weight="700" fill="#246BFE">待办</text>
<text x="210" y="878" text-anchor="middle" font-family="{FONT}" font-size="15" fill="#64748B">样品</text>
<text x="298" y="878" text-anchor="middle" font-family="{FONT}" font-size="15" fill="#64748B">我的</text>
</svg>""",
    "02_手机端_任务详情.svg": f"""<svg xmlns="http://www.w3.org/2000/svg" width="520" height="980" viewBox="0 0 520 980">{PHONE}
<text x="260" y="50" text-anchor="middle" font-family="{FONT}" font-size="32" font-weight="800" fill="#111827">任务详情</text>
<text x="92" y="162" font-family="{FONT}" font-size="22" font-weight="800" fill="#111827">任务详情</text>
<text x="92" y="200" font-family="{FONT}" font-size="27" font-weight="800" fill="#111827">黑椒鸡柳料理包</text>
<rect x="92" y="218" width="92" height="32" rx="16" fill="#FFF7ED"/><text x="138" y="240" text-anchor="middle" font-family="{FONT}" font-size="15" font-weight="700" fill="#F59E0B">待接受</text>
<rect x="92" y="268" width="336" height="280" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
<text x="114" y="300" font-family="{FONT}" font-size="16" font-weight="700" fill="#64748B">样品信息</text>
<text x="114" y="332" font-family="{FONT}" font-size="15" fill="#64748B">客户</text><text x="300" y="332" text-anchor="end" font-family="{FONT}" font-size="16" font-weight="600" fill="#111827">连锁餐饮客户B</text>
<text x="114" y="364" font-family="{FONT}" font-size="15" fill="#64748B">样品版本</text><text x="300" y="364" text-anchor="end" font-family="{FONT}" font-size="16" font-weight="600" fill="#111827">A0</text>
<text x="114" y="396" font-family="{FONT}" font-size="15" fill="#64748B">规格</text><text x="300" y="396" text-anchor="end" font-family="{FONT}" font-size="16" font-weight="600" fill="#111827">300g/袋，24袋/箱</text>
<text x="114" y="428" font-family="{FONT}" font-size="15" fill="#64748B">需求说明</text>
<text x="114" y="456" font-family="{FONT}" font-size="15" fill="#111827">复热后保持嫩度，黑椒风味明显。</text>
<text x="114" y="518" font-family="{FONT}" font-size="15" fill="#64748B">研发总监备注</text>
<text x="114" y="546" font-family="{FONT}" font-size="15" fill="#111827">优先保水与复热稳定性。</text>
<rect x="92" y="820" width="336" height="50" rx="14" fill="#246BFE"/><text x="260" y="853" text-anchor="middle" font-family="{FONT}" font-size="17" font-weight="800" fill="#FFFFFF">接受任务</text>
<rect x="92" y="878" width="336" height="50" rx="14" fill="#FFFFFF" stroke="#246BFE" stroke-width="2"/><text x="260" y="911" text-anchor="middle" font-family="{FONT}" font-size="17" font-weight="800" fill="#246BFE">开始填写实验单</text>
</svg>""",
    "09_手机端_寄样反馈.svg": f"""<svg xmlns="http://www.w3.org/2000/svg" width="520" height="980" viewBox="0 0 520 980">{PHONE}
<text x="260" y="50" text-anchor="middle" font-family="{FONT}" font-size="32" font-weight="800" fill="#111827">寄样反馈</text>
<text x="92" y="162" font-family="{FONT}" font-size="22" font-weight="800" fill="#111827">寄样反馈</text>
<text x="92" y="200" font-family="{FONT}" font-size="27" font-weight="800" fill="#111827">调理鸡排 A2</text>
<rect x="92" y="290" width="336" height="120" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
<text x="114" y="322" font-family="{FONT}" font-size="15" fill="#64748B">寄样数量</text><text x="300" y="322" text-anchor="end" font-family="{FONT}" font-size="16" font-weight="600" fill="#111827">6 袋</text>
<text x="114" y="354" font-family="{FONT}" font-size="15" fill="#64748B">快递单号</text><text x="300" y="354" text-anchor="end" font-family="{FONT}" font-size="16" font-weight="600" fill="#111827">SF1234567890</text>
<rect x="92" y="604" width="104" height="46" rx="14" fill="#FFFFFF" stroke="#16A34A" stroke-width="2"/><text x="144" y="634" text-anchor="middle" font-family="{FONT}" font-size="16" font-weight="800" fill="#16A34A">客户通过</text>
<rect x="208" y="604" width="104" height="46" rx="14" fill="#FFFFFF" stroke="#F59E0B" stroke-width="2"/><text x="260" y="634" text-anchor="middle" font-family="{FONT}" font-size="16" font-weight="800" fill="#F59E0B">继续打样</text>
<rect x="324" y="604" width="104" height="46" rx="14" fill="#FFFFFF" stroke="#EF4444" stroke-width="2"/><text x="376" y="634" text-anchor="middle" font-family="{FONT}" font-size="16" font-weight="800" fill="#EF4444">停止打样</text>
<rect x="92" y="820" width="336" height="50" rx="14" fill="#246BFE"/><text x="260" y="853" text-anchor="middle" font-family="{FONT}" font-size="17" font-weight="800" fill="#FFFFFF">客户通过，生成核价文件</text>
</svg>""",
    "10_手机端_样品搜索.svg": f"""<svg xmlns="http://www.w3.org/2000/svg" width="520" height="980" viewBox="0 0 520 980">{PHONE}
<text x="260" y="50" text-anchor="middle" font-family="{FONT}" font-size="32" font-weight="800" fill="#111827">样品</text>
<text x="92" y="176" font-family="{FONT}" font-size="27" font-weight="800" fill="#111827">样品</text>
<rect x="92" y="196" width="336" height="44" rx="22" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
<text x="128" y="225" font-family="{FONT}" font-size="16" fill="#94A3B8">搜索产品名称 / 样品编号</text>
<rect x="92" y="256" width="56" height="32" rx="16" fill="#246BFE"/><text x="120" y="278" text-anchor="middle" font-family="{FONT}" font-size="14" font-weight="700" fill="#FFFFFF">全部</text>
<rect x="92" y="308" width="336" height="88" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
<text x="114" y="342" font-family="{FONT}" font-size="20" font-weight="800" fill="#111827">500g香卤大肠头</text>
<text x="114" y="370" font-family="{FONT}" font-size="16" fill="#64748B">A0 · 打样中 · 客户A</text>
<rect x="68" y="822" width="384" height="72" fill="#FFFFFF" stroke="#E5EAF2"/>
<text x="210" y="878" text-anchor="middle" font-family="{FONT}" font-size="15" font-weight="700" fill="#246BFE">样品</text>
</svg>""",
    "11_手机端_我的.svg": f"""<svg xmlns="http://www.w3.org/2000/svg" width="520" height="980" viewBox="0 0 520 980">{PHONE}
<text x="260" y="50" text-anchor="middle" font-family="{FONT}" font-size="32" font-weight="800" fill="#111827">我的</text>
<rect x="92" y="200" width="336" height="100" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
<text x="184" y="238" font-family="{FONT}" font-size="20" font-weight="800" fill="#111827">黄丽金</text>
<text x="184" y="264" font-family="{FONT}" font-size="16" fill="#64748B">研发人员 · 飞书已绑定</text>
<rect x="92" y="320" width="336" height="56" rx="14" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/><text x="114" y="355" font-family="{FONT}" font-size="17" font-weight="700" fill="#111827">我的草稿实验单</text>
<rect x="92" y="386" width="336" height="56" rx="14" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/><text x="114" y="421" font-family="{FONT}" font-size="17" font-weight="700" fill="#111827">我的已完成记录</text>
<rect x="68" y="822" width="384" height="72" fill="#FFFFFF" stroke="#E5EAF2"/>
<text x="298" y="878" text-anchor="middle" font-family="{FONT}" font-size="15" font-weight="700" fill="#246BFE">我的</text>
</svg>""",
    "12_手机端_实验单历史.svg": f"""<svg xmlns="http://www.w3.org/2000/svg" width="520" height="980" viewBox="0 0 520 980">{PHONE}
<text x="260" y="50" text-anchor="middle" font-family="{FONT}" font-size="32" font-weight="800" fill="#111827">实验单历史</text>
<text x="92" y="200" font-family="{FONT}" font-size="27" font-weight="800" fill="#111827">500g香卤大肠头</text>
<line x1="118" y1="260" x2="118" y="700" stroke="#E2E8F0" stroke-width="3"/>
<circle cx="118" cy="290" r="10" fill="#246BFE"/>
<rect x="140" y="268" width="288" height="80" rx="18" fill="#FFFFFF" stroke="#246BFE" stroke-width="2"/>
<text x="162" y="300" font-family="{FONT}" font-size="20" font-weight="800" fill="#111827">A2 · 打样中</text>
<circle cx="118" cy="410" r="10" fill="#16A34A"/>
<rect x="140" y="388" width="288" height="80" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
<text x="162" y="420" font-family="{FONT}" font-size="20" font-weight="800" fill="#111827">A1 · 已锁定</text>
<circle cx="118" cy="530" r="10" fill="#64748B"/>
<rect x="140" y="508" width="288" height="80" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
<text x="162" y="540" font-family="{FONT}" font-size="20" font-weight="800" fill="#111827">A0 · 已锁定</text>
</svg>""",
}

if __name__ == "__main__":
    for name, content in SVG_FILES.items():
        path = PAGES / name
        path.write_text(content.strip() + "\n", encoding="utf-8")
        print("wrote", path)
