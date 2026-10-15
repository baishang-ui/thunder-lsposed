"""Regularize small status artwork with symmetric curves and constant stroke widths.
Apple guide silhouettes establish proportions; Android-only states are project artwork.
"""
from pathlib import Path
import html

OUT = Path(__file__).resolve().parent.parent / 'app/src/main/assets/icons/ios-reference'

def write(kind, shapes):
    parts=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">',
           '<!-- Project vector geometry; proportions and sources: docs/status-icons-sources.md. -->']
    for path, width in shapes:
        style = f'fill="none" stroke="currentColor" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"' if width else 'fill="currentColor" fill-rule="evenodd"'
        parts.append(f'<path d="{html.escape(path)}" {style}/>')
    (OUT / f'{kind.lower()}.svg').write_text('\n'.join(parts + ['</svg>', '']))

def refine():
    # Three filled, concentric Wi-Fi segments, in low-to-high strength order.
    write('wifi', [
        ('M9.5,16.4 C10.88,15.02 13.12,15.02 14.5,16.4 L12.45,18.45 C12.2,18.7 11.8,18.7 11.55,18.45 Z',0),
        ('M5.25,12.15 C8.98,8.42 15.02,8.42 18.75,12.15 L16.8,14.1 C14.15,11.45 9.85,11.45 7.2,14.1 Z',0),
        ('M2,8.9 C7.52,3.38 16.48,3.38 22,8.9 L20.05,10.85 C15.61,6.41 8.39,6.41 3.95,10.85 Z',0)])
    # Body and terminal fit the exact 27 x 13 rendering viewport.
    write('battery', [
        ('M3.3,5.5 L21.2,5.5 C23.55,5.5 24.5,6.45 24.5,8.8 L24.5,15.2 C24.5,17.55 23.55,18.5 21.2,18.5 L3.3,18.5 C.95,18.5 0,17.55 0,15.2 L0,8.8 C0,6.45 .95,5.5 3.3,5.5 Z',0),
        ('M25.5,9.5 C27.5,9.9 27.5,14.1 25.5,14.5 Z',0)])
    write('bluetooth', [('M7,7 L17,16 L12,21 L12,3 L17,8 L7,17',1.85)])
    bell='M8.2,16 L7,16 C8.25,14.4 8.25,13.2 8.25,10.2 C8.25,7.7 9.65,6.25 12,6.25 C14.35,6.25 15.75,7.7 15.75,10.2 C15.75,13.2 15.75,14.4 17,16 L8.2,16 M10.5,19 C11.3,19.8 12.7,19.8 13.5,19 M12,4.5 L12,5.25'
    write('silent', [(bell,1.65),('M4,4 L20,20',2.0)])
    write('vibrate', [
        ('M9.5,5 L14.5,5 C15.33,5 16,5.67 16,6.5 L16,17.5 C16,18.33 15.33,19 14.5,19 L9.5,19 C8.67,19 8,18.33 8,17.5 L8,6.5 C8,5.67 8.67,5 9.5,5 Z',1.6),
        ('M5,7 L3.5,9.5 L5,12 L3.5,14.5 L5,17 M19,7 L20.5,9.5 L19,12 L20.5,14.5 L19,17',1.6)])
    write('nfc', [('M5,19 L5,5 L9,5 L15,14 L15,5 M19,5 L19,19 L15,19 L9,10 L9,19',1.8)])
    write('performance', [('M13.5,2.5 L5.6,13 L11,13 L10.5,21.5 L18.4,10.5 L13,10.5 Z',0)])
    write('alarm', [
        ('M12,5 C7.58,5 4,8.58 4,13 C4,17.42 7.58,21 12,21 C16.42,21 20,17.42 20,13 C20,8.58 16.42,5 12,5 Z M11.2,7.5 L12.8,7.5 L12.8,13.8 L7.5,13.8 L7.5,12.2 L11.2,12.2 Z',0),
        ('M3.4,6.2 C2.2,4.1 5,1.8 7,3.5 M17,3.5 C19,1.8 21.8,4.1 20.6,6.2 M6,20 L4.8,21.2 M18,20 L19.2,21.2',1.6)])
    write('moon', [('M13.5,3 C7.3,2 2.9,7.7 4.1,13.5 C5.2,19 11.7,21.8 16.6,18.9 C18.4,17.85 19.7,16.1 20.2,14.1 C16.2,16 11.7,13.8 11.2,9.6 C10.9,7.1 11.8,4.9 13.5,3 Z',0)])
    write('hotspot', [
        ('M9,8 L7,8 C4.24,8 2,10.24 2,13 C2,15.76 4.24,18 7,18 L10,18 C12.76,18 15,15.76 15,13 M15,16 L17,16 C19.76,16 22,13.76 22,11 C22,8.24 19.76,6 17,6 L14,6 C11.24,6 9,8.24 9,11 M8,12 L16,12',1.8)])
    write('rotation', [
        ('M19.4,10 C18.5,5.8 14.1,3.3 10,4.4 C5.9,5.5 3.4,9.7 4.5,13.8 C5.6,17.9 9.8,20.4 13.9,19.3 C15.4,18.9 16.7,18.1 17.7,17',1.8),
        ('M16.3,10.2 L22,10.2 L19.15,14 Z',0),
        ('M8.6,11 L15.4,11 L15.4,16 L8.6,16 Z',0),
        ('M10,11 L10,8.8 C10,6.4 14,6.4 14,8.8 L14,11',1.5)])
    write('headphones', [
        ('M4,13 L4,11 C4,6.58 7.58,3 12,3 C16.42,3 20,6.58 20,11 L20,13',2.2),
        ('M4,12 C2.5,12 2.5,13.2 3,15 L4,18 C4.4,19.3 5.2,20 6.4,19.6 L7.6,19.2 C8.4,18.9 8.7,18.1 8.4,17.2 L7.2,13 C6.9,12 6.1,11.4 5.2,11.7 Z M20,12 C21.5,12 21.5,13.2 21,15 L20,18 C19.6,19.3 18.8,20 17.6,19.6 L16.4,19.2 C15.6,18.9 15.3,18.1 15.6,17.2 L16.8,13 C17.1,12 17.9,11.4 18.8,11.7 Z',0)])
    write('airplane', [('M2,10.5 L9,10.5 L5.5,3 L8,3 L14,10.5 L20,10.5 C22.7,10.5 22.7,13.5 20,13.5 L14,13.5 L8,21 L5.5,21 L9,13.5 L2,13.5 L1,17 L0,17 L0,7 L1,7 Z',0)])
    write('location', [('M4,10.4 L20,3.5 C20.8,3.2 21.3,3.8 21,4.6 L14.1,20.6 C13.7,21.5 12.5,21.3 12.4,20.4 L11.7,12.8 L4.2,12.1 C3.2,12 3.1,10.8 4,10.4 Z',0)])

if __name__ == '__main__':
    refine()
