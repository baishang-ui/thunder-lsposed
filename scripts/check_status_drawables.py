"""Validate packaged static status resources independently of the preview renderer."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
DRAWABLES = ROOT / 'app/src/main/res/drawable'
JAVA = ROOT / 'app/src/main/java/io/github/baishangui/thunder'
NS = '{http://schemas.android.com/apk/res/android}'
names = (ROOT / 'scripts/fixtures/iconstyle/status-resources.txt').read_text().splitlines()
inventory = json.loads((ROOT / 'docs/status-icons-resource-inventory.json').read_text())
mapping = (JAVA / 'IosStatusResources.java').read_text()
assert set(names) == set(inventory)
assert set(re.findall(r'case "com.android.systemui:drawable/([^"]+)"', mapping)) == set(names)
assert set(p.stem.removeprefix('thunder_ios_') for p in DRAWABLES.glob('thunder_ios_*.xml')) == set(names)


def xml(name):
    return ET.parse(DRAWABLES / ('thunder_ios_' + name + '.xml')).getroot()


def signature(name):
    return hashlib.sha256(ET.tostring(xml(name))).hexdigest()


for name in names:
    root = xml(name)
    kind = inventory[name]['type']
    expected = kind if kind in ('animation-list', 'inset') else 'vector'
    assert root.tag == expected, name
    for node in root.iter():
        for value in node.attrib.values():
            assert '?' not in value, (name, 'theme attribute')
            if '@' in value:
                assert kind == 'animation-list' and value.startswith('@drawable/thunder_ios_'), (name, value)
                assert (DRAWABLES / (value.split('/')[-1] + '.xml')).exists(), value
    if root.tag == 'vector':
        assert float(root.attrib[NS + 'viewportWidth']) > 0, name
        assert float(root.attrib[NS + 'viewportHeight']) > 0, name
        assert len(list(root.iter('path'))) > 0, name
    if kind == 'animation-list':
        assert [int(n.attrib[NS + 'duration']) for n in root] == [f['duration'] for f in inventory[name]['frames']]

# System-selected states must not collapse to a single static picture.
for pattern, levels in [('stat_signal_wifi_signal_{}_os17', 5),
                        ('stat_sys_signal_{}', 5),
                        ('stat_sys_signal_5_bar_{}', 6),
                        ('stat_signal_lte_signal_stacked_primary_{}', 5),
                        ('stat_signal_lte_signal_stacked_secondary_{}', 5)]:
    assert len({signature(pattern.format(level)) for level in range(levels)}) == levels, pattern
assert signature('stat_signal_lte_signal_stacked_primary_2') != signature('stat_signal_lte_signal_stacked_secondary_2')
assert signature('ic_wifi_3') != signature('ic_wifi_3_error')
assert signature('ic_vowifi') != signature('ic_vowifi_calling')

# Native calculations and draw callbacks must remain outside this implementation.
hook = (JAVA / 'IosStatusBarHook.java').read_text()
for forbidden in ['Canvas', 'Paint', 'SignalDrawable', 'getPowerLevel', 'getLevel(', '"dispatchDraw"', '"onDraw"', 'setImageDrawable', 'setImageResource', 'StateFlow', 'BatteryViewBinder', 'new IosIconDrawable']:
    assert forbidden not in hook, forbidden
settings = (ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/StatusIconSettings.kt').read_text()
assert '需重启系统界面' in settings and '切回系统默认' in settings
assert 'Kind.BATTERY' not in settings, 'Preview must not promise custom battery rendering'
print(f'Verified {len(names)} static drawable assets, native resource types, signal states, animation timing and restart notice.')
