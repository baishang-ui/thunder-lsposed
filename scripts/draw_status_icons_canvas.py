"""Raster inspection with Skia Canvas, using the production SVGs and generated metrics.

Run with .local/work/tools-venv/bin/python (requires skia-python).
This is a geometry proof, not a SystemUI screenshot. Fonts come from canvas-design.
"""
from pathlib import Path
import re
import math
import argparse
import xml.etree.ElementTree as ET
import skia
from render_status_icons import ROOT, ICONS, paths, metrics, bounds, cell_width

FONTS = Path.home()/'.codex/skills/canvas-design/canvas-fonts'
REGULAR = skia.Typeface.MakeFromFile(str(FONTS/'InstrumentSans-Regular.ttf'))
BOLD = skia.Typeface.MakeFromFile(str(FONTS/'InstrumentSans-Bold.ttf'))
MONO = skia.Typeface.MakeFromFile(str(FONTS/'DMMono-Regular.ttf'))
INK, MUTED, PAPER = '#182027', '#66717a', '#f2f3f1'


def color(hex_color):
    return skia.ColorSetARGB(255, *bytes.fromhex(hex_color.lstrip('#')))


def paint(ink, alpha=1, stroke=0):
    p=skia.Paint(AntiAlias=True, Color=color(ink))
    p.setAlphaf(alpha)
    if stroke:
        p.setStyle(skia.Paint.kStroke_Style); p.setStrokeWidth(stroke)
        p.setStrokeCap(skia.Paint.kRound_Cap); p.setStrokeJoin(skia.Paint.kRound_Join)
    return p


def path(data):
    tokens=re.findall(r'[MLCHVZ]|[-+]?(?:\d*\.)?\d+(?:[eE][-+]?\d+)?',data)
    p=skia.Path(); p.setFillType(skia.PathFillType.kEvenOdd)
    i=0;command='M';x=y=0
    while i<len(tokens):
        if tokens[i] in 'MLCHVZ':
            command=tokens[i];i+=1
            if command=='Z':p.close();continue
        n={'M':2,'L':2,'C':6,'H':1,'V':1}[command]
        v=list(map(float,tokens[i:i+n]));i+=n
        if command=='M':p.moveTo(*v);x,y=v;command='L'
        elif command=='L':p.lineTo(*v);x,y=v
        elif command=='C':p.cubicTo(*v);x,y=v[-2:]
        elif command=='H':x=v[0];p.lineTo(x,y)
        elif command=='V':y=v[0];p.lineTo(x,y)
    return p


def text(c,label,x,y,size=12,ink=INK,face=REGULAR,center=False):
    f=skia.Font(face,size)
    if center:x-=f.measureText(label)/2
    c.drawString(label,x,y,f,paint(ink))


def centered(c,label,x,y,size,ink,face=BOLD):
    f=skia.Font(face,size);b=skia.Rect()
    f.measureText(label,bounds=b)
    c.drawString(label,x-(b.left()+b.right())/2,y-(b.top()+b.bottom())/2,f,paint(ink))


def shapes(c,kind,level,ink,maximum=4):
    for i,(d,w,*_) in enumerate(paths(kind)):
        a=1
        if kind=='WIFI':a=1 if (level>0 if i==0 else level*3/maximum>=i+.5) else .24
        if kind=='CELLULAR':a=1 if level*4/maximum>=4-i else .24
        if kind=='NO_SERVICE' and w==0:a=.24
        if kind=='ACTIVITY':a=1 if level&(1<<i) else .2
        c.drawPath(path(d),paint(ink,a,w))


def glyph(c,kind,x,y,density=1,level=4,ink=INK,stacked=False,flags=0,label='',charging=False,fill=None):
    width=cell_width(kind,stacked,flags,label)
    c.save();c.translate(x,y);c.scale(density,density)
    if kind=='LABEL':centered(c,label,width/2,10,10,ink)
    elif kind=='BATTERY':
        c.translate(1.5,-2)
        for i,(d,*_) in enumerate(paths(kind)):c.drawPath(path(d),paint((fill or ink) if i==0 else ink,1 if i==0 else .5))
        # Design-sheet battery contrast and numeral/bolt placement.
        rgb=bytes.fromhex((fill or ink).lstrip('#'))
        linear=[v/255/12.92 if v/255<=.04045 else ((v/255+.055)/1.055)**2.4 for v in rgb]
        contrast='#000000' if sum(v*w for v,w in zip(linear,[.2126,.7152,.0722]))>.45 else '#ffffff'
        size=(8.2 if level==100 else 9.6) if charging else (9.5 if level==100 else 10.4)
        centered(c,str(level),10.3 if charging else 12.25,12,size,contrast)
        if charging:
            c.save();c.translate(18.6,7.1);c.scale(.24,.45);shapes(c,'PERFORMANCE',4,contrast);c.restore()
    else:
        _,scale,cx,cy=metrics(kind,stacked)
        c.translate(width/2-cx*scale,10-cy*scale);c.scale(scale,scale)
        if stacked:
            for row,value in [(1,3),(2,2)]:
                for i,(d,*_) in enumerate(paths('stacked_'+str(row))):c.drawPath(path(d),paint(ink,1 if value>=i+1 else .24))
        else:shapes(c,kind,level,ink)
        x1,_,x2,_=bounds(kind)
        if flags&1:centered(c,'!',x2-.5,18,8,ink)
        if flags&2:centered(c,'R',x1+2.5,6,8,ink)
        if flags&4:c.drawPath(path(f'M{x1},3 L{x2},21'),paint(ink,stroke=1.5))
        if flags&8:c.drawCircle(x2,20,1.2,paint(ink))
    c.restore()
    return width*density


def strip(c,x,y,density,dark=False,stacked=False):
    bg,fg=('#182027','#ffffff') if dark else ('#ffffff',INK)
    c.drawRoundRect(skia.Rect.MakeXYWH(x,y,390*density,44*density),10*density,10*density,paint(bg))
    c.save();c.translate(x,y);c.scale(density,density)
    text(c,'9:41',20,28,17,fg,BOLD)
    items=[('CELLULAR',3),('WIFI',4),('BATTERY',75)]
    widths=[cell_width(k,stacked and k=='CELLULAR') for k,_ in items]
    offset=390-20-sum(widths)
    for (k,v),w in zip(items,widths):
        glyph(c,k,offset,12,level=v,ink=fg,stacked=stacked and k=='CELLULAR');offset+=w
    c.restore()


def verify_rasters():
    # Catch clipping in the actual Skia raster, including curve/stroke extents.
    # The layout contract is a 20 dp cell; antialiased edge pixels are permitted.
    cases=[(k,4,0,'',False) for k in ICONS]
    cases += [(k,3,f,'',False) for k in ('WIFI','CELLULAR','BLUETOOTH') for f in (1,2,4,8)]
    cases += [('BATTERY',v,0,'',charging) for v in (0,9,20,75,100) for charging in (False,True)]
    cases += [('LABEL',4,0,label,False) for label in ('5G+','HD¹','6E')]
    cases += [('CELLULAR',v,0,'',False) for v in range(5)]
    cases += [('WIFI',v,0,'',False) for v in range(5)]
    for density in (1,2,2.75,3):
        for k,v,f,label,charge in cases:
            w=cell_width(k,False,f,label);pad=8
            s=skia.Surface(math.ceil(w*density)+2*pad,math.ceil(20*density)+2*pad)
            c=s.getCanvas();c.clear(skia.ColorTRANSPARENT)
            glyph(c,k,pad,pad,density,v,flags=f,label=label,charging=charge)
            a=s.makeImageSnapshot().toarray()[:,:,3]
            assert not a[:pad,:].any() and not a[pad+math.ceil(20*density):,:].any(),f'vertical clipping: {k}/{f} at {density}'
            assert not a[:,:pad].any() and not a[:,pad+math.ceil(w*density):].any(),f'horizontal clipping: {k}/{f} at {density}'
    # Validate the geometry generator against an independent cubic implementation.
    for k in ICONS:
        actual=[]
        for d,w,*_ in paths(k):
            b=path(d).computeTightBounds()
            actual.append((b.left()-w/2,b.top()-w/2,b.right()+w/2,b.bottom()+w/2))
        expected=(min(v[0] for v in actual),min(v[1] for v in actual),max(v[2] for v in actual),max(v[3] for v in actual))
        assert all(abs(a-b)<.001 for a,b in zip(expected,bounds(k))),f'curve extrema: {k}'
    print('Skia raster bounds verified at 1x, 2x, 2.75x and 3x; device layout and typeface remain unverified.')


def corner_study(before):
    """Same source coordinates at 6x, for judging the contours rather than layout."""
    kinds=['CELLULAR','BATTERY','VIBRATE','VPN','CAST','WORK','CAMERA','ETHERNET']
    surface=skia.Surface(1200,1140);c=surface.getCanvas();c.clear(color(PAPER))
    text(c,'CONTINUOUS CORNERS',40,47,24,face=BOLD)
    text(c,'Previous / revised   ·   same coordinates at 6x   ·   production paths',40,77,12,MUTED)
    current=ROOT/'app/src/main/assets/icons/ios-reference'
    for i,kind in enumerate(kinds):
        x=40+i%2*580;y=105+i//2*240
        c.drawRoundRect(skia.Rect.MakeXYWH(x,y,560,222),16,16,paint('#ffffff'))
        text(c,kind.lower(),x+22,y+27,12,MUTED,MONO)
        for j,source in enumerate([before,current]):
            c.save();c.translate(x+142+j*270-12*6,y+111-12*6);c.scale(6,6)
            nodes=ET.parse(source/(kind.lower()+'.svg')).getroot()
            for n in nodes:
                if n.tag.endswith('path'):
                    c.drawPath(path(n.attrib['d']),paint(INK,stroke=float(n.attrib.get('stroke-width',0))))
            if kind=='BATTERY':centered(c,'75',12.25,12,10.4,'#ffffff')
            c.restore()
            text(c,'previous' if j==0 else 'revised',x+142+j*270,y+203,11,MUTED,MONO,True)
    text(c,'Two cubic segments per corner · smooth curvature into each straight edge',40,1103,12,MUTED)
    output=ROOT/'docs/status-icons-rounded-corners.png';surface.makeImageSnapshot().save(str(output),skia.kPNG)
    print(output)


def main():
    verify_rasters()
    surface=skia.Surface(1280,1470);c=surface.getCanvas();c.clear(color(PAPER))
    text(c,'THUNDER  /  OPTICAL QUIET',48,44,12,face=MONO)
    text(c,'Status, at its actual scale.',48,89,34,face=BOLD)
    text(c,'01  /  1 px per dp',48,132,11,MUTED,MONO);strip(c,48,149,1)
    text(c,'02  /  2.75 px per dp',48,225,11,MUTED,MONO);strip(c,48,245,2.75)
    text(c,'03  /  3 px per dp   ·   dual SIM',48,414,11,MUTED,MONO);strip(c,48,435,3,True,True)
    text(c,'04  /  Contour studies     3x + 1x',48,625,11,MUTED,MONO)
    kinds=list(ICONS)
    for i,k in enumerate(kinds):
        x=48+i%10*119;y=650+i//10*142
        c.drawRoundRect(skia.Rect.MakeXYWH(x,y,111,129),8,8,paint('#ffffff'))
        w=cell_width(k);glyph(c,k,x+(111-w*3)/2,y+15,3,level=75 if k=='BATTERY' else 4)
        glyph(c,k,x+(111-w)/2,y+86,level=75 if k=='BATTERY' else 4)
        text(c,k.replace('_',' ').lower(),x+55.5,y+119,8.5,MUTED,MONO,True)
    text(c,'05  /  Strength · warnings · charge',48,1123,11,MUTED,MONO)
    samples=[('WIFI',0,0),('WIFI',2,0),('WIFI',4,0),('CELLULAR',1,0),('CELLULAR',4,0),('WIFI',3,1),('CELLULAR',3,2),('BLUETOOTH',4,8)]
    for i,(k,v,f) in enumerate(samples):glyph(c,k,54+i*145,1152,3,level=v,flags=f)
    for i,(v,charge,fill) in enumerate([(20,False,'#df3b3b'),(75,False,INK),(62,True,'#34c759'),(100,True,'#34c759')]):
        x=48+i*297
        c.drawRoundRect(skia.Rect.MakeXYWH(x,1252,279,105),9,9,paint('#ffffff'))
        glyph(c,'BATTERY',x+94.5,1272,3,level=v,charging=charge,fill=fill,ink=fill)
    text(c,'Skia Canvas · production paths + optical metrics · design proof, not a device capture.',48,1412,11,MUTED)
    text(c,'Typeface: Instrument Sans / DM Mono. Android uses its system typeface.',48,1433,10,MUTED)
    output=ROOT/'docs/status-icons-canvas.png';surface.makeImageSnapshot().save(str(output),skia.kPNG)
    print(output)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before',type=Path,help='Optional SVG directory for a corner comparison')
    args=parser.parse_args()
    main()
    if args.before:corner_study(args.before)
