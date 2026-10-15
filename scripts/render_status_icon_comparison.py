"""Create a side-by-side official-reference / regularized-vector comparison sheet."""
from pathlib import Path
from io import BytesIO
import base64
from PIL import Image
from render_status_icons import paths, symbol, stacked_signal

ROOT=Path(__file__).resolve().parent.parent
rows=[('Wi-Fi','wifi',20,symbol('WIFI')),('单卡信号','cellular',22,symbol('CELLULAR')),('双卡堆叠','dual',22,stacked_signal(4,4)),
      ('旋转锁定','rotation',19,symbol('ROTATION')),('个人热点','hotspot',23,symbol('HOTSPOT'))]
svg=['<svg xmlns="http://www.w3.org/2000/svg" width="840" height="710" viewBox="0 0 840 710">',
     '<rect width="840" height="710" rx="24" fill="#f7f8fa"/>',
     '<g font-family="-apple-system,PingFang SC,Arial,sans-serif" fill="#17191d">',
     '<text x="36" y="48" font-size="24" font-weight="600">苹果官方原图 / 应用矢量轮廓</text>',
     '<text x="36" y="77" font-size="13" fill="#737983">同色、同宽对照 · 应用预览与 SystemUI 共用右侧路径</text>',
     '<text x="300" y="116" font-size="15" text-anchor="middle">Apple 官方参考</text>',
     '<text x="630" y="116" font-size="15" text-anchor="middle">应用中的平滑矢量</text>']
for i,(label,name,width,content) in enumerate(rows):
 y=135+i*103
 svg += [f'<rect x="28" y="{y}" width="784" height="94" rx="15" fill="#fff"/>',f'<text x="49" y="{y+51}" font-size="15">{label}</text>']
 im=Image.open(ROOT/f'.local/apple-ref-{name}.png').convert('RGBA');im=im.crop(im.getbbox())
 # Preserve the original alpha, tint both sides identically to compare shape.
 im.paste((23,25,29),mask=Image.new('L',im.size,255));alpha=Image.open(ROOT/f'.local/apple-ref-{name}.png').convert('RGBA');alpha=alpha.crop(alpha.getbbox()).getchannel('A');im.putalpha(alpha)
 data=BytesIO();im.save(data,format='PNG');encoded=base64.b64encode(data.getvalue()).decode()
 w=width*3.2;h=w*im.height/im.width
 svg.append(f'<image href="data:image/png;base64,{encoded}" x="{300-w/2}" y="{y+47-h/2}" width="{w}" height="{h}"/>')
 svg.append(f'<g transform="translate({630-12*3.2} {y+47-12*3.2}) scale(3.2)" color="#17191d">{content}</g>')
svg+=['<text x="36" y="686" font-size="11" fill="#737983">原图来源：Apple《iPhone 使用手册》状态图标页；矢量按参考比例用规则曲线重绘，信号柱统一柱宽与基线。</text>','</g></svg>']
out=ROOT/'docs/status-icons-official-comparison.svg';out.write_text('\n'.join(svg)+'\n');print(out)
