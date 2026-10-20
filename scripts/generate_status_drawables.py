"""Prepare static Android drawables. Runtime state selection and drawing stay native.

Uses JDK 17+ to export the existing resource semantics, then writes compiled-resource
inputs. Visible geometry comes from C16; C17 supplies the native layout contract.
"""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'app/src/main/java/io/github/baishangui/thunder'
OUT = ROOT / 'app/src/main/res/drawable'
INVENTORY = json.loads((ROOT / 'docs/status-icons-resource-inventory.json').read_text())
SOURCE_ASSETS = ROOT / 'app/src/main/assets/icons/theme-reference'
THEME = json.loads((SOURCE_ASSETS / 'manifest.json').read_text(encoding='utf-8'))
NS = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', NS[1:-1])
ICON_SCALE = float(re.search(r'ICON_SCALE = ([0-9.]+)f', (SOURCE / 'IosStatusBarHook.java').read_text(encoding='utf-8'))[1])
SIDE_GAP = float(re.search(r'ICON_SIDE_GAP_DP = ([0-9.]+)f', (SOURCE / 'IosStatusBarHook.java').read_text(encoding='utf-8'))[1])


def sized_original(source):
    tree = ET.parse(SOURCE_ASSETS / (source + '.xml')).getroot()
    if not source.startswith(('stat_battery_', 'stat_bt_battery_')):
        # C17's system ImageViews use wrap_content and CENTER. They measure the
        # drawable's intrinsic size, bypassing the status_bar_* dimension values.
        # Grow the canvas for spacing as well as the image. Extend its viewport
        # by the same physical amount, so extra width does not stretch the ink.
        # Both SIM rows get the same translation and retain their shared frame.
        for vector in tree.iter('vector'):
            for attr in ('width', 'height'):
                value = vector.get(NS + attr)
                if not value or not value.endswith('dp'):
                    raise ValueError(f'{source}: expected literal intrinsic {attr}')
                vector.set(NS + attr, f'{float(value[:-2]) * ICON_SCALE:.9g}dp')
            width = float(vector.get(NS+'width')[:-2])
            viewport = float(vector.get(NS+'viewportWidth'))
            inset = SIDE_GAP * viewport / width
            vector.set(NS+'width', f'{width + 2*SIDE_GAP:.9g}dp')
            vector.set(NS+'viewportWidth', f'{viewport + 2*inset:.9g}')
            group = ET.Element('group', {NS+'name': 'thunder_spacing', NS+'translateX': f'{inset:.9g}'})
            for child in list(vector):
                vector.remove(child)
                group.append(child)
            vector.append(group)
    return ET.tostring(tree, encoding='unicode').rstrip() + '\n'


def symbols():
    home = os.environ.get('JAVA_HOME')
    javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
    java = str(Path(home) / 'bin/java') if home else shutil.which('java')
    with tempfile.TemporaryDirectory(prefix='thunder-icons-') as directory:
        folder = Path(directory)
        for name in ['IosStatusIcons.java', 'IosBatteryResources.java']:
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


def hidden_badge(name, kind):
    return kind in ('ACTIVITY', 'ACTIVITY_IN', 'ACTIVITY_OUT') or (kind == 'LABEL' and name != 'stat_sys_sos')


def main():
    mapping = []
    xml_mapping = []
    exported = symbols()
    known = {item[0] for item in exported}
    exported += [(name, 'BATTERY', '0', '100', '0', '0', '') for name in THEME['overrides'] if name in INVENTORY and name not in known]
    produced = set()
    for name, kind, level, maximum, row, flags, label in exported:
        item = INVENTORY[name]
        if item['type'] == 'animated-vector': continue  # Native animated state contracts remain intact.
        target = 'thunder_ios_' + name
        override = THEME['overrides'].get(name)
        if kind == 'NO_SERVICE':
            override = 'stat_signal_lte_signal_stacked_primary_noservice' if int(row) == 1 else 'stat_signal_lte_signal_stacked_secondary_noservice' if int(row) == 2 else 'stat_signal_signal_null_lte'
        elif kind == 'CELLULAR' and int(row):
            override = f'stat_signal_lte_signal_stacked_{"primary" if int(row) == 1 else "secondary"}_{level}'
        if kind == 'CELLULAR' and not int(row):
            override = f'stat_signal_signal_lte_single_{round(int(level)*4/int(maximum))}_os17'
        if not override and not hidden_badge(name, kind) and item['type'] != 'animation-list':
            continue  # Visible resources absent from C16 stay native.
        if item['type'] == 'animation-list' and not all(f['name'] in THEME['overrides'] for f in item['frames']):
            continue  # Do not invent artwork for an unprovided animation frame.
        if hidden_badge(name, kind):
            # Keep native vector/inset types and intrinsic sizes while drawing no ink.
            width, height = item.get('width', 21), item.get('height', 20)
            xml = f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{width:g}dp" android:height="{height:g}dp" android:viewportWidth="{width:g}" android:viewportHeight="{height:g}"><path android:fillColor="#00000000" android:pathData="M0,0h1v1h-1z"/></vector>\n'
            if item['type'] == 'inset':
                xml = '<inset xmlns:android="http://schemas.android.com/apk/res/android">\n' + xml + '</inset>\n'
        elif override:
            xml = sized_original(override)
        elif item['type'] == 'animation-list':
            frames = '\n'.join(f'    <item android:duration="{f["duration"]}" android:drawable="@drawable/thunder_ios_{f["name"]}"/>' for f in item['frames'])
            xml = f'<animation-list xmlns:android="http://schemas.android.com/apk/res/android" android:oneshot="false">\n{frames}\n</animation-list>\n'
        else:
            raise ValueError(f'No C16 artwork: {name}')
        produced.add(target)
        (OUT / (target + '.xml')).write_text('<!-- Generated by scripts/generate_status_drawables.py. -->\n' + xml)
        mapping.append(f'            case "com.android.systemui:drawable/{name}" -> R.drawable.{target};')
        if ET.fromstring(xml.removeprefix('<!-- Generated by scripts/generate_status_drawables.py. -->\n')).tag == 'vector':
            xml_mapping.append(f'            case "com.android.systemui:drawable/{name}" -> R.drawable.{target};')
    # Include original assets referenced by wrappers (e.g. Bluetooth's inset).
    for source in THEME['sources']:
        target = 'thunder_ios_' + source
        if target not in produced:
            (OUT / (target + '.xml')).write_text(sized_original(source), encoding='utf-8')
            produced.add(target)
    for old in OUT.glob('thunder_ios_*.xml'):
        if old.stem not in produced: old.unlink()
    (SOURCE / 'IosStatusResources.java').write_text('\n'.join([
        'package io.github.baishangui.thunder;', '',
        '/** Generated static resource allowlist. No runtime signal or battery calculations. */',
        'final class IosStatusResources {', '    static int replacement(String name) {',
        '        return switch(name) {', *mapping, '            default -> 0;',
        '        };', '    }', '    static int xmlReplacement(String name) {',
        '        return switch(name) {', *xml_mapping, '            default -> 0;',
        '        };', '    }', '    static Float dimension(String name) {',
        '        return switch(name) {',
        *[f'            case "com.android.systemui:dimen/{name}" -> {value:.9g}f;' for name, value in THEME['dimensions'].items()],
        '            default -> null;', '        };', '    }',
        '    private IosStatusResources() { }', '}', '']))
    print(f'Prepared {len(mapping)} native Android drawable resources.')


if __name__ == '__main__':
    main()
