"""Prepare static Android drawables. Runtime state selection and drawing stay native.

Uses JDK 17+ to export the existing resource semantics, then writes compiled-resource
inputs. Glyph outlines are checked in with their OFL license; no font is needed.
"""
from pathlib import Path
import html
import json
import os
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from render_status_icons import bounds

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'app/src/main/java/io/github/baishangui/thunder'
OUT = ROOT / 'app/src/main/res/drawable'
GLYPHS = json.loads((ROOT / 'scripts/fixtures/iconstyle/label-glyphs.json').read_text())
INVENTORY = json.loads((ROOT / 'docs/status-icons-resource-inventory.json').read_text())
ASSETS = ROOT / 'app/src/main/assets/icons/ios-reference'


def symbols():
    home = os.environ.get('JAVA_HOME')
    javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
    java = str(Path(home) / 'bin/java') if home else shutil.which('java')
    with tempfile.TemporaryDirectory(prefix='thunder-icons-') as directory:
        folder = Path(directory)
        for name in ['IosStatusIcons.java', 'IosIconAssets.java']:
            (folder / name).write_text((SOURCE / name).read_text())
        (folder / 'HookConfig.java').write_text('package io.github.baishangui.thunder; class HookConfig { boolean enabled; int statusIconStyle; }')
        (folder / 'Export.java').write_text('''package io.github.baishangui.thunder;
import java.nio.file.*;
public class Export { public static void main(String[] args) throws Exception {
 for(String name:Files.readAllLines(Path.of(args[0]))) {
  var s=IosStatusIcons.system("com.android.systemui:drawable/"+name);
  if(s==null)throw new IllegalStateException(name);
  System.out.println(String.join("\\t",name,s.kind().name(),""+s.level(),""+s.maximum(),""+s.row(),""+s.flags(),s.label()));
 }
} }''')
        subprocess.run([javac, '-d', str(folder), *map(str, folder.glob('*.java'))], check=True)
        result = subprocess.check_output([java, '-cp', str(folder), 'io.github.baishangui.thunder.Export', str(ROOT / 'scripts/fixtures/iconstyle/status-resources.txt')], text=True)
    return [line.split('\t') for line in result.splitlines()]


def path(data, stroke=0, alpha=1):
    attrs = f'android:pathData="{html.escape(data, quote=True)}" android:fillType="evenOdd"'
    if stroke:
        attrs += f' android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="{stroke}" android:strokeAlpha="{alpha}" android:strokeLineCap="round" android:strokeLineJoin="round"'
    else:
        attrs += f' android:fillColor="#FFFFFFFF" android:fillAlpha="{alpha}"'
    return f'<path {attrs}/>'


def text_paths(text, cx, cy, size=10):
    # Literal network/badge labels are vector outlines, never runtime font drawing.
    text = text.replace('☎', '')
    scale = size / 10
    width = sum(GLYPHS[c]['advance'] for c in text)
    parts = [f'<group android:translateX="{cx-width*scale/2:.5f}" android:translateY="{cy+size*.35:.5f}" android:scaleX="{scale}" android:scaleY="{scale}">']
    offset = 0
    for char in text:
        glyph = GLYPHS[char]
        if glyph['path']:
            parts += [f'<group android:translateX="{offset:.5f}">', path(glyph['path']), '</group>']
        offset += glyph['advance']
    return ''.join(parts) + '</group>'


def vector(kind, level, maximum, row, flags, label, width, height):
    # Keep the source resource's intrinsic dimensions and the native container.
    ink = (1, 3.75, 23, 20.25) if row else (0, 5, 24, 19) if kind == 'LABEL' else bounds(kind)
    if kind in ('WIFI', 'CELLULAR') or row:
        # Every state in one family keeps the same transform, including badge space.
        ink = (0, 1, 24, 23)
    left, top, right, bottom = ink
    scale = min(width / (right-left+3), height / (bottom-top+3))
    cx, cy = (left+right)/2, (top+bottom)/2
    parts = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{width:g}dp" android:height="{height:g}dp" android:viewportWidth="{width:g}" android:viewportHeight="{height:g}">',
             f'<group android:translateX="{width/2-cx*scale:.6f}" android:translateY="{height/2-cy*scale:.6f}" android:scaleX="{scale:.6f}" android:scaleY="{scale:.6f}">']
    if kind == 'LABEL':
        size = min(10, 22/max(1, sum(GLYPHS[c]['advance'] for c in label.replace('☎', '')))*10)
        parts += [text_paths(label, 12, 12, size)]
        if '☎' in label:
            parts += [path('M19,18 L21,16 L23,18 L21,20 Z')]
    else:
        stem = f'stacked_{row}' if row else kind.lower()
        shapes = list(ET.parse(ASSETS / (stem + '.svg')).getroot())
        for i, shape in enumerate(shapes):
            alpha = 1
            if row: alpha = 1 if kind != 'NO_SERVICE' and level*4/maximum >= i+1 else .24
            # Resolve all levels at generation time, including five-level cellular
            # and four-level Wi-Fi on glyphs with fewer segments. No live math.
            elif kind == 'WIFI': alpha = round(.24 + .76 * max(0, min(1, level*3/maximum-i)), 4)
            elif kind == 'CELLULAR': alpha = round(.24 + .76 * max(0, min(1, level*4/maximum-(3-i))), 4)
            elif kind == 'NO_SERVICE' and i == 0: alpha = .24
            elif kind == 'ACTIVITY': alpha = 1 if level & (1 << i) else .2
            parts += [path(shape.attrib['d'], float(shape.attrib.get('stroke-width', 0)), alpha)]
        if row and (kind == 'NO_SERVICE' or flags):
            parts += [text_paths('×' if kind == 'NO_SERVICE' else 'R' if flags & 2 else '!', 22, 9 if row == 1 else 18, 5.5)]
        elif flags:
            if flags & 1: parts += [text_paths('!', 21, 18, 7)]
            if flags & 2: parts += [text_paths('R', 4, 6, 7)]
            if flags & 4: parts += [path('M3,3 L21,21', 1.5)]
            if flags & 8: parts += [path('M21,20 A1,1 0 1,0 23,20 A1,1 0 1,0 21,20')]
    return '\n'.join(parts + ['</group>', '</vector>', ''])


def main():
    mapping = []
    xml_mapping = []
    for name, kind, level, maximum, row, flags, label in symbols():
        item = INVENTORY[name]
        target = 'thunder_ios_' + name
        if item['type'] == 'animation-list':
            frames = '\n'.join(f'    <item android:duration="{f["duration"]}" android:drawable="@drawable/thunder_ios_{f["name"]}"/>' for f in item['frames'])
            xml = f'<animation-list xmlns:android="http://schemas.android.com/apk/res/android" android:oneshot="false">\n{frames}\n</animation-list>\n'
        else:
            xml = vector(kind, int(level), int(maximum), int(row), int(flags), label, item.get('width', 21), item.get('height', 20))
            if item['type'] == 'inset':
                xml = '<inset xmlns:android="http://schemas.android.com/apk/res/android">\n' + xml + '</inset>\n'
        (OUT / (target + '.xml')).write_text('<!-- Generated by scripts/generate_status_drawables.py. -->\n' + xml)
        mapping.append(f'            case "com.android.systemui:drawable/{name}" -> R.drawable.{target};')
        if item['type'] == 'vector':
            xml_mapping.append(f'            case "com.android.systemui:drawable/{name}" -> R.drawable.{target};')
    (SOURCE / 'IosStatusResources.java').write_text('\n'.join([
        'package io.github.baishangui.thunder;', '',
        '/** Generated static resource allowlist. No runtime signal or battery calculations. */',
        'final class IosStatusResources {', '    static int replacement(String name) {',
        '        return switch(name) {', *mapping, '            default -> 0;',
        '        };', '    }', '    static int xmlReplacement(String name) {',
        '        return switch(name) {', *xml_mapping, '            default -> 0;',
        '        };', '    }', '    private IosStatusResources() { }', '}', '']))
    print(f'Prepared {len(mapping)} native Android drawable resources.')


if __name__ == '__main__':
    main()
