"""Validate C16 artwork adapted to C17 and native resource contracts."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from zipfile import ZipFile
ROOT=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk',type=Path,help='Also verify actual intrinsic sizes in the compiled APK with aapt2')
parser.add_argument('--c16',type=Path,help='Also compare every visible path and source hash against the original user archive')
args=parser.parse_args()
DRAWABLES=ROOT/'app/src/main/res/drawable'
JAVA=ROOT/'app/src/main/java/io/github/baishangui/thunder'
REF=ROOT/'app/src/main/assets/icons/theme-reference'
NS='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', NS[1:-1])
theme=json.loads((REF/'manifest.json').read_text(encoding='utf-8'))
assert theme.get('artwork_archive') == '双排信号状态栏模块C16.zip', 'Visible artwork must come from the requested C16 pack'
inventory=json.loads((ROOT/'docs/status-icons-resource-inventory.json').read_text(encoding='utf-8'))
mapping=(JAVA/'IosStatusResources.java').read_text(encoding='utf-8')
names=set(re.findall(r'case "com.android.systemui:drawable/([^\"]+)"',mapping))
assert names <= set(inventory)
def geometry(tree):
    # Unwrap only the explicit spacing transform before comparing source art.
    tree=ET.fromstring(ET.tostring(tree))
    for vector in tree.iter('vector'):
        group=vector.find('group')
        if group is not None and group.get(NS+'name')=='thunder_spacing':
            inset=float(group.get(NS+'translateX'))
            width=float(vector.get(NS+'width')[:-2]);viewport=float(vector.get(NS+'viewportWidth'))
            assert abs(inset*width/viewport-1)<.00001,'spacing must be exactly 1dp per side'
            vector.set(NS+'viewportWidth',f'{viewport-2*inset:g}')
            vector.remove(group)
            for child in list(group):vector.append(child)
        vector.attrib.pop(NS+'width',None);vector.attrib.pop(NS+'height',None)
    return ET.tostring(tree)
originals={geometry(ET.parse(p).getroot()) for p in REF.glob('*.xml')}
def xml(name):return ET.parse(DRAWABLES/('thunder_ios_'+name+'.xml')).getroot()
def signature(name):return hashlib.sha256(ET.tostring(xml(name))).hexdigest()
for source in theme['sources'].values():
    assert source['archive']=='双排信号状态栏模块C16.zip'
    assert source['overlay'] in ('ThemeStatusIcon', 'ThemeHorizontalBattery', 'ThemeBluetoothBatteryIcon')
    assert source['entry'].startswith('res/')
    assert len(source['sha256']) == 64
for name, source in theme['sources'].items():
    assert [hashlib.sha256(p.get(NS+'pathData').encode()).hexdigest()
            for p in ET.parse(REF/(name+'.xml')).getroot().iter('path')] == source['path_sha256']
if args.c16:
    from import_theme_icons import Overlay, find_aapt2
    with tempfile.TemporaryDirectory(prefix='status-source-check-') as directory:
        overlays={}
        for name, source in theme['sources'].items():
            module=source['overlay']
            if module not in overlays:
                overlays[module]=Overlay(args.c16,module,Path(directory),find_aapt2())
            overlay=overlays[module]
            assert overlay.provenance(source['resource'])['sha256']==source['sha256'],(name,'source hash differs from C16 archive')
            original=overlay.xml(source['resource'])
            paths={hashlib.sha256(p.get(NS+'pathData').encode()).hexdigest() for p in original.iter('path')}
            assert set(source['path_sha256'])<=paths,(name,'visible path does not occur in the C16 original')
    print('Verified every visible path and source SHA-256 against the original C16 archive.')
for p in DRAWABLES.glob('thunder_ios_*.xml'):
    name=p.stem.removeprefix('thunder_ios_');root=ET.parse(p).getroot()
    if name in inventory:
        expected=inventory[name]['type']
        if expected in ('inset','layer-list','animation-list','vector'):assert root.tag==expected,(name,root.tag,expected)
    for node in root.iter():
        for value in node.attrib.values():
            assert '?' not in value and not value.startswith('@dimen/'),(name,value)
            if value.startswith('@drawable/'):
                assert value.startswith('@drawable/thunder_ios_')
                assert (DRAWABLES/(value.split('/')[-1]+'.xml')).exists(),(name,value)
    if root.tag=='animation-list':
        assert [int(n.get(NS+'duration')) for n in root]==[f['duration'] for f in inventory[name]['frames']]
        continue
    # Every visible path is from the imported/adapted C16 XML. Others may only be fully
    # transparent badge suppressors; no old/generated illustration can slip in.
    paths=list(root.iter('path'))
    transparent=paths and all(p.get(NS+'fillColor')=='#00000000' and NS+'strokeColor' not in p.attrib for p in paths)
    if transparent:continue
    assert geometry(root) in originals,(name,'artwork must exactly match a C16 source or adaptation')

# C17 measures these with wrap_content, not status_bar_* dimension resources.
# Read the actual generated canvas sizes to catch the previous dimension-only
# change that enlarged the battery but left these images at their original size.
for name in ('stat_sys_alarm','stat_sys_airplane_mode','stat_sys_data_bluetooth',
             'stat_sys_ringer_vibrate','stat_sys_nfc','stat_sys_vpn_ic',
             'stat_signal_wifi_signal_4_os17','stat_signal_signal_lte_single_4_os17',
             'stat_signal_lte_signal_stacked_primary_4','stat_signal_lte_signal_stacked_secondary_4'):
    source=theme['overrides'][name]
    before=ET.parse(REF/(source+'.xml')).getroot();after=xml(name)
    for attr in ('width','height'):
        original=float(before.get(NS+attr)[:-2]);actual=float(after.get(NS+attr)[:-2])
        extra=2 if attr=='width' else 0
        assert abs(actual-original*1.12-extra)<.00001,(name,attr,'12 percent ink growth plus 1dp spacing on each side')
    assert geometry(before)==geometry(after),(name,'resizing must not redraw or crop artwork')
for name in ('stat_battery_horizontal_bg','stat_battery_horizontal_progress_bg','stat_bt_battery_black_0'):
    assert ET.tostring(xml(name))==ET.tostring(ET.parse(REF/(name+'.xml')).getroot()),(name,'do not enlarge the battery twice')
for prefix in ('stat_signal_lte_signal_stacked_primary_','stat_signal_lte_signal_stacked_secondary_','stat_signal_wifi_signal_'):
    suffix='_os17' if 'wifi' in prefix else ''
    assert len({signature(prefix+str(level)+suffix) for level in range(5)})==(4 if 'wifi' in prefix else 5),prefix
assert signature('ic_wifi_3')==signature('ic_wifi_3_error')
for name in ('stat_sys_hotspot','stat_sys_cast','stat_sys_rotate_portrait','stat_sys_high_performance',
             'stat_sys_data_saver','stat_battery_vertical_bg','stat_battery_vertical_frame','stat_battery_pb','usb_notification_icon'):
    assert name not in names,'Icons absent from the C16 pack remain native'
    assert not (DRAWABLES/('thunder_ios_'+name+'.xml')).exists(), 'Obsolete drawable must be removed'
    assert not (REF/(name+'.xml')).exists(), 'Obsolete source asset must be removed'

# Preserve exact C16 row outlines, with independent 0..4 strengths. The lower
# row in C16 was partly hard-coded; its state must now track the second SIM.
row_paths = {}
for row in ('primary','secondary'):
    for level in range(5):
        root=xml(f'stat_signal_lte_signal_stacked_{row}_{level}')
        paths=list(root.iter('path'))
        assert len(paths)==4
        assert sum(p.get(NS+'fillColor')=='#ffffffff' for p in paths)==level,(row,level,'independent illuminated bars')
        assert all(p.get(NS+'fillColor') in ('#ffffffff','#35ffffff') for p in paths)
        data={p.get(NS+'pathData') for p in paths}
        if level==0:row_paths[row]=data
        assert data==row_paths[row],(row,'levels must use the same source outlines')
        assert float(root.get(NS+'height')[:-2])==16*1.12
assert row_paths['primary'].isdisjoint(row_paths['secondary']), 'Rows may not contain one another or double-draw the other SIM'
assert len({(signature(f'stat_signal_lte_signal_stacked_primary_{a}'),signature(f'stat_signal_lte_signal_stacked_secondary_{b}'))
            for a in range(5) for b in range(5)})==25
for name in ('stat_signal_wifi_6','stat_signal_wifi_7','stat_signal_wifi_double_os17',
             'stat_signal_activity_wifi_inout_os17','stat_signal_connected_5g','stat_signal_volte'):
    assert name in names,'Transparent badge suppression must remain available'
    assert all(p.get(NS+'fillColor')=='#00000000' for p in xml(name).iter('path'))
assert theme['dimensions']['status_bar_battery_horizontal_width']==28
assert theme['dimensions']['status_bar_battery_horizontal_height']==17
assert abs(theme['dimensions']['status_bar_battery_horizontal_progress_end']-28*8/93)<.00001
assert abs(theme['dimensions']['status_bar_battery_horizontal_progress_top']-17*5/55)<.00001
background=xml('stat_battery_horizontal_bg')
fill=xml('stat_battery_horizontal_progress_bg')
assert next(background.iter('path')).get(NS+'pathData')==next(fill.iter('path')).get(NS+'pathData')
assert fill.find('group').get(NS+'translateY')=='-5'
assert float(fill.get(NS+'viewportWidth'))==85 and float(fill.get(NS+'viewportHeight'))==45
assert abs((28-theme['dimensions']['status_bar_battery_horizontal_progress_end'])/85-28/93)<.00001
assert abs((17-2*theme['dimensions']['status_bar_battery_horizontal_progress_top'])/45-17/55)<.00001
for name in ('stat_battery_horizontal_progress','stat_battery_horizontal_percent_out_progress','stat_battery_horizontal_percent_out_no_padding_progress'):
    item=xml(name).find('item');assert item.get(NS+'id')=='@android:id/progress';assert item.find('clip').get(NS+'clipOrientation')=='horizontal'
hook=(JAVA/'IosStatusBarHook.java').read_text(encoding='utf-8')
for forbidden in ('Canvas','Paint','SignalDrawable','getPowerLevel','getLevel(','"dispatchDraw"','"onDraw"','setImageDrawable','setImageResource','StateFlow','BatteryViewBinder','new IosIconDrawable'):
    assert forbidden not in hook,forbidden
settings=(ROOT/'app/src/main/kotlin/io/github/baishangui/thunder/StatusIconSettings.kt').read_text(encoding='utf-8')
assert '需重启系统界面' in settings and '切回系统默认' in settings
assert 'IosIconDrawable(symbol)' not in settings
if args.apk:
    with ZipFile(args.apk) as apk:
        assert apk.read('assets/icons/theme-reference/manifest.json')==(REF/'manifest.json').read_bytes(), 'APK contains a stale source manifest'
        for name in theme['sources']:
            assert apk.read('assets/icons/theme-reference/'+name+'.xml')==(REF/(name+'.xml')).read_bytes(), (name,'APK contains a stale source asset')
    from import_theme_icons import find_aapt2
    aapt=find_aapt2()
    assert aapt,'Android SDK aapt2 is required for APK verification'
    table=subprocess.check_output([str(aapt),'dump','resources',str(args.apk)],text=True,encoding='utf-8')
    for name in ('stat_sys_alarm','stat_sys_data_bluetooth','stat_signal_wifi_signal_4_os17',
                 'stat_signal_lte_signal_stacked_primary_4','stat_signal_lte_signal_stacked_secondary_4',
                 'stat_battery_horizontal_bg'):
        # Release optimization shortens file paths. Resolve the compiled resource
        # name through the table rather than assuming source paths survive.
        entry=re.search(r'resource .* drawable/thunder_ios_'+re.escape(name)+r'\n\s+\(\) \(file\) (\S+) type=XML',table)
        assert entry,(name,'compiled resource missing')
        output=subprocess.check_output([str(aapt),'dump','xmltree','--file',entry[1],str(args.apk)],text=True,encoding='utf-8')
        for attr in ('width','height'):
            match=re.search(r'android:'+attr+r'\([^)]*\)=([0-9.]+)dp',output)
            assert match,(name,attr,'compiled intrinsic dimension missing')
            expected=float(xml(name).get(NS+attr)[:-2])
            assert abs(float(match[1])-expected)<.001,(name,attr,'APK has stale/unscaled resources')
    print('Verified compiled APK intrinsic sizes for ordinary icons, Wi-Fi, both SIM rows and unchanged battery.')
print(f'Verified {len(names)} C16/C17 resource mappings, source geometry, obsolete-icon removal, transparent badges, 25 SIM combinations and native battery clipping.')
