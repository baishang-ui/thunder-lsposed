"""Import original status-bar XML exclusively from the user-supplied C17 PUI pack.

Usage: python scripts/import_pui_icons.py PATH_TO_DECODED_C17
No raster conversion, curve fitting or redrawn icon fallback is used.
"""
from pathlib import Path
import copy
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'app/src/main/assets/icons/pui-reference'
NS = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', NS[1:-1])
ARCHIVE = 'PUI Theme For ColorOS 17_v17.0.0.137.zip'


def main(decoded):
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = {'sources': {}, 'overrides': {}, 'dimensions': {}}
    inventory = json.loads((ROOT / 'docs/status-icons-resource-inventory.json').read_text(encoding='utf-8'))
    for dimensions in decoded.glob('*/resources/res/values/dimens.xml'):
        for node in ET.parse(dimensions).getroot():
            if node.text.endswith('dp'): manifest['dimensions'][node.get('name')]=float(node.text[:-2])
    def save(name, tree, entry, raw=None, conversion='original C17 XML paths; native tint mask'):
        # Substitute only color RGB for the native system tint. Geometry, alpha,
        # group transforms and intrinsic sizes are copied directly from the pack.
        if tree.tag == 'inset' and tree.find('vector') is not None and inventory.get(name, {}).get('type') == 'vector':
            inset = tree
            tree = copy.deepcopy(inset.find('vector'))
            w = float(tree.get(NS+'width').removesuffix('dp'))
            h = float(tree.get(NS+'height').removesuffix('dp'))
            l, r, t, b = [float(inset.get(NS+'inset'+edge, '0dp').removesuffix('dp')) for edge in ('Left','Right','Top','Bottom')]
            vw, vh = float(tree.get(NS+'viewportWidth')), float(tree.get(NS+'viewportHeight'))
            tree.set(NS+'width', f'{w+l+r:g}dp'); tree.set(NS+'height', f'{h+t+b:g}dp')
            tree.set(NS+'viewportWidth', f'{vw*(w+l+r)/w:g}'); tree.set(NS+'viewportHeight', f'{vh*(h+t+b)/h:g}')
            if l or t:
                group = ET.Element('group', {NS+'translateX': f'{vw*l/w:g}', NS+'translateY': f'{vh*t/h:g}'})
                for child in list(tree): tree.remove(child); group.append(child)
                tree.append(group)
            conversion += '; original inset padding baked into vector canvas for native type contract'
        if tree.tag == 'inset' and tree.get(NS+'drawable') and inventory.get(name, {}).get('type') == 'vector':
            reference = tree.get(NS+'drawable').split('/')[-1]
            tree = ET.parse(decoded/'PuiThemeStatusIcon/resources/res/drawable'/(reference+'.xml')).getroot()
            conversion += '; referenced original vector inlined for native type contract'
        for node in tree.iter():
            for attr in ('fillColor','strokeColor'):
                color = node.get(NS+attr)
                if color and color.startswith('#'):
                    node.set(NS+attr, '#' + (color[1:3] if len(color)==9 else 'ff') + 'ffffff')
            for attr, value in list(node.attrib.items()):
                if value.startswith('@dimen/'):
                    node.set(attr, f'{manifest["dimensions"][value.split("/")[-1]]:g}dp')
                if value.startswith('@drawable/'):
                    node.set(attr, '@drawable/thunder_ios_'+value.split('/')[-1])
        (OUT / (name+'.xml')).write_text(ET.tostring(tree, encoding='unicode').rstrip()+'\n',encoding='utf-8')
        manifest['sources'][name]={'archive':ARCHIVE,'entry':entry,'conversion':conversion}
        if raw: manifest['sources'][name]['sha256']=hashlib.sha256(raw).hexdigest()
        manifest['overrides'][name]=name

    for module in ('PuiThemeStatusIcon','PuiThemeSignalIcon','PuiThemeBatteryHorizontal','PuiThemeBatteryVerticalIcon','PuiThemeBluetoothBatteryIcon','PuiThemeAdbStatusIcon'):
        folder=decoded/module/'resources/res'
        for p in folder.glob('drawable*/*.xml'):
            if p.stem == 'decor_close_button_dark': continue # window decoration, not a status icon
            save(p.stem,ET.parse(p).getroot(),module+'/'+p.relative_to(folder).as_posix(),p.read_bytes())
        dimensions=folder/'values/dimens.xml'
        if dimensions.exists():
            for node in ET.parse(dimensions).getroot():
                if node.text.endswith('dp'): manifest['dimensions'][node.get('name')]=float(node.text[:-2])

    aliases={'stat_sys_flavor_one_ringer_vibrate':'stat_sys_ringer_vibrate','stat_sys_branded_vpn':'stat_sys_vpn_ic'}
    for level in range(5):
        aliases[f'stat_signal_soft_signal_stacked_primary_{level}']=f'stat_signal_lte_signal_stacked_primary_{level}'
        for prefix in ('stat_signal_signal_lte_single_','stat_signal_soft_signal_'):
            for suffix in ('','_os17'): aliases[prefix+str(level)+suffix]=f'stat_signal_signal_lte_single_{level}_os17'
        aliases[f'stat_signal_wifi_signal_{level}']=f'stat_signal_wifi_signal_{level}_os17'
    for level, source_level in enumerate((0,1,2,4)):
        for suffix in ('','_error'): aliases[f'ic_wifi_{level}{suffix}']=f'stat_signal_wifi_signal_{source_level}_os17'
    aliases['ic_wifi_off']='stat_signal_wifi_signal_0_os17'
    aliases['ic_wifi_connecting']='stat_signal_wifi_signal_1_os17'
    aliases['stat_signal_soft_signal_stacked_primary_noservice']='stat_signal_lte_signal_stacked_primary_noservice'
    manifest['overrides'].update(aliases)
    # Native C17 owns progress level, charging and percentage. The pack supplies
    # the artwork; these wrappers preserve SystemUI's android:id/progress contract.
    for suffix in ('','percent_out_','percent_out_no_padding_'):
        name='stat_battery_horizontal_'+suffix+'progress'
        target='stat_battery_horizontal_'+('percent_out_no_padding_' if suffix=='percent_out_no_padding_' else '')+'progress_bg'
        tree=ET.Element('layer-list'); item=ET.SubElement(tree,'item',{NS+'id':'@android:id/progress'})
        ET.SubElement(item,'clip',{NS+'drawable':'@drawable/'+target,NS+'clipOrientation':'horizontal',NS+'gravity':'left'})
        save(name,tree,'PuiThemeBatteryHorizontal/'+target+'.xml',conversion='native progress wrapper around original C17 artwork')
    # Remove obsolete imported first-pack/reconstructed XMLs, only within OUT.
    keep=set(manifest['sources'])
    for p in OUT.glob('*.xml'):
        if p.stem not in keep: p.unlink()
    (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'Imported {len(keep)} original C17 resources; {len(manifest["overrides"])} mappings.')

if __name__=='__main__': main(Path(sys.argv[1]))
