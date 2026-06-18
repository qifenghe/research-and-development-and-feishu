from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


DIR = Path("prototypes/pages")
OUT = DIR / "00_页面总览.png"

FILES = [
    DIR / "01_手机端_飞书首页.png",
    DIR / "02_手机端_样品需求录入.png",
    DIR / "03_手机端_打样实验单.png",
    DIR / "04_手机端_测试确认.png",
    DIR / "05_PC端-首页看板.png",
    DIR / "06_PC端-研发任务分发.png",
    DIR / "07_PC端-核价文件.png",
    DIR / "08_PC端-产品档案.png",
]

W, H = 2200, 1650
BG = (246, 248, 251)
INK = (17, 24, 39)
MUTED = (100, 116, 139)
BORDER = (216, 224, 234)
WHITE = (255, 255, 255)


def font(size, bold=False):
    candidates = [
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/Library/Fonts/Arial Unicode.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size=size, index=0)
    return ImageFont.load_default()


def rounded(draw, xy, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


canvas = Image.new("RGB", (W, H), BG)
draw = ImageDraw.Draw(canvas)
title_font = font(42, True)
sub_font = font(22)
label_font = font(24, True)
draw.text((W // 2, 42), "研发样品管理系统 - 页面总览", anchor="mm", fill=INK, font=title_font)
draw.text((W // 2, 84), "每张页面已单独导出 PNG，可用于评审、飞书沟通或放入方案文档。", anchor="mm", fill=MUTED, font=sub_font)

positions = [
    (60, 130),
    (610, 130),
    (1160, 130),
    (1710, 130),
    (60, 880),
    (610, 880),
    (1160, 880),
    (1710, 880),
]

for path, (x, y) in zip(FILES, positions):
    label = path.stem
    rounded(draw, (x, y, x + 500, y + 690), 18, WHITE, BORDER, 2)
    draw.text((x + 24, y + 42), label, fill=INK, font=label_font)
    img = Image.open(path).convert("RGB")
    max_w, max_h = 430, 590
    img.thumbnail((max_w, max_h), Image.Resampling.LANCZOS)
    ix = x + (500 - img.width) // 2
    iy = y + 78 + (590 - img.height) // 2
    canvas.paste(img, (ix, iy))

canvas.save(OUT)
print(OUT)
