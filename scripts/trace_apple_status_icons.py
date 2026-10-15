"""Trace the alpha silhouettes in Apple's published status-icon reference images.
Requires Pillow, numpy and potracer; outputs editable vector studies, not a font.
Run after downloading the URLs recorded in docs/status-icons-sources.md to .local/apple-ref-*.png.
"""
from pathlib import Path
import numpy as np
from PIL import Image
import potrace

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'app/src/main/assets/icons/ios-reference'
OUT.mkdir(parents=True,exist_ok=True)
CONFIG={
 'WIFI':('wifi',20), 'CELLULAR':('cellular',22),
 'ALARM':('alarm',20), 'MOON':('moon',17), 'HOTSPOT':('hotspot',23),
 'ROTATION':('rotation',19), 'HEADPHONES':('headphones',20),
 'AIRPLANE':('airplane',22), 'LOCATION':('location',17), 'VPN':('vpn',24),
}

def bounds(curve):
 points=[curve.start_point]+[s.end_point for s in curve]
 return min(p.x for p in points),min(p.y for p in points),max(p.x for p in points),max(p.y for p in points)

def path(curve,transform):
 def xy(p):
  x,y=transform(p.x,p.y);return f'{x:.4f},{y:.4f}'
 data=['M'+xy(curve.start_point)]
 for s in curve:
  if s.is_corner:data+=['L'+xy(s.c),'L'+xy(s.end_point)]
  else:data+=['C'+xy(s.c1)+' '+xy(s.c2)+' '+xy(s.end_point)]
 return ' '.join(data)+' Z'

def trace(name,width):
 im=Image.open(ROOT/f'.local/apple-ref-{name}.png').convert('RGBA')
 alpha=np.array(im)[:,:,3];mask=alpha>=128
 yy,xx=np.where(mask);l,t,r,b=xx.min(),yy.min(),xx.max()+1,yy.max()+1
 scale=width/(r-l);height=(b-t)*scale
 def transform(x,y):return (24-width)/2+(x-l)*scale,(24-height)/2+(y-t)*scale
 # Fit curves on a 4x alpha field; tracing the low-resolution binary edge directly
 # loses the small, symmetric corners and distorts details such as lock keyholes.
 up=Image.fromarray(alpha).resize((im.width*4,im.height*4),Image.Resampling.LANCZOS)
 curves=list(potrace.Bitmap(np.where(np.array(up)>=128,0,255).astype(np.uint8)).trace(turdsize=8,opttolerance=.1))
 return curves,lambda x,y:transform(x/4,y/4)

def save(kind,curves,transform,separate=False):
 if not separate:groups=[' '.join(path(c,transform) for c in curves)]
 else:groups=[path(c,transform) for c in curves]
 data='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">\n'
 data+='<!-- Silhouette study from Apple iPhone User Guide status-icon reference. See docs/status-icons-sources.md. -->\n'
 data+='\n'.join(f'<path d="{d}" fill="currentColor" fill-rule="evenodd"/>' for d in groups)+'\n</svg>\n'
 (OUT/(kind.lower()+'.svg')).write_text(data)

for kind,(name,width) in CONFIG.items():
 curves,tr=trace(name,width)
 if kind=='WIFI':curves.sort(key=lambda c:-bounds(c)[1])
 if kind=='CELLULAR':curves.sort(key=lambda c:-bounds(c)[0])
 save(kind,curves,tr,kind in ['WIFI','CELLULAR'])
curves,tr=trace('dual',22)
for row in [1,2]:
 selected=[c for c in curves if (bounds(c)[1]<35)==(row==1)]
 selected.sort(key=lambda c:bounds(c)[0])
 save('stacked_'+str(row),selected,tr,True)
# The charging bolt is the independent gray component between the two frame halves.
im=np.array(Image.open(ROOT/'.local/apple-ref-charging.png').convert('RGBA'))
mask=(im[:,:,3]>=128)&(np.abs(im[:,:,0].astype(int)-im[:,:,1].astype(int))<15)&(im[:,:,0]<180)
curves=list(potrace.Bitmap(np.where(mask,0,255).astype(np.uint8)).trace(turdsize=3,opttolerance=.08))
bolt=next(c for c in curves if 50<bounds(c)[0]<65 and bounds(c)[2]<95)
l,t,r,b=bounds(bolt);s=18/(b-t)
save('performance',[bolt],lambda x,y:((24-(r-l)*s)/2+(x-l)*s,3+(y-t)*s))
# Trace the filled outside silhouette of the battery frame and its separate terminal.
# White pixels inside Apple's frame stay part of the solid silhouette, as requested for iOS 16 percentage mode.
im=np.array(Image.open(ROOT/'.local/apple-ref-battery.png').convert('RGBA'))
mask=im[:,:,3]>=128
curves=list(potrace.Bitmap(np.where(mask,0,255).astype(np.uint8)).trace(turdsize=2,opttolerance=.08))
body=min(curves,key=lambda c:bounds(c)[0]);terminal=max(curves,key=lambda c:bounds(c)[0])
l,t,r,b=bounds(body)
save('battery',[body,terminal],lambda x,y:((x-l)*24.5/(r-l),5.5+(y-t)*13/(b-t)),True)
# Regularize the measured straight signal columns: equal widths, shared baseline,
# and circular corners avoid baking bitmap pixel-grid asymmetry into a vector.
def round_path(x,y,w,h,r):
 k=.5522847498
 return (f'M{x+r:.4f},{y:.4f} H{x+w-r:.4f} C{x+w-r+k*r:.4f},{y:.4f} {x+w:.4f},{y+r-k*r:.4f} {x+w:.4f},{y+r:.4f} '
         f'V{y+h-r:.4f} C{x+w:.4f},{y+h-r+k*r:.4f} {x+w-r+k*r:.4f},{y+h:.4f} {x+w-r:.4f},{y+h:.4f} '
         f'H{x+r:.4f} C{x+r-k*r:.4f},{y+h:.4f} {x:.4f},{y+h-r+k*r:.4f} {x:.4f},{y+h-r:.4f} '
         f'V{y+r:.4f} C{x:.4f},{y+r-k*r:.4f} {x+r-k*r:.4f},{y:.4f} {x+r:.4f},{y:.4f} Z')
def measured_bars(kind,heights,bottom,radius,reverse=False):
 bars=[round_path(1+i*(22-4.2)/3,bottom-h,4.2,h,radius) for i,h in enumerate(heights)]
 if reverse:bars.reverse()
 # Explicit L commands keep fixture bounds extraction and Android PathParser identical.
 import re
 def explicit(d):
  tokens=re.findall(r'[A-Za-z]|[-+]?(?:\d*\.)?\d+',d);out=[];i=0;x=y=0
  while i<len(tokens):
   command=tokens[i];i+=1
   n={'M':2,'H':1,'V':1,'C':6,'Z':0}[command]
   a=[float(v) for v in tokens[i:i+n]];i+=n
   if command=='H':x=a[0];out.append(f'L{x:.4f},{y:.4f}')
   elif command=='V':y=a[0];out.append(f'L{x:.4f},{y:.4f}')
   elif command=='Z':out.append('Z')
   else:
    x,y=a[-2:];out.append(command+' '.join(f'{a[j]:.4f},{a[j+1]:.4f}' for j in range(0,len(a),2)))
  return ' '.join(out)
 (OUT/(kind+'.svg')).write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">\n<!-- Measured from Apple reference; symmetric columns and shared baseline. -->\n'+''.join(f'<path d="{explicit(d)}" fill="currentColor" fill-rule="evenodd"/>\n' for d in bars)+'</svg>\n')
measured_bars('cellular',[6.23,8.07,10.63,13.2],18.6,.65,True)
measured_bars('stacked_1',[4.77,6.23,8.07,9.9],13.65,1.05)
measured_bars('stacked_2',[4.4]*4,20.25,1.05)
# Use smooth fitting for the continuous battery corner; preserve the terminal contour.
curves=list(potrace.Bitmap(np.where(mask,0,255).astype(np.uint8)).trace(turdsize=2,alphamax=1.34,opttolerance=.08))
body=min(curves,key=lambda c:bounds(c)[0]);terminal=max(curves,key=lambda c:bounds(c)[0])
l,t,r,b=bounds(body)
save('battery',[body,terminal],lambda x,y:((x-l)*24.5/(r-l),5.5+(y-t)*13/(b-t)),True)

from refine_status_icons import refine
refine()

print('Generated',len(list(OUT.glob('*.svg'))),'vector studies in',OUT)
