"""Render the packaged PUI vectors for visual review (not a device screenshot)."""
from pathlib import Path
from io import BytesIO
import html
import xml.etree.ElementTree as ET
import cairosvg
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
                result += f'<path d="{html.escape(node.get(NS+"pathData"), quote=True)}" fill="{color}" fill-opacity="{fa*float(node.get(NS+"fillAlpha",1))}" fill-rule="evenodd" stroke="{color}" stroke-opacity="{sa*float(node.get(NS+"strokeAlpha",1))}" stroke-width="{node.get(NS+"strokeWidth",0)}" stroke-linecap="round" stroke-linejoin="round"/>'
        return result
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {tree.get(NS+"viewportWidth")} {tree.get(NS+"viewportHeight")}">{nodes(tree)}</svg>'
    return Image.open(BytesIO(cairosvg.svg2png(bytestring=svg.encode()))).convert('RGBA')


def main():
    im = Image.new('RGB', (1080, 760), '#f4f5f7')
    draw = ImageDraw.Draw(im)
    font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 19)
    small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 15)
    title = ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc', 29)
    draw.text((36, 22), 'PUI ColorOS 17 · 原始资源', fill='#16181c', font=title)
    draw.text((36, 66), 'C17 原始路径 · 图标 +12% / 间距约 +2dp / 电池 28 × 17dp', fill='#636975', font=small)
    icons = [('airplane_mode','飞行模式'), ('alarm','闹钟'), ('data_bluetooth_connected_stop','蓝牙'),
             ('dnd','勿扰'), ('ringer_silent','静音'), ('flavor_one_ringer_vibrate','振动'),
             ('headset','耳机'), ('location','定位'), ('nfc','NFC'), ('vpn_ic','VPN'),
             ('data_saver','流量节省'), ('high_performance','高性能')]
    for i, (name, label) in enumerate(icons):
        x, y = 36 + (i % 7)*146, 112 + (i//7)*115
        icon = vector('stat_sys_'+name, '#15171b', 70, 60)
        im.paste(icon, (x+25, y), icon)
        draw.text((x+60, y+70), label, anchor='mt', fill='#30343b', font=small)
    draw.rounded_rectangle((24, 362, 1056, 608), 22, fill='#17191d')
    draw.text((42, 380), 'C17 堆叠信号：主卡信号柱 + 副卡圆点', fill='white', font=font)
    for level in range(5):
        x = 65 + level*130
        a = vector(f'stat_signal_lte_signal_stacked_primary_{level}', '#ffffff', 90, 76)
        b = vector(f'stat_signal_lte_signal_stacked_secondary_{4-level}', '#ffffff', 90, 76)
        im.paste(a, (x, 425), a)
        im.paste(b, (x, 425), b)
        draw.text((x+45, 510), f'主 {level} / 副 {4-level}', anchor='mt', fill='#cbd0d9', font=small)
        wifi = vector(f'stat_signal_wifi_signal_{level}_os17', '#ffffff', 70, 50)
        im.paste(wifi, (x+10, 545), wifi)
    draw.text((754, 422), '横向电池 · 数字居中', fill='white', font=font)
    battery = vector('stat_battery_horizontal_bg', '#ffffff', 1100, 600).resize((84, 51), Image.Resampling.LANCZOS)
    percent_font = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', 30)
    for index, text in enumerate(['7', '75', '100']):
        x, y = 752 + index * 100, 479
        im.paste(battery, (x, y), battery)
        left, top, right, bottom = draw.textbbox((0, 0), text, font=percent_font)
        draw.text((x + 84 * (1979 / 2200) / 2 - (left + right) / 2,
                   y + 51 / 2 - (top + bottom) / 2), text, fill='#17191d', font=percent_font)
    draw.text((753, 554), '28 × 17dp / 系统电量', fill='#cbd0d9', font=small)
    draw.text((36, 640), '浅色状态与堆叠资源使用同一轮廓，随系统 tint 自动切换。', fill='#454b56', font=font)
    draw.text((36, 683), 'Wi-Fi、电池及状态图标均取自 C17 包；5G 文字、角标和箭头隐藏。', fill='#636975', font=small)
    im.save(ROOT / 'docs/status-icons-pui.png')


if __name__ == '__main__':
    main()
