"""Import C16 artwork and adapt its resource contracts to ColorOS 17.

Usage: python scripts/import_theme_icons.py C16.zip C17.zip [--aapt2 PATH]
Only APK resources are read. Archive installers and mount scripts are never run.
"""
from pathlib import Path
from zipfile import ZipFile
import argparse
import copy
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'app/src/main/assets/icons/theme-reference'
NS = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', NS[1:-1])
ARCHIVE = '双排信号状态栏模块C16.zip'
LAYOUT_ARCHIVE = 'ColorOS 17 reference archive'


def find_aapt2(explicit=None):
    candidates = [explicit, shutil.which('aapt2')]
    sdk_roots = [ROOT/'.local/work/android-sdk', Path.home()/'Library/Android/sdk', Path.home()/'Android/Sdk']
    import os
    sdk_roots += [Path(os.environ[key]) for key in ('ANDROID_HOME', 'ANDROID_SDK_ROOT') if os.environ.get(key)]
    properties = ROOT/'local.properties'
    if properties.exists():
        sdk_roots += [Path(line.split('=', 1)[1]) for line in properties.read_text().splitlines() if line.startswith('sdk.dir=')]
    for sdk in sdk_roots:
        candidates += sorted((sdk/'build-tools').glob('*/aapt2'), reverse=True)
        candidates += sorted((sdk/'build-tools').glob('*/aapt2.exe'), reverse=True)
    return next((str(p) for p in candidates if p and Path(p).is_file()), None)


class Overlay:
    """Decode audited resource names through aapt2, including shortened APK paths."""
    def __init__(self, archive, module, directory, aapt2):
        self.aapt2 = aapt2
        self.module = module
        with ZipFile(archive) as pack:
            entries = [n for n in pack.namelist() if n.endswith('.apk') and Path(n).stem.endswith(module)]
            if len(entries) != 1:
                raise ValueError(f'Expected one {module} overlay, found {len(entries)}')
            self.entry = entries[0]
            self.path = directory / (module+'.apk')
            self.path.write_bytes(pack.read(self.entry))
        self.table = subprocess.check_output([aapt2, 'dump', 'resources', str(self.path)], text=True)
        self.ids = {m[1]: m[2] for m in re.finditer(r'resource (0x[0-9a-f]+) (\S+)', self.table)}
        self.files, self.dimensions = {}, {}
        for match in re.finditer(r'resource 0x[0-9a-f]+ (\S+)\n(.*?)(?=    resource|  type|\Z)', self.table, re.S):
            name, body = match[1], match[2]
            files = re.findall(r'\(([^)]*)\) \(file\) (\S+) type=XML', body)
            if files:
                self.files[name.split('/')[-1]] = next((p for c, p in files if 'night' not in c), files[0][1])
            dimen = re.search(r'\(\) (-?[0-9.]+)dp', body)
            if name.startswith('dimen/') and dimen:
                self.dimensions[name.split('/')[-1]] = float(dimen[1])

    def xml(self, name):
        entry = self.files[name]
        output = subprocess.check_output([self.aapt2, 'dump', 'xmltree', '--file', entry, str(self.path)], text=True)
        stack, root = [], None
        for line in output.splitlines():
            element = re.match(r'( *)E: (\S+)', line)
            if element:
                indent = len(element[1])
                node = ET.Element(element[2])
                while stack and stack[-1][0] >= indent:
                    stack.pop()
                if stack:
                    stack[-1][1].append(node)
                else:
                    root = node
                stack.append((indent, node))
            attr = re.match(r' *A: (?:http://schemas.android.com/apk/res/android:)?(\w+)\(0x[0-9a-f]+\)=(.*)', line)
            if not attr:
                continue
            key, value = attr[1], attr[2].split(' (Raw: ', 1)[0]
            if value.startswith('"'):
                value = value[1:-1]
            elif value.startswith('@0x'):
                value = '@'+self.ids.get(value[1:], value[1:])
            enums = {'fillType': {'0': 'nonZero', '1': 'evenOdd'},
                     'strokeLineCap': {'0': 'butt', '1': 'round', '2': 'square'},
                     'strokeLineJoin': {'0': 'miter', '1': 'round', '2': 'bevel'}}
            value = enums.get(key, {}).get(value, value)
            stack[-1][1].set(NS+key, value)
        if root is None:
            raise ValueError(f'Unable to decode {self.entry}/{entry}')
        return root

    def provenance(self, name):
        entry = self.files[name]
        with ZipFile(self.path) as apk:
            digest = hashlib.sha256(apk.read(entry)).hexdigest()
        return {'archive': ARCHIVE, 'overlay': self.module, 'entry': entry, 'resource': name, 'sha256': digest}


def main(c16, c17, aapt2):
    inventory = json.loads((ROOT/'docs/status-icons-resource-inventory.json').read_text())
    sizing = json.loads((ROOT/'docs/status-icons-sizing.json').read_text())
    manifest = {'artwork_archive': ARCHIVE, 'layout_archive': LAYOUT_ARCHIVE,
                'sources': {}, 'overrides': {}, 'dimensions': {}, 'sizing': sizing['targets']}
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='status-c16-') as folder:
        directory = Path(folder)
        status = Overlay(c16, 'ThemeStatusIcon', directory, aapt2)
        battery = Overlay(c16, 'ThemeHorizontalBattery', directory, aapt2)
        bluetooth = Overlay(c16, 'ThemeBluetoothBatteryIcon', directory, aapt2)
        layout = Overlay(c17, 'ThemeSignalIcon', directory, aapt2)
        # C17 supplies only the shared frame / resource naming contract, never ink.
        frame = layout.xml('stat_signal_lte_signal_stacked_primary_4')
        frame_width = float(frame.get(NS+'width')[:-2])
        frame_height = float(frame.get(NS+'height')[:-2])
        manifest['signal_frame'] = {'width': frame_width, 'height': frame_height,
                                    'reference': layout.module, 'resource': 'stat_signal_lte_signal_stacked_primary_4',
                                    'sha256': layout.provenance('stat_signal_lte_signal_stacked_primary_4')['sha256']}

        def tint(tree, dimensions):
            for node in tree.iter():
                for key, value in list(node.attrib.items()):
                    if value.startswith('@dimen/'):
                        value = f'{dimensions[value.split("/")[-1]]:g}dp'
                    if key in (NS+'fillColor', NS+'strokeColor') and value.startswith('#'):
                        value = '#'+(value[1:3] if len(value)==9 else 'ff')+'ffffff'
                    if key in (NS+'width', NS+'height') and value.endswith('px'):
                        value = value[:-2]+'dp'
                    node.set(key, value)
            return tree

        def vector(tree):
            # Pack insets are legacy inter-icon spacing (usually +5dp). Normalize
            # that spacing centrally in the generator; preserve the actual paths.
            if tree.tag == 'inset':
                child = tree.find('vector')
                if child is None:
                    raise ValueError('Expected an inline source vector')
                tree = copy.deepcopy(child)
            return tree

        def save(name, tree, overlay, original, conversion):
            tree = tint(copy.deepcopy(tree), overlay.dimensions)
            fraction=sizing['optical_height_fractions'].get(name)
            if fraction and tree.tag=='vector':
                old_height=float(tree.get(NS+'height')[:-2])
                height=sizing['targets']['status_visible_height_dp']/fraction
                tree.set(NS+'width',f'{float(tree.get(NS+"width")[:-2])*height/old_height:.9g}dp')
                tree.set(NS+'height',f'{height:.9g}dp')
                conversion+='; optical visible height matched to the user reference'
            for node in tree.iter():
                for key, value in list(node.attrib.items()):
                    if value.startswith('@drawable/'):
                        node.set(key, '@drawable/thunder_ios_'+value.split('/')[-1])
            (OUT/(name+'.xml')).write_text(ET.tostring(tree, encoding='unicode')+'\n', encoding='utf-8')
            manifest['sources'][name] = dict(overlay.provenance(original), conversion=conversion,
                path_sha256=[hashlib.sha256(p.get(NS+'pathData').encode()).hexdigest() for p in tree.iter('path')])
            manifest['overrides'][name] = name

        # Keep only artwork present in the first pack and applicable to the
        # existing status-icon feature. Badge hiding is handled separately.
        for overlay in (status, bluetooth):
            for name in sorted(overlay.files):
                if name not in inventory or name.startswith('stat_signal_') or 'fully_connected' in name:
                    continue
                tree = vector(overlay.xml(name))
                if tree.tag != 'vector':
                    continue
                save(name, tree, overlay, name, 'C16 paths and opacity; native tint; standardized external spacing')
        for target, source in {'stat_sys_data_bluetooth_connected': 'stat_sys_data_bluetooth_connected_stop',
                               'stat_sys_branded_vpn': 'stat_sys_vpn_ic'}.items():
            manifest['overrides'][target] = source

        # Top bars and lower short bars are extracted verbatim from C16 s0..s4.
        # C16 lower rows had hard-coded levels; C17 selects each SIM independently.
        for level in range(5):
            original = f'stat_signal_signal_lte_single_{level}'
            source = vector(status.xml(original))
            paths = list(source.iter('path'))
            top = [p for p in paths if float(re.match(r'M([0-9.]+),([0-9.]+)', p.get(NS+'pathData'))[2]) < 9]
            bottom = [p for p in paths if p not in top]
            assert len(top) == len(bottom) == 4
            bottom.sort(key=lambda p: float(re.match(r'M([0-9.]+)', p.get(NS+'pathData'))[1]))
            # Center the original 19x13 artwork inside C17's shared 19x16 frame.
            for row, selected in [('primary', top), ('secondary', bottom), ('single', top)]:
                stacked_scale=sizing['targets']['stacked_visible_height_dp']/12.6
                width=sizing['targets']['single_signal_width_dp'] if row=='single' else frame_width*stacked_scale
                height=sizing['targets']['single_signal_height_dp'] if row=='single' else frame_height*stacked_scale
                viewport_height=8 if row=='single' else 16
                tree = ET.Element('vector', {NS+'width': f'{width:.9g}dp', NS+'height': f'{height:.9g}dp',
                    NS+'viewportWidth': '19', NS+'viewportHeight': str(viewport_height)})
                offset = (16-13)/2 if row != 'single' else (8-7.56)/2
                group = ET.SubElement(tree, 'group', {NS+'translateY': f'{offset:g}'})
                for index, path in enumerate(selected):
                    path = copy.deepcopy(path)
                    if row == 'secondary':
                        path.set(NS+'fillColor', '#ff000000' if index < level else '#35000000')
                    group.append(path)
                name = f'stat_signal_signal_lte_single_{level}_os17' if row == 'single' else f'stat_signal_lte_signal_stacked_{row}_{level}'
                save(name, tree, status, original, f'C16 {row} paths; C17 shared frame; independent native SIM strength')
            for prefix in ('stat_signal_signal_lte_single_', 'stat_signal_soft_signal_'):
                for suffix in ('', '_os17'):
                    manifest['overrides'][prefix+str(level)+suffix] = f'stat_signal_signal_lte_single_{level}_os17'
            for prefix in ('stat_signal_soft_signal_stacked_primary_', 'stat_signal_soft_signal_stacked_secondary_'):
                manifest['overrides'][prefix+str(level)] = f'stat_signal_lte_signal_stacked_{"primary" if "primary" in prefix else "secondary"}_{level}'
            wifi = f'stat_signal_wifi_signal_{level}'
            wifi_tree=vector(status.xml(wifi))
            wifi_canvas=vector(status.xml('stat_signal_wifi_signal_4'))
            for attr in ('width','height'):wifi_tree.set(NS+attr,wifi_canvas.get(NS+attr))
            save(wifi+'_os17', wifi_tree, status, wifi, 'C16 Wi-Fi paths and opacity; shared state canvas; reference optical sizing')
            manifest['overrides'][wifi] = wifi+'_os17'
        for row in ('primary', 'secondary'):
            original = 'stat_signal_noservice_lte'
            source = vector(status.xml(original))
            stacked_scale=sizing['targets']['stacked_visible_height_dp']/12.6
            tree = ET.Element('vector', {NS+'width': f'{19*stacked_scale:.9g}dp', NS+'height': f'{16*stacked_scale:.9g}dp',
                NS+'viewportWidth': '19', NS+'viewportHeight': '16'})
            # Align the source short bars to the regular row's baseline.
            selected = source.find('group')
            group = ET.SubElement(tree, 'group', {NS+'translateY': '5.46' if row == 'primary' else '10.5'})
            for path in selected:
                group.append(copy.deepcopy(path))
            name = f'stat_signal_lte_signal_stacked_{row}_noservice'
            save(name, tree, status, original, 'C16 no-service short bars aligned to C17 SIM row')
            for prefix in ('stat_signal_lte_signal_stacked_', 'stat_signal_soft_signal_stacked_'):
                manifest['overrides'][prefix+row+'_noservice'] = name
        # Keep the actual C16 no-service short bars for a single unavailable SIM.
        no_service=vector(status.xml('stat_signal_signal_null_lte'))
        no_service.set(NS+'width',f'{sizing["targets"]["single_signal_width_dp"]:g}dp')
        no_service.set(NS+'height',f'{sizing["targets"]["single_signal_height_dp"]:g}dp')
        save('stat_signal_signal_null_lte', no_service, status,
             'stat_signal_signal_null_lte', 'C16 no-service paths and opacity; standardized spacing')
        for level, source_level in enumerate((0, 1, 2, 4)):
            for suffix in ('', '_error'):
                manifest['overrides'][f'ic_wifi_{level}{suffix}'] = f'stat_signal_wifi_signal_{source_level}_os17'

        # C17 assigns the same bounds to background and progress drawables.
        # Preserve the original proportions and padding. Scaling only the body
        # height would turn the battery into a short, tall shape.
        original_battery = vector(battery.xml('stat_battery_horizontal_bg'))
        sizing_source=(ROOT/'app/src/main/java/io/github/baishangui/thunder/IosStatusBarHook.java').read_text()
        battery_width = float(re.search(r'BATTERY_WIDTH_DP = ([0-9.]+)f',sizing_source)[1])
        battery_text_size = float(re.search(r'BATTERY_TEXT_SIZE_DP = ([0-9.]+)f',sizing_source)[1])
        battery_height = battery_width * 55/93
        def battery_canvas():
            return ET.Element('vector', {NS+'width': f'{battery_width:.9g}dp', NS+'height': f'{battery_height:.9g}dp',
                NS+'viewportWidth': '93', NS+'viewportHeight': '55'})
        for name in ('stat_battery_horizontal_bg', 'stat_battery_horizontal_frame', 'stat_battery_horizontal_percent_out_bg'):
            tree = battery_canvas()
            for path in vector(battery.xml(name)):
                tree.append(copy.deepcopy(path))
            save(name, tree, battery, name, f'C16 battery paths and original 93x55 canvas; uniform enlargement to {battery_width:g}dp width')
        tree = battery_canvas()
        for path in original_battery:
            tree.append(copy.deepcopy(path))
        save('stat_battery_horizontal_progress_bg', tree, battery, 'stat_battery_horizontal_bg',
             f'C16 body and cap paths; original 93x55 canvas; full coverage at 100 percent; {battery_width:g}dp width')
        aliases = {'stat_battery_horizontal_percent_out_no_padding_bg': 'stat_battery_horizontal_percent_out_bg',
                   'stat_battery_horizontal_percent_out_progress_bg': 'stat_battery_horizontal_progress_bg',
                   'stat_battery_horizontal_percent_out_no_padding_progress_bg': 'stat_battery_horizontal_progress_bg'}
        manifest['overrides'].update(aliases)
        for suffix in ('', 'percent_out_', 'percent_out_no_padding_'):
            name = 'stat_battery_horizontal_'+suffix+'progress'
            tree = ET.Element('layer-list')
            item = ET.SubElement(tree, 'item', {NS+'id': '@android:id/progress'})
            ET.SubElement(item, 'clip', {NS+'drawable': '@drawable/stat_battery_horizontal_progress_bg',
                NS+'clipOrientation': 'horizontal', NS+'gravity': 'left'})
            save(name, tree, battery, 'stat_battery_horizontal_progress', 'C17 native progress ID and clip around C16 battery body')
        # The artwork contains its padding. Legacy ProgressBar consumers must
        # also receive the full frame instead of applying that padding twice.
        # No vertical-battery or ADB dimensions from the second pack are retained.
        manifest['dimensions'].update({name: bluetooth.dimensions[name]
            for name in ('stat_bt_battery_height', 'stat_bt_battery_width')})
        manifest['dimensions'].update({
            'status_bar_battery_horizontal_width': battery_width, 'status_bar_battery_horizontal_height': battery_height,
            'status_bar_battery_horizontal_progress_start': 0.0,
            'status_bar_battery_horizontal_progress_end': 0.0,
            'status_bar_battery_horizontal_progress_top': 0.0,
            'status_bar_battery_horizontal_progress_bottom': 0.0,
            'status_bar_battery_horizontal_padding_left': 0.0,
            'status_bar_battery_horizontal_padding_right': 0.0,
            'status_bar_battery_horizontal_padding_top': 0.0,
            'status_bar_battery_horizontal_padding_bottom': 0.0,
            'battery_progress_corner_radius': battery_width*9/93,
            'battery_percent_in_text_size': battery_text_size, 'battery_percent_in_text_margin_top': 0.14,
            'battery_percent_in_text_offset_x': 0.0, 'battery_percent_in_text_offset_y': 0.25,
            'signal_cluster_battery_padding': sizing['targets']['battery_separator_dp'],
        })
        # C17 dimensions are consulted above for the frame only; assert provenance
        # before pruning old assets so an accidental source swap cannot go unnoticed.
        assert all(s['archive'] == ARCHIVE for s in manifest['sources'].values())
        for path in OUT.glob('*.xml'):
            if path.stem not in manifest['sources']:
                path.unlink()
        (OUT/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        print(f'Imported {len(manifest["sources"])} C16 artwork/adaptation assets; {len(manifest["overrides"])} mappings.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('c16', type=Path)
    parser.add_argument('c17', type=Path)
    parser.add_argument('--aapt2', type=Path)
    args = parser.parse_args()
    executable = find_aapt2(args.aapt2)
    if not executable:
        parser.error('Provide Android SDK aapt2 with --aapt2 or PATH.')
    main(args.c16, args.c17, executable)
