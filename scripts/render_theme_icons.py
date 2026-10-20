"""Render packaged C16 artwork adapted to C17 (offline review, not a screenshot).

Requires Pillow and either CairoSVG or Node.js with sharp. Set STATUS_ICONS_NODE and
NODE_PATH when using a bundled Node runtime. STATUS_ICONS_FONT can select a CJK font.
"""
from pathlib import Path
from io import BytesIO
import html
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'app/src/main/res/drawable'
NS = '{http://schemas.android.com/apk/res/android}'


def vector(name, color, width=100, height=80):
    tree = ET.parse(RES / ('thunder_ios_' + name + '.xml')).getroot()
    if tree.tag == 'inset':
        tree = tree.find('vector')

    def nodes(parent):
        result = ''
        for node in parent:
            if node.tag == 'group':
                transform = f'translate({node.get(NS+"translateX", "0")} {node.get(NS+"translateY", "0")}) scale({node.get(NS+"scaleX", "1")} {node.get(NS+"scaleY", "1")})'
                result += f'<g transform="{transform}">{nodes(node)}</g>'
            elif node.tag == 'path':
                fill = node.get(NS+'fillColor', '#00000000')
                stroke = node.get(NS+'strokeColor', '#00000000')
                fa = int(fill[1:3], 16)/255 if len(fill) == 9 else 1
                sa = int(stroke[1:3], 16)/255 if len(stroke) == 9 else 1
                rule = 'evenodd' if node.get(NS+'fillType') == 'evenOdd' else 'nonzero'
                result += f'<path d="{html.escape(node.get(NS+"pathData"), quote=True)}" fill="{color}" fill-opacity="{fa*float(node.get(NS+"fillAlpha",1))}" fill-rule="{rule}" stroke="{color}" stroke-opacity="{sa*float(node.get(NS+"strokeAlpha",1))}" stroke-width="{node.get(NS+"strokeWidth",0)}" stroke-linecap="{node.get(NS+"strokeLineCap","butt")}" stroke-linejoin="{node.get(NS+"strokeLineJoin","miter")}"/>'
        return result

    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" preserveAspectRatio="xMidYMid meet" viewBox="0 0 {tree.get(NS+"viewportWidth")} {tree.get(NS+"viewportHeight")}">{nodes(tree)}</svg>'
    try:
        import cairosvg
    except ImportError:
        node = os.environ.get('STATUS_ICONS_NODE') or shutil.which('node')
        if not node:
            raise RuntimeError('Install CairoSVG or provide STATUS_ICONS_NODE and Node.js sharp')
        script = "const sharp=require('sharp');let b=[];process.stdin.on('data',c=>b.push(c));process.stdin.on('end',()=>sharp(Buffer.concat(b)).png().toBuffer().then(p=>process.stdout.write(p)).catch(e=>{console.error(e);process.exit(1)}));"
        png = subprocess.check_output([node, '-e', script], input=svg.encode())
    else:
        png = cairosvg.svg2png(bytestring=svg.encode())
    return Image.open(BytesIO(png)).convert('RGBA')


def font(size):
    paths = [os.environ.get('STATUS_ICONS_FONT'), '/System/Library/Fonts/STHeiti Medium.ttc',
             'C:/Windows/Fonts/msyh.ttc', '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc']
    path = next((p for p in paths if p and Path(p).exists()), None)
    return ImageFont.truetype(path, size) if path else ImageFont.load_default(size=size)


def main():
    im = Image.new('RGB', (1080, 1000), '#f4f5f7')
    draw = ImageDraw.Draw(im)
    body, small, title = font(20), font(16), font(30)
    draw.text((36, 24), 'C16 图标样式 → ColorOS 17 原生堆叠', fill='#16181c', font=title)
    draw.text((36, 72), '第一包轮廓与透明度 · 主副卡独立 0–4 格 · 透明角标保留', fill='#636975', font=small)
    icons = [('airplane_mode','飞行模式'), ('alarm','闹钟'), ('data_bluetooth_connected_stop','蓝牙'),
             ('dnd','勿扰'), ('ringer_silent','静音'), ('flavor_one_ringer_vibrate','振动'),
             ('headset','耳机'), ('location','定位'), ('nfc','NFC'), ('vpn_ic','VPN')]
    for i, (name, label) in enumerate(icons):
        x, y = 36 + (i % 5)*204, 120 + (i//5)*108
        icon = vector('stat_sys_'+name, '#15171b', 74, 58)
        im.paste(icon, (x+53, y), icon)
        draw.text((x+90, y+66), label, anchor='mt', fill='#30343b', font=small)
    draw.rounded_rectangle((24, 354, 1056, 786), 22, fill='#17191d')
    draw.text((44, 374), '上排圆角信号柱 + 下排圆角短条：25 种双卡组合', fill='white', font=body)
    for secondary in range(5):
        for primary in range(5):
            x, y = 92+primary*193, 422+secondary*68
            a = vector(f'stat_signal_lte_signal_stacked_primary_{primary}', '#ffffff', 68, 54)
            b = vector(f'stat_signal_lte_signal_stacked_secondary_{secondary}', '#ffffff', 68, 54)
            im.paste(a, (x, y), a)
            im.paste(b, (x, y), b)
            draw.text((x+76,y+19), f'{primary} / {secondary}', fill='#cbd0d9', font=small)
    draw.text((36, 812), 'Wi-Fi 各档', fill='#30343b', font=body)
    for level in range(5):
        icon = vector(f'stat_signal_wifi_signal_{level}_os17', '#15171b', 64, 48)
        im.paste(icon, (36+level*90, 850), icon)
        draw.text((68+level*90,910), str(level), anchor='mt', fill='#636975', font=small)
    draw.text((574, 812), 'C16 横向电池 · 原图等比放大 12%', fill='#30343b', font=body)
    battery_tree=ET.parse(RES/'thunder_ios_stat_battery_horizontal_bg.xml').getroot()
    battery_width=round(float(battery_tree.get(NS+'width')[:-2])*3)
    battery_height=round(float(battery_tree.get(NS+'height')[:-2])*3)
    battery = vector('stat_battery_horizontal_bg', '#15171b', battery_width, battery_height)
    for index, text in enumerate(['7', '75', '100']):
        x, y = 584+index*140, 856
        im.paste(battery, (x, y), battery)
        digits = font(26)
        left, top, right, bottom = draw.textbbox((0, 0), text, font=digits)
        draw.text((x+battery_width*(85/93)/2-(left+right)/2, y+battery_height/2-(top+bottom)/2), text, fill='white', font=digits)
    draw.text((36, 960), '离线资源预览；电量、充电状态、双卡排列与亮暗颜色由系统控制。', fill='#636975', font=small)
    im.save(ROOT / 'docs/status-icons-preview.png')


if __name__ == '__main__':
    main()
