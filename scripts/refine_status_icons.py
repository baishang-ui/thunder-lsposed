"""Regularize small status artwork with symmetric curves and constant stroke widths.
Apple guide silhouettes establish proportions; Android-only states are project artwork.
"""
from pathlib import Path
import html
import math
import xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent.parent / 'app/src/main/assets/icons/ios-reference'

def write(kind, shapes):
    parts=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">',
           '<!-- Project vector geometry; proportions and sources: docs/status-icons-sources.md. -->']
    for path, width in shapes:
        style = f'fill="none" stroke="currentColor" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"' if width else 'fill="currentColor" fill-rule="evenodd"'
        parts.append(f'<path d="{html.escape(path)}" {style}/>')
    (OUT / f'{kind.lower()}.svg').write_text('\n'.join(parts + ['</svg>', '']))

def refine():
    # Concentric circular sectors with eased corners; all three share the same origin.
    def sector(outer, inner):
        def p(r,a): return (12+r*math.cos(a),19+r*math.sin(a))
        def pair(p): return f'{p[0]:.5f},{p[1]:.5f}'
        def arc(r,a,b):
            k=4/3*math.tan((b-a)/4);x,y=p(r,a);u,v=p(r,b)
            return f'C{pair((x-k*r*math.sin(a),y+k*r*math.cos(a)))} {pair((u+k*r*math.sin(b),v-k*r*math.cos(b)))} {pair((u,v))}'
        a,b=math.radians(-135),math.radians(-45);corner=.34;d=corner/outer
        parts=[f'M{pair(p(outer,a+d))}',arc(outer,a+d,b-d)]
        # Short cubic corners soften radial ends without turning bands into capsules.
        parts += [f'C{pair(p(outer,b))} {pair(p(outer,b))} {pair(p(outer-corner,b))}']
        if inner:
            di=corner/inner
            parts += [f'L{pair(p(inner+corner,b))}',f'C{pair(p(inner,b))} {pair(p(inner,b))} {pair(p(inner,b-di))}',arc(inner,b-di,a+di),
                      f'C{pair(p(inner,a))} {pair(p(inner,a))} {pair(p(inner+corner,a))}']
        else:
            parts += ['L12.24,18.76 C12.10,18.90 11.90,18.90 11.76,18.76']
        parts += [f'L{pair(p(outer-corner,a))}',f'C{pair(p(outer,a))} {pair(p(outer,a))} {pair(p(outer,a+d))} Z']
        return ' '.join(parts)
    write('wifi', [
        (sector(3.35,0),0), (sector(8.65,5.8),0), (sector(14.15,11.3),0)])
    # Body and terminal fit the exact 27 x 13 rendering viewport.
    write('battery', [
        ('M3.3,5.5 L21.2,5.5 C23.55,5.5 24.5,6.45 24.5,8.8 L24.5,15.2 C24.5,17.55 23.55,18.5 21.2,18.5 L3.3,18.5 C.95,18.5 0,17.55 0,15.2 L0,8.8 C0,6.45 .95,5.5 3.3,5.5 Z',0),
        ('M25.5,9.5 C27.5,9.9 27.5,14.1 25.5,14.5 Z',0)])
    write('bluetooth', [('M7,7 L17,16 L12,21 L12,3 L17,8 L7,17',1.8)])
    # Open the bell along the mute diagonal so the slash remains readable at 1x.
    write('silent', [
        ('M10.4,5.8 L10.4,5.2 C10.4,3.2 13.6,3.2 13.6,5.2 L13.6,5.8 C16.3,6.5 17.4,8.4 17.4,11.3 L17.4,13.1 Z M7.1,8.8 C6.8,9.5 6.6,10.4 6.6,11.4 C6.6,14.1 6.2,15.1 5.1,16.1 C4.7,16.5 4.9,17.2 5.6,17.2 L15.5,17.2 Z M10,19 L14,19 C13.7,21.1 10.3,21.1 10,19 Z',0),
        ('M4,3.7 L20.2,20',1.8)])
    write('vibrate', [
        ('M9.5,5 L14.5,5 C15.33,5 16,5.67 16,6.5 L16,17.5 C16,18.33 15.33,19 14.5,19 L9.5,19 C8.67,19 8,18.33 8,17.5 L8,6.5 C8,5.67 8.67,5 9.5,5 Z',1.6),
        ('M5,7 L3.5,9.5 L5,12 L3.5,14.5 L5,17 M19,7 L20.5,9.5 L19,12 L20.5,14.5 L19,17',1.6)])
    write('nfc', [('M5,19 L5,5 L9,5 L15,14 L15,5 M19,5 L19,19 L15,19 L9,10 L9,19',1.8)])
    write('reverse_charge', [('M5,8 L19,8 M15,4 L19,8 L15,12 M19,16 L5,16 M9,12 L5,16 L9,20',1.7)])
    write('performance', [('M13.5,2.5 L5.6,13 L11,13 L10.5,21.5 L18.4,10.5 L13,10.5 Z',0)])
    write('alarm', [
        ('M12,5 C7.58,5 4,8.58 4,13 C4,17.42 7.58,21 12,21 C16.42,21 20,17.42 20,13 C20,8.58 16.42,5 12,5 Z M11.2,7.5 L12.8,7.5 L12.8,13.8 L7.5,13.8 L7.5,12.2 L11.2,12.2 Z',0),
        ('M3.4,6.2 C2.2,4.1 5,1.8 7,3.5 M17,3.5 C19,1.8 21.8,4.1 20.6,6.2 M6,20 L4.8,21.2 M18,20 L19.2,21.2',1.6)])
    write('moon', [('M13.5,3 C7.3,2 2.9,7.7 4.1,13.5 C5.2,19 11.7,21.8 16.6,18.9 C18.4,17.85 19.7,16.1 20.2,14.1 C16.2,16 11.7,13.8 11.2,9.6 C10.9,7.1 11.8,4.9 13.5,3 Z',0)])
    write('hotspot', [
        ('M8.5,15 L7.5,15 C4.46,15 2,12.54 2,9.5 C2,6.46 4.46,4 7.5,4 L13,4 C16.04,4 18.5,6.46 18.5,9.5 C18.5,12.54 16.04,15 13,15 L12,15',1.85),
        ('M15.5,9 L16.5,9 C19.54,9 22,11.46 22,14.5 C22,17.54 19.54,20 16.5,20 L11,20 C7.96,20 5.5,17.54 5.5,14.5 C5.5,11.46 7.96,9 11,9 L12,9',1.85)])
    write('rotation', [
        ('M20.3,10 C19.4,6.2 16,3.5 12,3.5 C7.3,3.5 3.5,7.3 3.5,12 C3.5,16.7 7.3,20.5 12,20.5 C14.9,20.5 17.4,19.1 18.9,16.9',1.9),
        ('M16.9,9.8 L23.1,9.8 L20,13.8 Z',0),
        ('M9.7,10.4 L14.3,10.4 C15,10.4 15.5,10.9 15.5,11.6 L15.5,15.7 C15.5,16.4 15,16.9 14.3,16.9 L9.7,16.9 C9,16.9 8.5,16.4 8.5,15.7 L8.5,11.6 C8.5,10.9 9,10.4 9.7,10.4 Z',0),
        ('M9.8,10.4 L9.8,8.7 C9.8,5.8 14.2,5.8 14.2,8.7 L14.2,10.4',1.5)])
    # Geometric lettering avoids the noisy contours of the former bitmap trace.
    write('vpn', [
        ('M3.1,6.2 L20.9,6.2 C22,6.2 22.8,7 22.8,8.1 L22.8,15.9 C22.8,17 22,17.8 20.9,17.8 L3.1,17.8 C2,17.8 1.2,17 1.2,15.9 L1.2,8.1 C1.2,7 2,6.2 3.1,6.2 Z',1.45),
        ('M4.2,9.2 L6.1,14.8 L8,9.2 M10.1,14.8 L10.1,9.2 L12.1,9.2 C14.8,9.2 14.8,12.4 12.1,12.4 L10.1,12.4 M16,14.8 L16,9.2 L19.8,14.8 L19.8,9.2',1.2)])
    write('headphones', [
        ('M4,13 L4,11 C4,6.58 7.58,3 12,3 C16.42,3 20,6.58 20,11 L20,13',2.2),
        ('M4,12 C2.5,12 2.5,13.2 3,15 L4,18 C4.4,19.3 5.2,20 6.4,19.6 L7.6,19.2 C8.4,18.9 8.7,18.1 8.4,17.2 L7.2,13 C6.9,12 6.1,11.4 5.2,11.7 Z M20,12 C21.5,12 21.5,13.2 21,15 L20,18 C19.6,19.3 18.8,20 17.6,19.6 L16.4,19.2 C15.6,18.9 15.3,18.1 15.6,17.2 L16.8,13 C17.1,12 17.9,11.4 18.8,11.7 Z',0)])
    write('airplane', [('M2,10.5 L9,10.5 L5.5,3 L8,3 L14,10.5 L20,10.5 C22.7,10.5 22.7,13.5 20,13.5 L14,13.5 L8,21 L5.5,21 L9,13.5 L2,13.5 L1,17 L0,17 L0,7 L1,7 Z',0)])
    write('cast', [('M3,8 L3,6 C3,5.45 3.45,5 4,5 L20,5 C20.55,5 21,5.45 21,6 L21,16 C21,16.55 20.55,17 20,17 L13,17 M3,12 C6.9,12 10,15.1 10,19 M3,15.5 C4.9,15.5 6.5,17.1 6.5,19',1.8),('M3,18 C3.55,18 4,18.45 4,19 L3,19 Z',0)])
    write('ethernet', [('M6,4 L18,4 C18.55,4 19,4.45 19,5 L19,14 C19,14.55 18.55,15 18,15 L16,15 L16,18 C16,18.55 15.55,19 15,19 L9,19 C8.45,19 8,18.55 8,18 L8,15 L6,15 C5.45,15 5,14.55 5,14 L5,5 C5,4.45 5.45,4 6,4 Z M8,7 L8,10 M12,7 L12,10 M16,7 L16,10',1.8)])
    write('work', [('M8,7 L8,5.3 C8,4.6 8.6,4 9.3,4 L14.7,4 C15.4,4 16,4.6 16,5.3 L16,7 M4.4,7.5 L19.6,7.5 C20.4,7.5 21,8.1 21,8.9 L21,17.6 C21,18.4 20.4,19 19.6,19 L4.4,19 C3.6,19 3,18.4 3,17.6 L3,8.9 C3,8.1 3.6,7.5 4.4,7.5 Z M3,12 L9.8,12 M14.2,12 L21,12 M10,11 L14,11 L14,14 L10,14 Z',1.8)])
    write('data_saver', [('M12,3 C7.03,3 3,7.03 3,12 C3,16.97 7.03,21 12,21 C16.97,21 21,16.97 21,12 C21,7.03 16.97,3 12,3 Z M8,12 L16,12 M12,8 L12,16',1.7)])
    write('tty', [('M4,4 L4,9 C4,11 5,12 7,12 M4,4 L6,4 M7,12 L7,14 M10,5 L20,5 M10,9 L20,9 M4,18 L5,18 M8,18 L9,18 M12,18 L13,18 M16,18 L17,18 M20,18 L21,18',1.7)])
    write('camera', [('M4.5,7 L7,7 L8.5,5 C8.75,4.65 9.1,4.5 9.5,4.5 L14.5,4.5 C14.9,4.5 15.25,4.65 15.5,5 L17,7 L19.5,7 C20.35,7 21,7.65 21,8.5 L21,17.5 C21,18.35 20.35,19 19.5,19 L4.5,19 C3.65,19 3,18.35 3,17.5 L3,8.5 C3,7.65 3.65,7 4.5,7 Z M15.5,12.5 C15.5,10.57 13.93,9 12,9 C10.07,9 8.5,10.57 8.5,12.5 C8.5,14.43 10.07,16 12,16 C13.93,16 15.5,14.43 15.5,12.5 Z',1.8)])
    write('microphone', [('M9,5 C9,1.8 15,1.8 15,5 L15,11 C15,14.2 9,14.2 9,11 Z M6,10 L6,11 C6,19 18,19 18,11 L18,10 M12,17 L12,21 M9,21 L15,21',1.7)])
    write('recording', [('M12,3 C7.03,3 3,7.03 3,12 C3,16.97 7.03,21 12,21 C16.97,21 21,16.97 21,12 C21,7.03 16.97,3 12,3 Z',1.7),('M12,7 C9.24,7 7,9.24 7,12 C7,14.76 9.24,17 12,17 C14.76,17 17,14.76 17,12 C17,9.24 14.76,7 12,7 Z',0)])
    write('stealth', [('M3,12 C8,4 16,4 21,12 C16,20 8,20 3,12 M10,10 C14,6 18,14 14,14 M3,3 L21,21',1.7)])
    write('activity', [('M8,5 L8,19 M4.5,15.5 L8,19 L11.5,15.5',1.65),('M16,19 L16,5 M12.5,8.5 L16,5 L19.5,8.5',1.65)])
    write('activity_in', [('M8,5 L8,19 M4.5,15.5 L8,19 L11.5,15.5',1.65)])
    write('activity_out', [('M16,19 L16,5 M12.5,8.5 L16,5 L19.5,8.5',1.65)])
    cellular=' '.join(n.attrib['d'] for n in ET.parse(OUT/'cellular.svg').getroot() if n.tag.endswith('path'))
    write('no_service', [(cellular,0),('M2,3 L22,21',1.7)])
    write('location', [('M4,10.4 L20,3.5 C20.8,3.2 21.3,3.8 21,4.6 L14.1,20.6 C13.7,21.5 12.5,21.3 12.4,20.4 L11.7,12.8 L4.2,12.1 C3.2,12 3.1,10.8 4,10.4 Z',0)])

if __name__ == '__main__':
    refine()
